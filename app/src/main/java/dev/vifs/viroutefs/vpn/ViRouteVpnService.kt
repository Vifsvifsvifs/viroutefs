// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Bundle
import android.os.ResultReceiver
import androidx.core.app.NotificationCompat
import dev.vifs.viroutefs.MainActivity
import dev.vifs.viroutefs.engine.EngineOrchestrator
import dev.vifs.viroutefs.engine.EngineOrchestratorException
import dev.vifs.viroutefs.routing.RouteRuleType
import dev.vifs.viroutefs.routing.RoutingConfig
import dev.vifs.viroutefs.routing.RoutingConfigRepository
import dev.vifs.viroutefs.routing.VPN_GATE_AUTOMATIC_GROUP_ID
import dev.vifs.viroutefs.routing.VpnGateCatalogClient
import dev.vifs.viroutefs.routing.createAutomaticVpnGateRoute
import dev.vifs.viroutefs.routing.defaultRouteActivationError
import dev.vifs.viroutefs.routing.isAutomaticVpnGateEnabled
import dev.vifs.viroutefs.runtime.tcp.TcpSessionState
import dev.vifs.viroutefs.R
import java.io.FileInputStream
import java.io.InterruptedIOException
import kotlinx.coroutines.runBlocking

/**
 * Single Android VPN entry point for both the real sing-box router and an explicit
 * developer-only TEST-NET read/drop preview.
 *
 * Normal activation installs IPv4/IPv6 default routes, excludes ViRouteFS
 * itself to prevent an engine loop. sing-box requests and owns the TUN through
 * the Android platform callback.
 * Invalid or unavailable selected outbounds are compiled to Block. The preview
 * mode never forwards and captures only TEST-NET-3.
 */
class ViRouteVpnService : VpnService() {
    private var tunDescriptor: ParcelFileDescriptor? = null
    private var packetLoopThread: Thread? = null
    private var runtimeThread: Thread? = null
    private var reloadValidationThread: Thread? = null
    private var profileConnectionTestThread: Thread? = null
    private var reloadGeneration: Long = 0L
    private var engineOrchestrator: EngineOrchestrator? = null
    private var singBoxEngineAdapter: SingBoxEngineAdapter? = null
    @Volatile private var packetLoopStopping: Boolean = false
    @Volatile private var runtimeStopping: Boolean = false
    private var testRoutePreviewActive: Boolean = false
    private var runtimeActive: Boolean = false
    private var runtimeDetail: String? = null
    private var packetsRead: Long = 0L
    private var bytesRead: Long = 0L
    private var ipv4PacketsRead: Long = 0L
    private var tcpPacketsRead: Long = 0L
    private var udpPacketsRead: Long = 0L
    private var icmpPacketsRead: Long = 0L
    private var lastPacketAt: Long? = null
    private val packetHistory = PacketSummaryHistory()
    @Volatile private var connectionFlows: List<VpnConnectionFlow> = emptyList()
    @Volatile private var profileGroupEvents: List<ProfileGroupRuntimeEvent> = emptyList()
    @Volatile private var dnsFallbackEvents: List<DnsFallbackRuntimeEvent> = emptyList()
    private val profileGroupEventLock = Any()
    private val dnsFallbackEventLock = Any()
    private var lastUiPublishAt: Long = 0L

