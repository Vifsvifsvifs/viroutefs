// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vpn

import android.content.Context
import android.content.pm.PackageManager.NameNotFoundException
import android.net.ConnectivityManager
import android.net.DnsResolver
import android.net.IpPrefix
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.OsConstants
import android.util.Log
import androidx.annotation.RequiresApi
import go.Seq
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.CommandClient
import io.nekohasekai.libbox.CommandClientHandler
import io.nekohasekai.libbox.CommandClientOptions
import io.nekohasekai.libbox.BridgeOptions
import io.nekohasekai.libbox.BridgeSession
import io.nekohasekai.libbox.ConnectionEvents
import io.nekohasekai.libbox.Connections
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.ExchangeContext
import io.nekohasekai.libbox.Func
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.LogIterator
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.NeighborUpdateListener
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.OutboundGroupIterator
import io.nekohasekai.libbox.OutboundGroupItemIterator
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.PlatformUser
import io.nekohasekai.libbox.RoutePrefix
import io.nekohasekai.libbox.RoutePrefixIterator
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.ShellSession
import io.nekohasekai.libbox.StringBox
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.StatusMessage
import io.nekohasekai.libbox.SystemProxyStatus
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import dev.vifs.viroutefs.engine.SingBoxRoutingConfigCompiler
import dev.vifs.viroutefs.routing.RoutingConfig
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import io.nekohasekai.libbox.NetworkInterface as BoxNetworkInterface

/**
 * Android host for the pinned sing-box libbox runtime.
 *
 * sing-box requests its TUN through [openTun], while Android socket protection,
 * system DNS, CA certificates, network changes and app ownership remain under
 * ViRouteFS control. There is no telemetry and no packet payload logging.
 */
