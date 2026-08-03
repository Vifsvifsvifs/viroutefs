// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.root

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.vifs.viroutefs.MainActivity
import dev.vifs.viroutefs.R
import java.util.Calendar
import java.util.concurrent.atomic.AtomicBoolean

data class RootAutomationResult(
    val successful: Boolean,
    val running: Boolean,
    val message: String,
)

data class RootAutomationStatus(
    val running: Boolean,
    val targetApplied: Boolean,
    val message: String,
)

class RootAutomationController(context: Context) {
    private val appContext = context.applicationContext
    private val access = RootAccessController(appContext)
    private val configRepository = RootAutomationConfigRepository(appContext)
    private val runtime = RootRuntimeStateRepository(appContext)

    fun loadConfig(): RootAutomationConfig = configRepository.load()

    fun status(): RootAutomationStatus = RootAutomationStatus(
        running = RootAutomationService.isRunning || RootManagedModule.Automation in runtime.load()?.modules.orEmpty(),
        targetApplied = RootAutomationService.targetApplied,
        message = RootAutomationService.lastMessage,
    )

    fun start(config: RootAutomationConfig): RootAutomationResult {
        val targetModules = config.target.managedModules
        if (runtime.load()?.modules.orEmpty().any(targetModules::contains)) {
            return RootAutomationResult(
                false,
                false,
                "Целевой root-модуль уже включён вручную. Сначала остановите его, чтобы автоматика не забрала чужую сессию.",
            )
        }
        when (config.target) {
            RootAutomationTarget.AppFirewall -> if (RootAppFirewallController(appContext).loadConfig().isEmpty) {
                return RootAutomationResult(false, false, "Сначала сохраните непустые правила в root-файрволе приложений.")
            }
            RootAutomationTarget.NetworkGuard -> if (RootNetworkGuardController(appContext).loadConfig().isEmpty) {
                return RootAutomationResult(false, false, "Сначала сохраните непустой набор защиты от утечек.")
            }
            RootAutomationTarget.ConnectionAdaptation -> Unit
        }
        val probe = access.requestAndProbe()
        if (!probe.granted) return RootAutomationResult(false, false, probe.message)
        val prepared = runCatching {
            configRepository.save(config)
            runtime.markPending(RootManagedModule.Automation, "foreground_conditions")
        }.isSuccess
        if (!prepared) {
            return RootAutomationResult(false, false, "Не удалось сохранить автоматику; фоновый режим не запущен.")
        }
        return runCatching {
            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, RootAutomationService::class.java).setAction(RootAutomationService.ACTION_START),
            )
            RootAutomationResult(
                true,
                true,
                "Автоматика запущена в видимом фоновом режиме. После перезагрузки она сама не стартует.",
            )
        }.getOrElse { error ->
            runtime.removeModule(RootManagedModule.Automation)
            RootAutomationResult(false, false, error.localizedMessage ?: "Android не запустил фоновую автоматику.")
        }
    }

    fun stop(): RootAutomationResult {
        RootAutomationService.requestStop(appContext)
        return RootAutomationResult(
            true,
            false,
            "Остановка автоматики запрошена. Если она включала root-модуль, сервис адресно удалит только его правила.",
        )
    }
}