    override fun onCreate() {
        super.onCreate()
        publishState(VpnServiceStatus.Starting)
        runCatching {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, buildNotification(VpnServiceStatus.ServiceActiveNoTun))
        }.onFailure { error ->
            val detail = error.localizedMessage ?: "Foreground service notification setup failed."
            publishState(VpnServiceStatus.Error, detail)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val forceReload = intent?.action == VpnServiceController.ACTION_RELOAD
        when (intent?.action) {
            VpnServiceController.ACTION_STOP -> {
                cancelPendingRuntimeReload()
                stopPreview()
                return START_NOT_STICKY
            }
            VpnServiceController.ACTION_CLEAR_PACKET_SUMMARIES -> {
                packetHistory.clear()
                connectionFlows = emptyList()
                profileGroupEvents = emptyList()
                dnsFallbackEvents = emptyList()
                singBoxEngineAdapter?.clearConnectionHistory()
                publishActiveState(force = true)
                return START_STICKY
            }
            VpnServiceController.ACTION_SET_PACKET_INSPECTOR_PAUSED -> {
                packetHistory.setPaused(intent.getBooleanExtra(VpnServiceController.EXTRA_PACKET_INSPECTOR_PAUSED, false))
                publishActiveState(force = true)
                return START_STICKY
            }
            VpnServiceController.ACTION_TEST_PROFILE_CONNECTION -> {
                startProfileConnectionTest(intent)
                return START_STICKY
            }
        }

        if (lastState.status == VpnServiceStatus.Error) return START_NOT_STICKY

        isRunning = true
        val requestedTestRoute = intent?.getBooleanExtra(
            VpnServiceController.EXTRA_TEST_ROUTE_ENABLED,
            false,
        ) ?: false

        if (forceReload &&
            !requestedTestRoute &&
            tunDescriptor != null &&
            runtimeActive
        ) {
            startValidatedRuntimeReload()
            return START_STICKY
        }

        if (!forceReload &&
            tunDescriptor != null &&
            testRoutePreviewActive == requestedTestRoute &&
            (requestedTestRoute || runtimeActive)
        ) {
            publishActiveState()
            updateNotification(activeStatus())
            return START_STICKY
        }

        if (tunDescriptor != null) closeTunDescriptor()
        if (requestedTestRoute) {
            establishTunPreview(enableTestRoute = true)
        } else {
            startTunRuntime()
        }
        return START_STICKY
    }

    override fun onRevoke() {
        cancelPendingRuntimeReload()
        stopPreview()
        super.onRevoke()
    }

    override fun onDestroy() {
        cancelPendingRuntimeReload()
        closeTunDescriptor()
        isRunning = false
        if (lastState.status != VpnServiceStatus.Error) publishStoppedState()
        super.onDestroy()
    }

    private fun establishTunPreview(enableTestRoute: Boolean) {
        testRoutePreviewActive = enableTestRoute
        runtimeActive = false
        runtimeDetail = null
        resetCounters()
        publishState(VpnServiceStatus.Starting)
        val descriptor = runCatching {
            val builder = Builder()
                .setSession(TUN_SESSION_NAME)
                .setMtu(TUN_MTU)
                .addAddress(TUN_IPV4_ADDRESS, TUN_IPV4_PREFIX_LENGTH)
            if (enableTestRoute) {
                builder.addRoute(TEST_ROUTE_IPV4_NETWORK, TEST_ROUTE_IPV4_PREFIX_LENGTH)
            }
            builder.establish()
        }.onFailure { error ->
            val detail = error.localizedMessage ?: "Could not establish safe TUN preview."
            publishState(VpnServiceStatus.Error, detail)
            updateNotification(VpnServiceStatus.ServiceActiveNoTun)
            isRunning = false
            stopSelf()
        }.getOrNull()

        if (descriptor == null) {
            if (lastState.status != VpnServiceStatus.Error) {
                publishState(VpnServiceStatus.Error, "Android returned no TUN descriptor for the safe preview.")
                updateNotification(VpnServiceStatus.ServiceActiveNoTun)
                isRunning = false
                stopSelf()
            }
            return
        }

        tunDescriptor = descriptor
        startPacketLoop(descriptor)
        publishActiveState()
        updateNotification(activeStatus())
    }

    private fun startTunRuntime(configOverride: RoutingConfig? = null) {
        testRoutePreviewActive = false
        runtimeActive = false
        runtimeStopping = false
        runtimeDetail = "Loading local routing configuration."
        resetCounters()
        publishState(VpnServiceStatus.Starting, runtimeDetail)
        updateNotification(VpnServiceStatus.Starting)
        runtimeThread = Thread(
            { establishTunRuntime(configOverride) },
            "ViRouteFS-SingBoxRuntime",
        ).apply {
            isDaemon = true
            start()
        }
    }