internal class SingBoxEngineRunner(
    private val service: ViRouteVpnService,
    private val onTunEstablished: (ParcelFileDescriptor) -> Unit,
    private val onLog: (String) -> Unit,
    private val onConnections: (List<VpnConnectionFlow>) -> Unit,
    private val managedProfileGroups: List<ManagedProfileGroup> = emptyList(),
    private val onProfileGroupAction: (ProfileGroupRuntimeAction) -> Unit = {},
) : PlatformInterface, CommandServerHandler {
    private val appContext = service.applicationContext
    private val connectivity =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val lock = Any()
    private var commandServer: CommandServer? = null
    private var commandClient: CommandClient? = null
    private var profileGroupController: ProfileGroupRuntimeController? = null
    private var connections = Connections()
    private var tunDescriptor: ParcelFileDescriptor? = null
    private var defaultInterfaceListener: InterfaceUpdateListener? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var underlyingNetwork: Network? = null
    private val running = AtomicBoolean(false)

    fun start(configJson: String): Result<Unit> {
        if (running.get()) return Result.success(Unit)
        return runCatching {
            SingBoxEnvironment.setup(appContext)
            Libbox.checkConfig(configJson)
            val server = CommandServer(this, this)
            server.start()
            synchronized(lock) { commandServer = server }
            server.startOrReloadService(
                configJson,
                OverrideOptions().apply {
                    excludePackage = StringArray(listOf(appContext.packageName))
                },
            )
            check(tunDescriptor != null) { "sing-box did not establish the Android TUN interface." }
            startConnectionMonitor()
            running.set(true)
            onLog("sing-box ${Libbox.version()} started the ViRouteFS TUN runtime.")
        }.onFailure { error ->
            running.set(false)
            closeResources()
            onLog("sing-box start failed: ${error.message ?: error::class.java.simpleName}")
        }
    }

    fun stop() {
        running.set(false)
        stopConnectionMonitor()
        val server = synchronized(lock) {
            val current = commandServer
            commandServer = null
            current
        }
        runCatching { server?.closeService() }
        runCatching { server?.close() }
        closeTun()
        stopNetworkMonitor()
    }

    fun isRunning(): Boolean = running.get() && tunDescriptor != null

    fun clearConnectionHistory() {
        synchronized(lock) {
            connections = Connections()
        }
        onConnections(emptyList())
    }

    override fun serviceStop() {
        running.set(false)
        closeTun()
    }

    override fun serviceReload() {
        onLog("sing-box requested a service reload; ViRouteFS will reload from its saved routing model.")
    }

    override fun getSystemProxyStatus(): SystemProxyStatus = SystemProxyStatus().apply {
        available = false
        enabled = false
    }

    override fun setSystemProxyEnabled(enabled: Boolean) {
        if (enabled) error("Android system HTTP proxy mode is not enabled by ViRouteFS.")
    }

    override fun writeDebugMessage(message: String?) {
        message?.takeIf(String::isNotBlank)?.let { onLog(it.take(MAX_LOG_LENGTH)) }
    }

    override fun connectSSHAgent(): Int =
        error("Android SSH agent forwarding is not enabled.")

    override fun triggerNativeCrash() {
        error("Native crash requests are disabled in ViRouteFS.")
    }

    private fun startConnectionMonitor() {
        val options = CommandClientOptions().apply {
            addCommand(Libbox.CommandConnections)
            if (managedProfileGroups.isNotEmpty()) addCommand(Libbox.CommandOutbounds)
        }
        val client = CommandClient(ConnectionClientHandler(), options)
        val groupController = managedProfileGroups.takeIf(List<*>::isNotEmpty)?.let {
            ProfileGroupRuntimeController(
                client = client,
                groups = managedProfileGroups,
                onAction = onProfileGroupAction,
                onLog = onLog,
            )
        }
        synchronized(lock) {
            commandClient = client
            profileGroupController = groupController
        }
        runCatching { client.connect() }
            .onSuccess {
                groupController?.start()
                onLog("Flow Scanner connected to the local sing-box connection stream.")
            }
            .onFailure { error ->
                synchronized(lock) {
                    if (commandClient === client) commandClient = null
                    if (profileGroupController === groupController) profileGroupController = null
                }
                groupController?.stop()
                runCatching { client.disconnect() }
                onLog("Flow Scanner connection stream is unavailable: ${error.message.orEmpty()}")
            }
    }

    private fun stopConnectionMonitor() {
        val groupController = synchronized(lock) {
            val current = profileGroupController
            profileGroupController = null
            current
        }
        groupController?.stop()
        val client = synchronized(lock) {
            val current = commandClient
            commandClient = null
            current
        }
        runCatching { client?.disconnect() }
        synchronized(lock) { connections = Connections() }
        onConnections(emptyList())
    }

    private inner class ConnectionClientHandler : CommandClientHandler {
        override fun connected() = Unit

        override fun disconnected(message: String?) {
            message?.takeIf(String::isNotBlank)?.let {
                onLog("Flow Scanner stream disconnected: ${it.take(MAX_LOG_LENGTH)}")
            }
        }

        override fun initializeClashMode(modeList: StringIterator?, currentMode: String?) = Unit

        override fun updateClashMode(newMode: String?) = Unit

        override fun clearLogs() = Unit

        override fun setDefaultLogLevel(level: Int) = Unit

        override fun writeGroups(message: OutboundGroupIterator?) = Unit

        override fun writeOutbounds(message: OutboundGroupItemIterator?) {
            synchronized(lock) { profileGroupController }?.updateOutbounds(message)
        }

        override fun writeLogs(messageList: LogIterator?) = Unit

        override fun writeStatus(message: StatusMessage?) = Unit

        override fun writeConnectionEvents(events: ConnectionEvents?) {
            if (events == null) return
            val controller = synchronized(lock) { profileGroupController }
            if (controller != null) {
                val eventIterator = events.iterator()
                while (eventIterator.hasNext()) {
                    val event = eventIterator.next()
                    if (event.type.toLong() != Libbox.ConnectionEventNew) continue
                    val connection = event.connection ?: continue
                    controller.onNewConnection(connection.chain().toStrings())
                }
            }
            val snapshot = synchronized(lock) {
                connections.applyEvents(events)
                connections.sortByDate()
                val iterator = connections.iterator()
                buildList {
                    while (iterator.hasNext()) {
                        val connection = iterator.next()
                        if (connection.outboundType == "dns") continue
                        val processInfo = connection.processInfo
                        add(
                            VpnConnectionFlow(
                                id = connection.id,
                                createdAt = connection.createdAt,
                                closedAt = connection.closedAt.takeIf { it > 0L },
                                network = connection.network,
                                source = connection.source,
                                destination = connection.destination,
                                domain = connection.domain,
                                protocol = connection.protocol,
                                appPackages = processInfo?.packageNames()?.toStrings().orEmpty(),
                                processPath = processInfo?.processPath.orEmpty(),
                                outboundTag = connection.outbound,
                                outboundType = connection.outboundType,
                                matchedRule = connection.rule,
                                uplinkBytes = connection.uplinkTotal,
                                downlinkBytes = connection.downlinkTotal,
                            ),
                        )
                    }
                }.take(MAX_FLOW_HISTORY)
            }
            onConnections(snapshot)
        }
    }

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    override fun autoDetectInterfaceControl(fd: Int) {
        check(service.protect(fd)) { "Android refused to protect an outbound socket from the VPN loop." }
    }

    override fun openTun(options: TunOptions): Int {
        check(android.net.VpnService.prepare(service) == null) { "Android VPN permission is missing." }
        val builder = service.Builder()
            .setSession("ViRouteFS")
            .setMtu(options.mtu.coerceIn(MIN_MTU, MAX_MTU))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)

        options.inet4Address.toRoutePrefixes().forEach { builder.addAddress(it.address(), it.prefix()) }
        options.inet6Address.toRoutePrefixes().forEach { builder.addAddress(it.address(), it.prefix()) }

        if (options.autoRoute) {
            runCatching { options.dnsServerAddress.toStrings() }
                .getOrDefault(emptyList())
                .filter(String::isNotBlank)
                .distinct()
                .forEach { builder.addDnsServer(it) }
            addAndroidRoutes(builder, options)
            applyPackagePolicy(builder, options)
        }

        val descriptor = builder.establish()
            ?: error("Android returned no TUN descriptor.")
        synchronized(lock) {
            closeTun()
            tunDescriptor = descriptor
        }
        onTunEstablished(descriptor)
        return descriptor.fd
    }

    private fun addAndroidRoutes(builder: android.net.VpnService.Builder, options: TunOptions) {
        val ipv4 = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            options.inet4RouteAddress.toRoutePrefixes()
        } else {
            options.inet4RouteRange.toRoutePrefixes()
        }
        val ipv6 = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            options.inet6RouteAddress.toRoutePrefixes()
        } else {
            options.inet6RouteRange.toRoutePrefixes()
        }
        val actualIpv4 = ipv4.ifEmpty { listOf(SimpleRoutePrefix("0.0.0.0", 0)) }
        val actualIpv6 = ipv6.ifEmpty { listOf(SimpleRoutePrefix("::", 0)) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            actualIpv4.forEach { builder.addRoute(IpPrefix(InetAddress.getByName(it.address()), it.prefix())) }
            actualIpv6.forEach { builder.addRoute(IpPrefix(InetAddress.getByName(it.address()), it.prefix())) }
            options.inet4RouteExcludeAddress.toRoutePrefixes()
                .forEach { builder.excludeRoute(IpPrefix(InetAddress.getByName(it.address()), it.prefix())) }
            options.inet6RouteExcludeAddress.toRoutePrefixes()
                .forEach { builder.excludeRoute(IpPrefix(InetAddress.getByName(it.address()), it.prefix())) }
        } else {
            actualIpv4.forEach { builder.addRoute(it.address(), it.prefix()) }
            actualIpv6.forEach { builder.addRoute(it.address(), it.prefix()) }
        }
    }

    private fun applyPackagePolicy(builder: android.net.VpnService.Builder, options: TunOptions) {
        val included = options.includePackage.toStrings()
        if (included.isNotEmpty()) {
            included.filterNot { it == appContext.packageName }.forEach { packageName ->
                try {
                    builder.addAllowedApplication(packageName)
                } catch (_: NameNotFoundException) {
                    onLog("Configured app '$packageName' is not installed and was skipped.")
                }
            }
            return
        }
        (options.excludePackage.toStrings() + appContext.packageName).distinct().forEach { packageName ->
            try {
                builder.addDisallowedApplication(packageName)
            } catch (_: NameNotFoundException) {
                onLog("Excluded app '$packageName' is not installed and was skipped.")
            }
        }
    }

    override fun useProcFS(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String?,
        sourcePort: Int,
        destinationAddress: String?,
        destinationPort: Int,
    ): ConnectionOwner {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Per-app routing requires Android 10 or newer."
        }
        val source = sourceAddress?.takeIf(String::isNotBlank)
            ?: error("Missing source address.")
        val destination = destinationAddress?.takeIf(String::isNotBlank)
            ?: error("Missing destination address.")
        val uid = connectivity.getConnectionOwnerUid(
            ipProtocol,
            InetSocketAddress(InetAddress.getByName(source), sourcePort),
            InetSocketAddress(InetAddress.getByName(destination), destinationPort),
        )
        check(uid >= 0) { "Android could not identify the connection owner." }
        val packages = appContext.packageManager.getPackagesForUid(uid).orEmpty().toList()
        return ConnectionOwner().apply {
            userId = uid
            userName = packages.firstOrNull().orEmpty()
            processPath = ""
            setAndroidPackageNames(StringArray(packages))
        }
    }

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        defaultInterfaceListener = listener
        if (networkCallback != null) {
            publishDefaultInterface()
            return
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (!network.isPhysicalInternetNetwork()) return
                underlyingNetwork = network
                publishDefaultInterface()
            }

            override fun onLinkPropertiesChanged(
                network: Network,
                linkProperties: android.net.LinkProperties,
            ) {
                if (network == underlyingNetwork) publishDefaultInterface()
            }

            override fun onLost(network: Network) {
                if (network == underlyingNetwork) {
                    underlyingNetwork = selectPhysicalInternetNetwork()
                    publishDefaultInterface()
                }
            }
        }
        networkCallback = callback
        underlyingNetwork = selectPhysicalInternetNetwork()
        connectivity.registerNetworkCallback(
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(),
            callback,
        )
        publishDefaultInterface()
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) {
        if (listener != null && listener !== defaultInterfaceListener) return
        stopNetworkMonitor()
    }

    private fun stopNetworkMonitor() {
        networkCallback?.let { callback ->
            runCatching { connectivity.unregisterNetworkCallback(callback) }
        }
        networkCallback = null
        defaultInterfaceListener = null
        underlyingNetwork = null
    }

    private fun publishDefaultInterface() {
        val listener = defaultInterfaceListener ?: return
        val network = underlyingNetwork
        val link = network?.let(connectivity::getLinkProperties)
        val name = link?.interfaceName.orEmpty()
        val index = runCatching { NetworkInterface.getByName(name)?.index ?: -1 }.getOrDefault(-1)
        val capabilities = network?.let(connectivity::getNetworkCapabilities)
        val congested = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED) == false
        } else {
            false
        }
        listener.updateDefaultInterface(
            name,
            index,
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false,
            congested,
        )
    }

    override fun getInterfaces(): NetworkInterfaceIterator {
        val androidNetworks = connectivity.allNetworks.mapNotNull { network ->
            val link = connectivity.getLinkProperties(network) ?: return@mapNotNull null
            val capabilities = connectivity.getNetworkCapabilities(network) ?: return@mapNotNull null
            link.interfaceName to (link to capabilities)
        }.toMap()
        val values = Collections.list(NetworkInterface.getNetworkInterfaces()).map { networkInterface ->
            val androidInfo = androidNetworks[networkInterface.name]
            val link = androidInfo?.first
            val capabilities = androidInfo?.second
            BoxNetworkInterface().apply {
                index = networkInterface.index
                name = networkInterface.name
                mtu = runCatching { networkInterface.mtu }.getOrDefault(1500)
                addresses = StringArray(
                    networkInterface.interfaceAddresses.mapNotNull { interfaceAddress ->
                        val host = interfaceAddress.address?.hostAddress ?: return@mapNotNull null
                        val normalized = if (interfaceAddress.address is Inet6Address) {
                            Inet6Address.getByAddress(interfaceAddress.address.address).hostAddress
                        } else {
                            host
                        }
                        "$normalized/${interfaceAddress.networkPrefixLength}"
                    },
                )
                dnsServer = StringArray(link?.dnsServers?.mapNotNull(InetAddress::getHostAddress).orEmpty())
                type = when {
                    capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ->
                        Libbox.InterfaceTypeWIFI
                    capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true ->
                        Libbox.InterfaceTypeCellular
                    capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true ->
                        Libbox.InterfaceTypeEthernet
                    else -> Libbox.InterfaceTypeOther
                }
                flags = buildInterfaceFlags(networkInterface)
                metered = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false
            }
        }
        return NetworkInterfaceArray(values)
    }

    override fun underNetworkExtension(): Boolean = false

    override fun includeAllNetworks(): Boolean = false

    override fun localDNSTransport(): LocalDNSTransport = AndroidLocalDnsTransport()

    override fun readWIFIState(): WIFIState? = null

    override fun clearDNSCache() = Unit

    override fun usePlatformShell(): Boolean = false

    override fun checkPlatformShell() {
        error("Platform shell access is intentionally disabled.")
    }

    override fun openShellSession(
        user: PlatformUser?,
        command: String?,
        environ: StringIterator?,
        term: String?,
        rows: Int,
        cols: Int,
    ): ShellSession = error("Platform shell access is intentionally disabled.")

    override fun readSystemSSHHostKey(): String =
        error("Reading the Android system SSH host key is not supported.")

    override fun lookupSFTPServer(): String =
        error("The optional platform SFTP helper is not bundled.")

    override fun usePlatformBridge(): Boolean = false

    override fun createBridge(options: BridgeOptions?): BridgeSession =
        error("Platform bridge mode is not available without a separately audited privileged adapter.")

    override fun lookupUser(username: String?): PlatformUser =
        error("Platform user lookup is not available in the unprivileged Android runtime.")

    override fun startNeighborMonitor(listener: NeighborUpdateListener?) = Unit

    override fun closeNeighborMonitor(listener: NeighborUpdateListener?) = Unit

    override fun tailscaleHostname(): String =
        "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "ViRouteFS Android" }

    override fun registerMyInterface(name: String?) = Unit

    override fun sendNotification(notification: Notification?) {
        notification ?: return
        val message = listOf(notification.title, notification.subtitle, notification.body)
            .filter(String::isNotBlank)
            .joinToString(" — ")
        if (message.isNotBlank()) onLog(message.take(MAX_LOG_LENGTH))
    }

    private fun closeResources() {
        stopConnectionMonitor()
        val server = commandServer
        commandServer = null
        runCatching { server?.closeService() }
        runCatching { server?.close() }
        closeTun()
        stopNetworkMonitor()
    }

    private fun closeTun() {
        val descriptor = synchronized(lock) {
            val current = tunDescriptor
            tunDescriptor = null
            current
        }
        descriptor?.let { runCatching { it.close() } }
    }

    private fun selectPhysicalInternetNetwork(): Network? =
        connectivity.allNetworks.firstOrNull { it.isPhysicalInternetNetwork() }

    private fun Network.isPhysicalInternetNetwork(): Boolean {
        val capabilities = connectivity.getNetworkCapabilities(this) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    private inner class AndroidLocalDnsTransport : LocalDNSTransport {
        override fun raw(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        @RequiresApi(Build.VERSION_CODES.Q)
        override fun exchange(ctx: ExchangeContext, message: ByteArray) {
            check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            val network = underlyingNetwork ?: selectPhysicalInternetNetwork()
                ?: error("No physical network is available for Android system DNS.")
            val latch = CountDownLatch(1)
            val signal = CancellationSignal()
            ctx.onCancel(Func { signal.cancel() })
            DnsResolver.getInstance().rawQuery(
                network,
                message,
                DnsResolver.FLAG_NO_RETRY,
                appContext.mainExecutor,
                signal,
                object : DnsResolver.Callback<ByteArray> {
                    override fun onAnswer(answer: ByteArray, rcode: Int) {
                        if (rcode == 0) ctx.rawSuccess(answer) else ctx.errorCode(rcode)
                        latch.countDown()
                    }

                    override fun onError(error: DnsResolver.DnsException) {
                        reportDnsError(ctx, error)
                        latch.countDown()
                    }
                },
            )
            check(latch.await(DNS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "Android system DNS timed out." }
        }

        override fun lookup(ctx: ExchangeContext, networkName: String?, domain: String?) {
            val domainName = domain?.takeIf(String::isNotBlank) ?: error("Missing DNS name.")
            val network = underlyingNetwork ?: selectPhysicalInternetNetwork()
                ?: error("No physical network is available for Android system DNS.")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val answer = runCatching { network.getAllByName(domainName) }.getOrElse {
                    ctx.errorCode(DNS_RCODE_NAME_ERROR)
                    return
                }
                ctx.success(answer.mapNotNull(InetAddress::getHostAddress).joinToString("\n"))
                return
            }

            val latch = CountDownLatch(1)
            val signal = CancellationSignal()
            ctx.onCancel(Func { signal.cancel() })
            val callback = object : DnsResolver.Callback<Collection<InetAddress>> {
                override fun onAnswer(answer: Collection<InetAddress>, rcode: Int) {
                    if (rcode == 0) {
                        ctx.success(answer.mapNotNull(InetAddress::getHostAddress).joinToString("\n"))
                    } else {
                        ctx.errorCode(rcode)
                    }
                    latch.countDown()
                }

                override fun onError(error: DnsResolver.DnsException) {
                    reportDnsError(ctx, error)
                    latch.countDown()
                }
            }
            val type = when {
                networkName?.endsWith("4") == true -> DnsResolver.TYPE_A
                networkName?.endsWith("6") == true -> DnsResolver.TYPE_AAAA
                else -> null
            }
            if (type == null) {
                DnsResolver.getInstance().query(
                    network,
                    domainName,
                    DnsResolver.FLAG_NO_RETRY,
                    appContext.mainExecutor,
                    signal,
                    callback,
                )
            } else {
                DnsResolver.getInstance().query(
                    network,
                    domainName,
                    type,
                    DnsResolver.FLAG_NO_RETRY,
                    appContext.mainExecutor,
                    signal,
                    callback,
                )
            }
            check(latch.await(DNS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "Android system DNS timed out." }
        }
    }

    private fun reportDnsError(ctx: ExchangeContext, error: DnsResolver.DnsException) {
        val cause = error.cause
        if (cause is ErrnoException) {
            ctx.errnoCode(cause.errno)
        } else {
            ctx.errorCode(DNS_RCODE_SERVER_FAILURE)
        }
    }

    private companion object {
        const val MIN_MTU = 1280
        const val MAX_MTU = 9000
        const val MAX_LOG_LENGTH = 600
        const val MAX_FLOW_HISTORY = 250
        const val DNS_TIMEOUT_SECONDS = 30L
        const val DNS_RCODE_SERVER_FAILURE = 2
        const val DNS_RCODE_NAME_ERROR = 3

        fun buildInterfaceFlags(networkInterface: NetworkInterface): Int {
            var flags = 0
            if (runCatching { networkInterface.isUp }.getOrDefault(false)) {
                flags = flags or OsConstants.IFF_UP or OsConstants.IFF_RUNNING
            }
            if (runCatching { networkInterface.isLoopback }.getOrDefault(false)) {
                flags = flags or OsConstants.IFF_LOOPBACK
            }
            if (runCatching { networkInterface.isPointToPoint }.getOrDefault(false)) {
                flags = flags or OsConstants.IFF_POINTOPOINT
            }
            if (runCatching { networkInterface.supportsMulticast() }.getOrDefault(false)) {
                flags = flags or OsConstants.IFF_MULTICAST
            }
            return flags
        }
    }
}

internal object SingBoxEnvironment {
    private val initialized = AtomicBoolean(false)
    private val lock = Any()

    fun setup(context: Context) {
        if (initialized.get()) return
        synchronized(lock) {
            if (initialized.get()) return
            Seq.setContext(context.applicationContext)
            val baseDirectory = context.filesDir.apply { mkdirs() }
            val workingDirectory = context.getExternalFilesDir(null) ?: baseDirectory
            workingDirectory.mkdirs()
            context.cacheDir.mkdirs()
            Libbox.setup(
                SetupOptions().apply {
                    basePath = baseDirectory.absolutePath
                    workingPath = workingDirectory.absolutePath
                    tempPath = context.cacheDir.absolutePath
                    fixAndroidStack = true
                    logMaxLines = 500
                    debug = false
                },
            )
            initialized.set(true)
        }
    }
}

internal object SingBoxRuntimeValidator {
    fun validate(context: Context, config: RoutingConfig): Result<List<String>> = runCatching {
        SingBoxEnvironment.setup(context.applicationContext)
        val compiled = SingBoxRoutingConfigCompiler().compile(config)
        Libbox.checkConfig(compiled.json)
        compiled.warnings
    }
}

private class StringArray(values: Collection<String>) : StringIterator {
    private val snapshot = values.toList()
    private val iterator = snapshot.iterator()

    override fun len(): Int = snapshot.size

    override fun hasNext(): Boolean = iterator.hasNext()

    override fun next(): String = iterator.next()
}

private class NetworkInterfaceArray(
    values: Collection<BoxNetworkInterface>,
) : NetworkInterfaceIterator {
    private val iterator = values.iterator()

    override fun hasNext(): Boolean = iterator.hasNext()

    override fun next(): BoxNetworkInterface = iterator.next()
}

private interface AddressPrefix {
    fun address(): String
    fun prefix(): Int
}

private data class SimpleRoutePrefix(
    private val value: String,
    private val length: Int,
) : AddressPrefix {
    override fun address(): String = value
    override fun prefix(): Int = length
}

private fun RoutePrefixIterator?.toRoutePrefixes(): List<AddressPrefix> {
    if (this == null) return emptyList()
    return buildList {
        while (hasNext()) {
            val prefix: RoutePrefix = next()
            add(SimpleRoutePrefix(prefix.address(), prefix.prefix()))
        }
    }
}

private fun StringIterator?.toStrings(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        while (hasNext()) add(next())
    }
}