class RootAutomationService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val evaluationRunning = AtomicBoolean(false)
    private val runtime by lazy { RootRuntimeStateRepository(applicationContext) }
    private val configRepository by lazy { RootAutomationConfigRepository(applicationContext) }
    private val connectivity by lazy { getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    private val power by lazy { getSystemService(Context.POWER_SERVICE) as PowerManager }
    private var registered = false
    @Volatile private var ownedTargetApplied = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = scheduleEvaluation(0L)
    }
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = scheduleEvaluation(0L)
        override fun onLost(network: Network) = scheduleEvaluation(0L)
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = scheduleEvaluation(0L)
    }
    private val periodicEvaluation = object : Runnable {
        override fun run() {
            evaluateAsync()
            handler.postDelayed(this, ROOT_AUTOMATION_INTERVAL_MILLIS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(ROOT_AUTOMATION_NOTIFICATION_ID, buildNotification("Проверяем условия…"))
        isRunning = true
        lastMessage = "Проверяем условия…"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAutomationAsync()
            return START_NOT_STICKY
        }
        registerSignals()
        handler.removeCallbacks(periodicEvaluation)
        handler.post(periodicEvaluation)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        unregisterSignals()
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerSignals() {
        if (registered) return
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        connectivity.registerDefaultNetworkCallback(networkCallback)
        registered = true
    }

    private fun unregisterSignals() {
        if (!registered) return
        runCatching { unregisterReceiver(screenReceiver) }
        runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        registered = false
    }

    private fun scheduleEvaluation(delayMillis: Long) {
        handler.postDelayed({ evaluateAsync() }, delayMillis)
    }

    private fun evaluateAsync() {
        if (!evaluationRunning.compareAndSet(false, true)) return
        Thread(
            {
                try {
                    val config = configRepository.load()
                    val capabilities = connectivity.activeNetwork?.let(connectivity::getNetworkCapabilities)
                    val network = when {
                        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> RootAutomationNetwork.Wifi
                        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> RootAutomationNetwork.Cellular
                        else -> RootAutomationNetwork.Any
                    }
                    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                    val shouldApply = automationConditionsMatch(config, network, power.isInteractive, hour)
                    val result = when {
                        shouldApply && !ownedTargetApplied -> applyTarget(config.target)
                        !shouldApply && ownedTargetApplied -> stopTarget(config.target)
                        else -> RootAutomationResult(
                            true,
                            ownedTargetApplied,
                            if (ownedTargetApplied) "Условия выполнены: ${config.target.displayName} включён." else "Ожидаем заданные условия.",
                        )
                    }
                    if (result.successful) ownedTargetApplied = result.running
                    targetApplied = ownedTargetApplied
                    lastMessage = result.message
                    handler.post { updateNotification(result.message) }
                } catch (error: Throwable) {
                    lastMessage = error.localizedMessage
                        ?.take(300)
                        ?: "Не удалось проверить условия root-автоматизации. Следующая проверка будет выполнена автоматически."
                    handler.post { updateNotification(lastMessage) }
                } finally {
                    evaluationRunning.set(false)
                }
            },
            "ViRouteFS-RootAutomationEvaluation",
        ).apply {
            isDaemon = true
            start()
        }
    }

    private fun applyTarget(target: RootAutomationTarget): RootAutomationResult = when (target) {
        RootAutomationTarget.ConnectionAdaptation -> ConnectionAdaptationController(applicationContext).start().let {
            RootAutomationResult(it.successful, it.running, it.message)
        }
        RootAutomationTarget.AppFirewall -> RootAppFirewallController(applicationContext).let { controller ->
            controller.apply(controller.loadConfig()).let {
                RootAutomationResult(it.successful, it.running, it.message)
            }
        }
        RootAutomationTarget.NetworkGuard -> RootNetworkGuardController(applicationContext).let { controller ->
            controller.apply(controller.loadConfig()).let {
                RootAutomationResult(it.successful, it.running, it.message)
            }
        }
    }

    private fun stopTarget(target: RootAutomationTarget): RootAutomationResult = when (target) {
        RootAutomationTarget.ConnectionAdaptation -> ConnectionAdaptationController(applicationContext).stop().let {
            RootAutomationResult(it.successful, it.running, it.message)
        }
        RootAutomationTarget.AppFirewall -> RootAppFirewallController(applicationContext).stop().let {
            RootAutomationResult(it.successful, it.running, it.message)
        }
        RootAutomationTarget.NetworkGuard -> RootNetworkGuardController(applicationContext).stop().let {
            RootAutomationResult(it.successful, it.running, it.message)
        }
    }

    private fun stopAutomationAsync() {
        handler.removeCallbacksAndMessages(null)
        unregisterSignals()
        if (!evaluationRunning.compareAndSet(false, true)) {
            handler.postDelayed({ stopAutomationAsync() }, 250L)
            return
        }
        Thread(
            {
                var result = RootAutomationResult(false, ownedTargetApplied, "Не удалось остановить root-автоматизацию.")
                try {
                    val config = configRepository.load()
                    result = if (ownedTargetApplied) stopTarget(config.target) else RootAutomationResult(true, false, "Автоматика остановлена.")
                    if (result.successful) {
                        ownedTargetApplied = false
                        targetApplied = false
                        runtime.removeModule(RootManagedModule.Automation)
                    }
                } catch (error: Throwable) {
                    result = RootAutomationResult(
                        false,
                        ownedTargetApplied,
                        error.localizedMessage?.take(300)
                            ?: "Остановка root-автоматизации не подтверждена. Повторите её из root-центра.",
                    )
                } finally {
                    lastMessage = result.message
                    evaluationRunning.set(false)
                    handler.post {
                        updateNotification(result.message)
                        if (result.successful) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    }
                }
            },
            "ViRouteFS-RootAutomationStop",
        ).apply {
            isDaemon = true
            start()
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                ROOT_AUTOMATION_CHANNEL_ID,
                "Root-автоматизация ViRouteFS",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun updateNotification(message: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(ROOT_AUTOMATION_NOTIFICATION_ID, buildNotification(message))
    }

    private fun buildNotification(message: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, RootAutomationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, ROOT_AUTOMATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_network_control_tile)
            .setContentTitle("Root-автоматизация ViRouteFS")
            .setContentText(message.take(120))
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.take(500)))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Остановить", stop)
            .build()
    }

    companion object {
        internal const val ACTION_START = "dev.vifs.viroutefs.root.AUTOMATION_START"
        internal const val ACTION_STOP = "dev.vifs.viroutefs.root.AUTOMATION_STOP"

        @Volatile internal var isRunning: Boolean = false
            private set
        @Volatile internal var targetApplied: Boolean = false
            private set
        @Volatile internal var lastMessage: String = "Автоматика выключена."
            private set

        internal fun requestStop(context: Context) {
            if (!isRunning) {
                RootRuntimeStateRepository(context.applicationContext).removeModule(RootManagedModule.Automation)
                return
            }
            context.applicationContext.startService(
                Intent(context.applicationContext, RootAutomationService::class.java).setAction(ACTION_STOP),
            )
        }

        internal fun awaitStopped(timeoutMillis: Long): Boolean {
            val deadline = System.currentTimeMillis() + timeoutMillis.coerceIn(0L, 15_000L)
            while (isRunning && System.currentTimeMillis() < deadline) {
                Thread.sleep(100L)
            }
            return !isRunning
        }
    }
}