    private fun establishTunRuntime(configOverride: RoutingConfig?) {
        var config = configOverride ?: loadRuntimeConfig().getOrElse { error ->
            failRuntime(error.userSafeEngineMessage("Не удалось загрузить конфигурацию маршрутов."))
            return
        }
        if (config.isAutomaticVpnGateEnabled()) {
            runtimeDetail = "VPNGate: обновляем каталог и выбираем доступные серверы…"
            publishState(VpnServiceStatus.Starting, runtimeDetail)
            config = refreshAutomaticVpnGate(config).getOrElse { error ->
                val existingGroup = config.profileGroups.firstOrNull { it.id == VPN_GATE_AUTOMATIC_GROUP_ID }
                if (existingGroup == null || existingGroup.memberProfileIds.size < 2) {
                    failRuntime(
                        "VPNGate не удалось обновить перед запуском: " +
                            (error.localizedMessage ?: "каталог недоступен"),
                    )
                    return
                }
                runtimeDetail = "VPNGate: новый анализ не удался, используем сохранённый набор серверов."
                config
            }
        }
        if (runtimeStopping) return
        if (!config.emergencyBlockEnabled) {
            defaultRouteActivationError(config)?.let { error ->
                failRuntime(error)
                return
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            config.rules.any {
                it.enabled && (it.type == RouteRuleType.APP || it.type == RouteRuleType.APP_GROUP)
            }
        ) {
            failRuntime(
                "Per-app routing requires Android 10 or newer. VPN activation was stopped to avoid a silent route fallback.",
            )
            return
        }

        val compatibilityAdapter = ByeDpiEngineAdapter(applicationContext)
        val xrayAdapter = XrayEngineAdapter(applicationContext)
        val singBoxAdapter = SingBoxEngineAdapter(
            service = this,
            onTunEstablished = { descriptor ->
                tunDescriptor = descriptor
            },
            onLog = { message ->
                runtimeDetail = message.take(MAX_RUNTIME_DETAIL_LENGTH)
            },
            onConnections = { flows ->
                if (!packetHistory.isPaused()) {
                    connectionFlows = flows
                    publishActiveStateThrottled()
                }
            },
            onProfileGroupAction = { action ->
                profileGroupEvents = synchronized(profileGroupEventLock) {
                    (
                        listOf(
                            ProfileGroupRuntimeEvent(
                                timestamp = System.currentTimeMillis(),
                                groupId = action.groupId,
                                groupName = action.groupName,
                                selectedProfileId = action.selectedProfileId,
                                selectedProfileName = action.selectedProfileName,
                                reason = action.reason,
                                message = action.message,
                            ),
                        ) + profileGroupEvents
                        ).take(MAX_PROFILE_GROUP_EVENTS)
                }
                publishActiveStateThrottled()
            },
            onDnsFallback = { policyNames ->
                val displayNames = policyNames.distinct().sorted()
                val subject = when (displayNames.size) {
                    0 -> "Один из DNS-серверов"
                    1 -> "Основной сервер политики «${displayNames.single()}»"
                    else -> "Основной сервер одной из политик: ${displayNames.joinToString()}"
                }
                dnsFallbackEvents = synchronized(dnsFallbackEventLock) {
                    (
                        listOf(
                            DnsFallbackRuntimeEvent(
                                timestamp = System.currentTimeMillis(),
                                policyNames = displayNames,
                                message = "$subject не ответил или вернул сетевую ошибку. " +
                                    "Движок продолжил этот DNS-запрос на следующем сервере; имя запрошенного домена не сохранено.",
                            ),
                        ) + dnsFallbackEvents
                        ).take(MAX_DNS_FALLBACK_EVENTS)
                }
                publishActiveStateThrottled()
            },
        )
        val orchestrator = EngineOrchestrator(
            listOf(compatibilityAdapter, xrayAdapter, singBoxAdapter),
        )
        engineOrchestrator = orchestrator
        singBoxEngineAdapter = singBoxAdapter
        val plan = orchestrator.prepare(config).getOrElse { error ->
            failRuntime(error.userSafeEngineMessage("Не удалось подготовить сетевые движки."))
            return
        }
        val startResult = orchestrator.start(plan)
        if (startResult.isFailure) {
            failRuntime(
                startResult.exceptionOrNull()
                    .userSafeEngineMessage("Сетевые движки не подтвердили готовность."),
            )
            return
        }
        if (!orchestrator.isHealthy()) {
            val healthError = orchestrator.snapshot().errors.values.firstOrNull()
            failRuntime(
                healthError
                    ?.let(::EngineOrchestratorException)
                    .userSafeEngineMessage(
                        runtimeDetail
                            ?.maskUserSafeEngineDetails()
                            ?.take(500)
                            ?: "Сетевые движки не подтвердили готовность.",
                    ),
            )
            return
        }

        runtimeActive = true
        runtimeDetail = buildString {
            append("Единый VPN-маршрутизатор запущен; активных runtime-профилей: ")
            append(singBoxAdapter.statistics().activeProfiles)
            append(".")
            (plan.warnings + singBoxAdapter.warnings()).take(2).takeIf { it.isNotEmpty() }?.let {
                append(" ")
                append(it.joinToString(" "))
            }
        }.take(MAX_RUNTIME_DETAIL_LENGTH)
        publishActiveState(force = true)
        updateNotification(VpnServiceStatus.RuntimeActive)

        while (!runtimeStopping && orchestrator.isHealthy()) {
            try {
                Thread.sleep(RUNTIME_STATS_INTERVAL_MS)
            } catch (_: InterruptedException) {
                break
            }
        }
        if (!runtimeStopping) {
            val details = orchestrator.snapshot().errors.values.firstOrNull()
            failRuntime(
                details?.let {
                    "${it.summary} ${it.recommendedAction}"
                } ?: "Один из сетевых движков остановился. Связанный трафик оставлен заблокированным.",
            )
        }
    }

    /**
     * Keeps the currently healthy generation alive until the replacement
     * configuration has passed repository, structural, engine and native checks.
     *
     * Android owns a single VPN TUN, so a successful swap may still reconnect
     * briefly. A rejected replacement never tears down the active generation.
     */
    private fun startValidatedRuntimeReload() {
        val generation = ++reloadGeneration
        reloadValidationThread?.interrupt()
        runtimeDetail = "Проверяем новые настройки. Текущий маршрут остаётся активным."
        publishActiveState(force = true)
        updateNotification(VpnServiceStatus.RuntimeActive)

        reloadValidationThread = Thread(
            {
                val result = loadRuntimeConfig().mapCatching { config ->
                    check(!Thread.currentThread().isInterrupted) { "Проверка отменена." }
                    preflightRuntimeConfig(config)
                    config
                }
                Handler(Looper.getMainLooper()).post {
                    if (generation != reloadGeneration || !isRunning) return@post
                    reloadValidationThread = null
                    result.onSuccess { config ->
                        if (tunDescriptor == null || !runtimeActive) return@onSuccess
                        runtimeDetail = "Настройки проверены. Перезапускаем сетевой маршрут."
                        publishActiveState(force = true)
                        closeTunDescriptor()
                        startTunRuntime(config)
                    }.onFailure { error ->
                        runtimeDetail = buildString {
                            append("Изменения не применены; прежний маршрут продолжает работать. ")
                            append(error.userSafeEngineMessage("Проверьте изменённые настройки."))
                        }.take(MAX_RUNTIME_DETAIL_LENGTH)
                        publishActiveState(force = true)
                        updateNotification(VpnServiceStatus.RuntimeActive)
                    }
                }
            },
            "ViRouteFS-ReloadPreflight",
        ).apply {
            isDaemon = true
            start()
        }
    }

    private fun refreshAutomaticVpnGate(config: RoutingConfig): Result<RoutingConfig> = runCatching {
        val snapshot = VpnGateCatalogClient(applicationContext).fetch(config)
        val refreshed = createAutomaticVpnGateRoute(
            config = config,
            servers = snapshot.servers,
            excludedCountryCode = detectRuntimeCountryCode(),
            preferredCountryCode = config.profileGroups
                .firstOrNull { it.id == VPN_GATE_AUTOMATIC_GROUP_ID }
                ?.preferredCountryCode,
        ).config
        runBlocking {
            RoutingConfigRepository(applicationContext).save(refreshed)
        }
        val serverCount = refreshed.profileGroups
            .firstOrNull { it.id == VPN_GATE_AUTOMATIC_GROUP_ID }
            ?.memberProfileIds
            ?.size
            ?: 0
        runtimeDetail = "VPNGate: каталог обновлён, подготовлено серверов: $serverCount"
        refreshed
    }

    private fun detectRuntimeCountryCode(): String {
        val telephony = getSystemService(android.telephony.TelephonyManager::class.java)
        return listOf(
            runCatching { telephony?.networkCountryIso }.getOrNull(),
            runCatching { telephony?.simCountryIso }.getOrNull(),
            java.util.Locale.getDefault().country,
        ).firstOrNull { value -> value?.matches(Regex("[A-Za-z]{2}")) == true }
            ?.uppercase()
            ?: "RU"
    }

    @Suppress("DEPRECATION")
    private fun startProfileConnectionTest(intent: Intent) {
        val receiver = intent.getParcelableExtra<ResultReceiver>(
            VpnServiceController.EXTRA_PROFILE_TEST_RECEIVER,
        ) ?: return
        val profileId = intent.getStringExtra(VpnServiceController.EXTRA_PROFILE_ID).orEmpty()
        if (profileId.isBlank()) {
            receiver.sendProfileTestFailure("Не выбран VPN-профиль для проверки.")
            return
        }
        val orchestrator = engineOrchestrator
        if (!runtimeActive || orchestrator == null) {
            receiver.sendProfileTestFailure(
                "Сетевой контроль не запущен. Включите его, чтобы проверить соединение именно через профиль.",
            )
            return
        }

        if (profileConnectionTestThread?.isAlive == true) {
            receiver.sendProfileTestFailure("Другая проверка VPN-профиля уже выполняется.")
            return
        }
        profileConnectionTestThread = Thread(
            {
                val result = orchestrator.testConnection(profileId)
                result.onSuccess { test ->
                    receiver.send(
                        if (test.successful) {
                            VpnServiceController.PROFILE_TEST_SUCCESS
                        } else {
                            VpnServiceController.PROFILE_TEST_FAILURE
                        },
                        Bundle().apply {
                            putString(VpnServiceController.EXTRA_PROFILE_TEST_SUMMARY, test.summary.take(500))
                            putLong(
                                VpnServiceController.EXTRA_PROFILE_TEST_LATENCY,
                                test.latencyMillis ?: VpnServiceController.NO_PROFILE_TEST_LATENCY,
                            )
                        },
                    )
                }.onFailure { error ->
                    receiver.sendProfileTestFailure(
                        (error.localizedMessage ?: "Проверка соединения через профиль завершилась ошибкой.")
                            .take(500),
                    )
                }
                if (profileConnectionTestThread === Thread.currentThread()) {
                    profileConnectionTestThread = null
                }
            },
            "ViRouteFS-ProfileConnectionTest",
        ).apply {
            isDaemon = true
            start()
        }
    }

    private fun loadRuntimeConfig(): Result<RoutingConfig> = runCatching {
        val loadResult = runBlocking { RoutingConfigRepository(applicationContext).load() }
        loadResult.errorMessage?.let(::error)
        loadResult.config
    }

    private fun preflightRuntimeConfig(config: RoutingConfig) {
        if (!config.emergencyBlockEnabled) {
            defaultRouteActivationError(config)?.let(::error)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            config.rules.any {
                it.enabled && (it.type == RouteRuleType.APP || it.type == RouteRuleType.APP_GROUP)
            }
        ) {
            error("Маршрутизация отдельных приложений требует Android 10 или новее.")
        }

        val compatibilityAdapter = ByeDpiEngineAdapter(applicationContext)
        val xrayAdapter = XrayEngineAdapter(applicationContext)
        val singBoxAdapter = SingBoxEngineAdapter(
            service = this,
            onTunEstablished = {},
            onLog = {},
            onConnections = {},
            onProfileGroupAction = {},
        )
        EngineOrchestrator(
            listOf(compatibilityAdapter, xrayAdapter, singBoxAdapter),
        ).prepare(config).getOrThrow()
        SingBoxRuntimeValidator.validate(applicationContext, config).getOrThrow()
    }

    private fun cancelPendingRuntimeReload() {
        reloadGeneration += 1L
        reloadValidationThread?.interrupt()
        reloadValidationThread = null
    }

    private fun failRuntime(detail: String) {
        if (runtimeStopping) return
        closeTunDescriptor()
        isRunning = false
        publishState(VpnServiceStatus.Error, detail)
        updateNotification(VpnServiceStatus.Error)
        stopSelf()
    }

    private fun startPacketLoop(descriptor: ParcelFileDescriptor) {
        stopPacketLoop()
        packetLoopStopping = false
        packetLoopThread = Thread({ readPacketsUntilClosed(descriptor) }, "ViRouteFS-TunReadDropPreview").apply {
            isDaemon = true
            start()
        }
    }

    private fun readPacketsUntilClosed(descriptor: ParcelFileDescriptor) {
        val buffer = ByteArray(TUN_MTU)
        runCatching {
            FileInputStream(descriptor.fileDescriptor).use { input ->
                while (!packetLoopStopping) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    inspectPacket(buffer, read)
                    publishActiveStateThrottled()
                }
            }
        }.onFailure { error ->
            if (!packetLoopStopping && error !is InterruptedIOException) {
                val detail = error.localizedMessage ?: "TUN read failed; packet preview stopped."
                closeTunDescriptor()
                isRunning = false
                publishState(VpnServiceStatus.Error, detail)
                updateNotification(VpnServiceStatus.ServiceActiveNoTun)
                stopSelf()
            }
        }
    }

    private fun stopPreview() {
        closeTunDescriptor()
        isRunning = false
        publishStoppedState()
        stopSelf()
    }

    private fun closeTunDescriptor() {
        profileConnectionTestThread?.interrupt()
        profileConnectionTestThread = null
        packetLoopStopping = true
        runtimeStopping = true
        runCatching { engineOrchestrator?.stop() }
        engineOrchestrator = null
        singBoxEngineAdapter = null
        tunDescriptor?.let { descriptor ->
            runCatching { descriptor.close() }
        }
        tunDescriptor = null
        stopPacketLoop()
        stopRuntimeThread()
        testRoutePreviewActive = false
        runtimeActive = false
        runtimeDetail = null
    }

    private fun stopPacketLoop() {
        packetLoopStopping = true
        val thread = packetLoopThread
        thread?.interrupt()
        if (thread != null && thread != Thread.currentThread()) {
            runCatching { thread.join(PACKET_LOOP_JOIN_TIMEOUT_MS) }
        }
        packetLoopThread = null
    }

    private fun stopRuntimeThread() {
        val thread = runtimeThread
        thread?.interrupt()
        if (thread != null && thread != Thread.currentThread()) {
            runCatching { thread.join(RUNTIME_THREAD_JOIN_TIMEOUT_MS) }
        }
        runtimeThread = null
    }