internal fun automationConditionsMatch(
    config: RootAutomationConfig,
    currentNetwork: RootAutomationNetwork,
    screenInteractive: Boolean,
    hour: Int,
): Boolean {
    require(hour in 0..23)
    val networkMatches = config.network == RootAutomationNetwork.Any || config.network == currentNetwork
    val screenMatches = when (config.screen) {
        RootAutomationScreen.Any -> true
        RootAutomationScreen.On -> screenInteractive
        RootAutomationScreen.Off -> !screenInteractive
    }
    val timeMatches = when {
        config.usesWholeDay -> true
        config.startHour < config.endHour -> hour in config.startHour until config.endHour
        else -> hour >= config.startHour || hour < config.endHour
    }
    return networkMatches && screenMatches && timeMatches
}

private val RootAutomationTarget.managedModules: Set<RootManagedModule>
    get() = when (this) {
        RootAutomationTarget.ConnectionAdaptation -> setOf(RootManagedModule.ConnectionAdaptation)
        RootAutomationTarget.AppFirewall -> setOf(RootManagedModule.AppFirewall)
        RootAutomationTarget.NetworkGuard -> setOf(RootManagedModule.EmergencyNetworkLock, RootManagedModule.LeakProtection)
    }

private const val ROOT_AUTOMATION_CHANNEL_ID = "viroutefs_root_automation"
private const val ROOT_AUTOMATION_NOTIFICATION_ID = 620
private const val ROOT_AUTOMATION_INTERVAL_MILLIS = 30_000L