    private fun inspectPacket(buffer: ByteArray, length: Int) {
        packetsRead += 1L
        bytesRead += length.toLong()
        val summary = Ipv4PacketParser.parseSummary(buffer, length)
        if (summary != null) {
            ipv4PacketsRead += 1L
            when (summary.protocol) {
                Ipv4Protocol.Tcp -> tcpPacketsRead += 1L
                Ipv4Protocol.Udp -> udpPacketsRead += 1L
                Ipv4Protocol.Icmp -> icmpPacketsRead += 1L
                Ipv4Protocol.Other -> Unit
            }
            packetHistory.add(summary)
            lastPacketAt = summary.timestamp
        } else {
            lastPacketAt = System.currentTimeMillis()
        }
    }

    private fun resetCounters() {
        packetsRead = 0L
        bytesRead = 0L
        ipv4PacketsRead = 0L
        tcpPacketsRead = 0L
        udpPacketsRead = 0L
        icmpPacketsRead = 0L
        lastPacketAt = null
        lastUiPublishAt = 0L
        packetHistory.clear()
        connectionFlows = emptyList()
    }

    private fun activeStatus(): VpnServiceStatus =
        when {
            runtimeActive -> VpnServiceStatus.RuntimeActive
            testRoutePreviewActive -> VpnServiceStatus.TunTestRouteActive
            else -> VpnServiceStatus.TunPreviewActive
        }

    private fun publishActiveState(force: Boolean = false) {
        if (force) lastUiPublishAt = System.currentTimeMillis()
        publishState(
            status = activeStatus(),
            detail = runtimeDetail,
            tunTestRouteActive = testRoutePreviewActive,
            packetsRead = packetsRead,
            bytesRead = bytesRead,
            ipv4PacketsRead = ipv4PacketsRead,
            tcpPacketsRead = tcpPacketsRead,
            udpPacketsRead = udpPacketsRead,
            icmpPacketsRead = icmpPacketsRead,
            lastPacketAt = lastPacketAt,
            packetSummaryUpdatedAt = packetHistory.lastUpdatedAt(),
            packetInspectorPaused = packetHistory.isPaused(),
            packetSummaries = packetHistory.newestFirst(),
            connectionFlows = connectionFlows,
            profileGroupEvents = profileGroupEvents,
            dnsFallbackEvents = dnsFallbackEvents,
        )
    }

    private fun publishActiveStateThrottled(now: Long = System.currentTimeMillis()) {
        if (now - lastUiPublishAt < UI_PUBLISH_INTERVAL_MS) return
        lastUiPublishAt = now
        publishActiveState()
    }

    private fun publishStoppedState() {
        publishState(
            status = VpnServiceStatus.Stopped,
            packetsRead = packetsRead,
            bytesRead = bytesRead,
            ipv4PacketsRead = ipv4PacketsRead,
            tcpPacketsRead = tcpPacketsRead,
            udpPacketsRead = udpPacketsRead,
            icmpPacketsRead = icmpPacketsRead,
            lastPacketAt = lastPacketAt,
            packetSummaryUpdatedAt = packetHistory.lastUpdatedAt(),
            packetInspectorPaused = packetHistory.isPaused(),
            packetSummaries = packetHistory.newestFirst(),
            connectionFlows = connectionFlows,
            profileGroupEvents = profileGroupEvents,
            dnsFallbackEvents = dnsFallbackEvents,
        )
    }

    private fun buildNotification(status: VpnServiceStatus): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = when (status) {
            VpnServiceStatus.RuntimeActive -> "VPN router active — rules are enforced by the local engine"
            VpnServiceStatus.TunTestRouteActive -> "TEST-NET route preview active — packets are counted and dropped"
            VpnServiceStatus.TunPreviewActive -> "TUN preview active — no traffic routes installed"
            VpnServiceStatus.Error -> "VPN router stopped because of an error"
            else -> "Starting the local VPN router"
        }
        val details = when (status) {
            VpnServiceStatus.RuntimeActive ->
                "IPv4/IPv6 traffic is routed locally. Selected unavailable profiles fail closed. No telemetry or payload logging."
            VpnServiceStatus.TunTestRouteActive ->
                "Developer preview only. Normal internet traffic is not captured and TEST-NET packets are dropped."
            else -> runtimeDetail ?: text
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_viroutefs_notification)
            .setContentTitle("ViRouteFS VPN router")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(status: VpnServiceStatus) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ViRouteFS VPN router",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Foreground notification for the local VPN router and explicit developer preview."
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun publishState(
        status: VpnServiceStatus,
        detail: String? = null,
        tunTestRouteActive: Boolean = false,
        packetsRead: Long = 0L,
        bytesRead: Long = 0L,
        ipv4PacketsRead: Long = 0L,
        tcpPacketsRead: Long = 0L,
        udpPacketsRead: Long = 0L,
        icmpPacketsRead: Long = 0L,
        lastPacketAt: Long? = null,
        packetSummaryUpdatedAt: Long? = null,
        packetInspectorPaused: Boolean = false,
        packetSummaries: List<PacketSummary> = emptyList(),
        connectionFlows: List<VpnConnectionFlow> = emptyList(),
        profileGroupEvents: List<ProfileGroupRuntimeEvent> = emptyList(),
        dnsFallbackEvents: List<DnsFallbackRuntimeEvent> = emptyList(),
        activeTcpSessions: Int = 0,
        tcpSessionStateStats: Map<TcpSessionState, Int> = emptyMap(),
    ) {
        val state = VpnServiceUiState(
            status = status,
            detail = detail,
            tunTestRouteActive = tunTestRouteActive,
            packetsRead = packetsRead,
            bytesRead = bytesRead,
            ipv4PacketsRead = ipv4PacketsRead,
            tcpPacketsRead = tcpPacketsRead,
            udpPacketsRead = udpPacketsRead,
            icmpPacketsRead = icmpPacketsRead,
            lastPacketAt = lastPacketAt,
            packetSummaryUpdatedAt = packetSummaryUpdatedAt,
            packetInspectorPaused = packetInspectorPaused,
            packetSummaries = packetSummaries,
            connectionFlows = connectionFlows,
            profileGroupEvents = profileGroupEvents,
            dnsFallbackEvents = dnsFallbackEvents,
            activeTcpSessions = activeTcpSessions,
            tcpSessionStateStats = tcpSessionStateStats,
        )
        val statusChanged = lastState.status != state.status
        rememberState(state)
        val intent = Intent(VpnServiceController.ACTION_STATE_CHANGED)
            .setPackage(packageName)
            .putExtra(VpnServiceController.EXTRA_STATUS, status.name)
            .putExtra(VpnServiceController.EXTRA_DETAIL, detail)
            .putExtra(VpnServiceController.EXTRA_TEST_ROUTE_ACTIVE, tunTestRouteActive)
            .putExtra(VpnServiceController.EXTRA_PACKETS_READ, packetsRead)
            .putExtra(VpnServiceController.EXTRA_BYTES_READ, bytesRead)
            .putExtra(VpnServiceController.EXTRA_IPV4_PACKETS_READ, ipv4PacketsRead)
            .putExtra(VpnServiceController.EXTRA_TCP_PACKETS_READ, tcpPacketsRead)
            .putExtra(VpnServiceController.EXTRA_UDP_PACKETS_READ, udpPacketsRead)
            .putExtra(VpnServiceController.EXTRA_ICMP_PACKETS_READ, icmpPacketsRead)
            .putExtra(VpnServiceController.EXTRA_LAST_PACKET_AT, lastPacketAt ?: VpnServiceController.NO_PACKET_TIME)
            .putExtra(VpnServiceController.EXTRA_PACKET_SUMMARY_UPDATED_AT, packetSummaryUpdatedAt ?: VpnServiceController.NO_PACKET_TIME)
            .putExtra(VpnServiceController.EXTRA_PACKET_INSPECTOR_PAUSED, packetInspectorPaused)
            .putStringArrayListExtra(
                VpnServiceController.EXTRA_PACKET_SUMMARIES,
                ArrayList(packetSummaries.map(VpnServiceController::encodePacketSummary)),
            )
            .putStringArrayListExtra(
                VpnServiceController.EXTRA_CONNECTION_FLOWS,
                ArrayList(connectionFlows.map(VpnServiceController::encodeConnectionFlow)),
            )
            .putStringArrayListExtra(
                VpnServiceController.EXTRA_PROFILE_GROUP_EVENTS,
                ArrayList(profileGroupEvents.map(VpnServiceController::encodeProfileGroupEvent)),
            )
            .putStringArrayListExtra(
                VpnServiceController.EXTRA_DNS_FALLBACK_EVENTS,
                ArrayList(dnsFallbackEvents.map(VpnServiceController::encodeDnsFallbackEvent)),
            )
            .putExtra(VpnServiceController.EXTRA_ACTIVE_TCP_SESSIONS, activeTcpSessions)
            .putStringArrayListExtra(
                VpnServiceController.EXTRA_TCP_SESSION_STATE_STATS,
                ArrayList(VpnServiceController.encodeTcpSessionStateStats(tcpSessionStateStats)),
            )
        sendBroadcast(intent)
        if (statusChanged) NetworkControlTileService.requestRefresh(this)
    }

    companion object {
        @Volatile
        internal var isRunning: Boolean = false
            private set

        @Volatile
        internal var lastState: VpnServiceUiState = VpnServiceUiState(VpnServiceStatus.Off)
            private set

        internal fun rememberState(state: VpnServiceUiState) {
            lastState = state
        }

        private const val CHANNEL_ID = "viroutefs_vpn_preview"
        private const val NOTIFICATION_ID = 500
        private const val TUN_SESSION_NAME = "ViRouteFS VPN router"
        private const val TUN_IPV4_ADDRESS = "10.250.0.2"
        private const val TUN_IPV4_PREFIX_LENGTH = 30
        private const val TUN_IPV6_ADDRESS = "fdfe:dcba:9876::2"
        private const val TUN_IPV6_PREFIX_LENGTH = 64
        private const val TEST_ROUTE_IPV4_NETWORK = "203.0.113.0"
        private const val TEST_ROUTE_IPV4_PREFIX_LENGTH = 24
        private const val TUN_MTU = 1500
        private const val PACKET_LOOP_JOIN_TIMEOUT_MS = 500L
        private const val RUNTIME_THREAD_JOIN_TIMEOUT_MS = 1_500L
        private const val RUNTIME_STATS_INTERVAL_MS = 1_000L
        private const val MAX_RUNTIME_DETAIL_LENGTH = 500
        private const val UI_PUBLISH_INTERVAL_MS = 250L
        private const val MAX_PROFILE_GROUP_EVENTS = 80
        private const val MAX_DNS_FALLBACK_EVENTS = 80
    }
}

private fun ResultReceiver.sendProfileTestFailure(summary: String) {
    send(
        VpnServiceController.PROFILE_TEST_FAILURE,
        Bundle().apply {
            putString(VpnServiceController.EXTRA_PROFILE_TEST_SUMMARY, summary.take(500))
            putLong(
                VpnServiceController.EXTRA_PROFILE_TEST_LATENCY,
                VpnServiceController.NO_PROFILE_TEST_LATENCY,
            )
        },
    )
}

private fun Throwable?.userSafeEngineMessage(fallback: String): String = when (this) {
    is EngineOrchestratorException -> buildString {
        append(engineError.summary)
        engineError.technicalDetails.takeIf(String::isNotBlank)?.let { details ->
            append(" Причина: ")
            append(details)
        }
        append(' ')
        append(engineError.recommendedAction)
    }.maskUserSafeEngineDetails().take(500)
    null -> fallback
    else -> (localizedMessage ?: fallback).maskUserSafeEngineDetails().take(500)
}

private fun String.maskUserSafeEngineDetails(): String = this
    .replace(Regex("(?i)vless://[^\\s]+"), "vless://<redacted>")
    .replace(
        Regex("(?i)(password|passphrase|private_key|psk|uuid|auth_key|cookie)\\s*[=:]\\s*[^\\s,;]+"),
        "$1=<redacted>",
    )
    .replace(
        Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"),
        "<redacted-uuid>",
    )
