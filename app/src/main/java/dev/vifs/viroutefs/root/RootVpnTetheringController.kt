// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.root

import android.content.Context
import android.os.Process
import dev.vifs.viroutefs.vpn.VpnServiceController
import dev.vifs.viroutefs.vpn.VpnServiceStatus

data class RootNetworkInterface(
    val name: String,
    val ipv4Cidr: String,
    val networkCidr: String,
    val isTunnel: Boolean,
    val isLikelyDownstream: Boolean,
    val isDefaultInternet: Boolean,
)

data class RootTetheringProbeResult(
    val successful: Boolean,
    val interfaces: List<RootNetworkInterface> = emptyList(),
    val message: String,
)

data class RootVpnTetheringResult(
    val successful: Boolean,
    val running: Boolean,
    val message: String,
)

class RootVpnTetheringController(context: Context) {
    private val appContext = context.applicationContext
    private val access = RootAccessController(appContext)
    private val executor = RootCommandExecutor()
    private val repository = RootRuntimeStateRepository(appContext)
    private val recovery = RootNetworkRecoveryController(appContext)
    private val vpn = VpnServiceController(appContext)

    fun isRunning(): Boolean = RootManagedModule.Tethering in repository.load()?.modules.orEmpty()

    fun discoverInterfaces(): RootTetheringProbeResult {
        val probe = access.requestAndProbe()
        if (!probe.granted) return RootTetheringProbeResult(false, message = probe.message)
        val result = executor.execute(ROOT_TETHERING_PROBE_SCRIPT, ROOT_TETHERING_PROBE_TIMEOUT_MILLIS)
        if (!result.completed || result.exitCode != 0) {
            return RootTetheringProbeResult(
                successful = false,
                message = "Не удалось безопасно прочитать сетевые интерфейсы через root.",
            )
        }
        val interfaces = parseRootTetheringProbe(result.output)
        return RootTetheringProbeResult(
            successful = true,
            interfaces = interfaces,
            message = when {
                interfaces.none(RootNetworkInterface::isTunnel) ->
                    "VPN-интерфейс не найден. Сначала включите сетевой контроль ViRouteFS."
                interfaces.none(RootNetworkInterface::isLikelyDownstream) ->
                    "Интерфейс точки доступа не найден. Сначала включите hotspot или USB/Bluetooth-модем."
                else -> "Интерфейсы прочитаны. Проверьте выбранную пару перед включением раздачи."
            },
        )
    }

    fun start(
        downstream: RootNetworkInterface,
        tunnel: RootNetworkInterface,
    ): RootVpnTetheringResult {
        if (vpn.currentState().status != VpnServiceStatus.RuntimeActive) {
            return RootVpnTetheringResult(
                false,
                false,
                "Сначала включите обычный сетевой контроль ViRouteFS. Root-раздача не запускает VPN сама.",
            )
        }
        require(downstream.isLikelyDownstream && !downstream.isTunnel) { "Unsafe downstream interface." }
        require(tunnel.isTunnel && downstream.name != tunnel.name) { "Unsafe tunnel interface." }
        val probe = access.requestAndProbe()
        val capabilities = probe.snapshot
        if (!probe.granted || capabilities == null) {
            return RootVpnTetheringResult(false, false, probe.message)
        }
        if (!capabilities.hasIp || !capabilities.hasIptables || !capabilities.hasIp6tables) {
            return RootVpnTetheringResult(
                false,
                false,
                "Нужны команды ip, iptables и ip6tables. Раздача не включена.",
            )
        }
        val stateFile = repository.tetheringStateFile()
        runCatching { stateFile.writeText("", Charsets.US_ASCII) }.getOrElse {
            return RootVpnTetheringResult(false, false, "Не удалось подготовить состояние отката раздачи.")
        }
        repository.markPending(RootManagedModule.Tethering, "iptables_policy_route")
        val result = executor.execute(
            rootVpnTetheringStartScript(
                downstreamInterface = downstream.name,
                downstreamAddressCidr = downstream.ipv4Cidr,
                downstreamNetworkCidr = downstream.networkCidr,
                tunnelInterface = tunnel.name,
                stateFile = stateFile.absolutePath,
                appUid = Process.myUid(),
            ),
            ROOT_TETHERING_START_TIMEOUT_MILLIS,
        )
        if (!result.completed || result.exitCode != 0) {
            val recovered = recovery.recoverTethering()
            return RootVpnTetheringResult(
                successful = false,
                running = false,
                message = if (recovered.successful) {
                    "Раздача не запустилась; маршруты, NAT и IPv4 forwarding автоматически возвращены назад."
                } else {
                    "Запуск раздачи прерван, но откат не подтверждён. Используйте общую очистку root-центра."
                },
            )
        }
        return RootVpnTetheringResult(
            successful = true,
            running = true,
            message = "IPv4 клиентов направлен только в текущий VPN-интерфейс. Прямой выход и IPv6 клиентов заблокированы от утечки.",
        )
    }

    fun stop(): RootVpnTetheringResult {
        val result = recovery.recoverTethering()
        return RootVpnTetheringResult(
            successful = result.successful,
            running = !result.successful,
            message = result.message,
        )
    }
}

internal fun parseRootTetheringProbe(output: String): List<RootNetworkInterface> {
    val defaultInterfaces = output.lineSequence()
        .filter { it.startsWith("default=") }
        .map { it.substringAfter('=').trim() }
        .filter(::isSafeInterfaceName)
        .toSet()
    return output.lineSequence()
        .filter { it.startsWith("addr=") }
        .mapNotNull { line ->
            val fields = line.substringAfter('=').split('|')
            if (fields.size != 2) return@mapNotNull null
            val name = fields[0].trim()
            val addressCidr = fields[1].trim()
            if (!isSafeInterfaceName(name)) return@mapNotNull null
            val networkCidr = normalizePrivateIpv4Network(addressCidr) ?: return@mapNotNull null
            val lower = name.lowercase()
            val isTunnel = TUNNEL_INTERFACE_PATTERNS.any { it.matches(lower) }
            val isDefault = name in defaultInterfaces
            val likelyDownstream = !isTunnel && !isDefault &&
                DOWNSTREAM_INTERFACE_PATTERNS.any { it.matches(lower) }
            RootNetworkInterface(
                name = name,
                ipv4Cidr = addressCidr,
                networkCidr = networkCidr,
                isTunnel = isTunnel,
                isLikelyDownstream = likelyDownstream,
                isDefaultInternet = isDefault,
            )
        }
        .distinctBy { it.name to it.ipv4Cidr }
        .take(ROOT_TETHERING_MAX_INTERFACES)
        .toList()
}

internal fun rootVpnTetheringStartScript(
    downstreamInterface: String,
    downstreamAddressCidr: String,
    downstreamNetworkCidr: String,
    tunnelInterface: String,
    stateFile: String,
    appUid: Int,
): String {
    require(isSafeInterfaceName(downstreamInterface)) { "Unsafe downstream interface." }
    require(isSafeInterfaceName(tunnelInterface)) { "Unsafe tunnel interface." }
    require(downstreamInterface != tunnelInterface) { "Interfaces must be different." }
    require(normalizePrivateIpv4Network(downstreamAddressCidr) == downstreamNetworkCidr) {
        "Unsafe downstream network."
    }
    require(appUid in 10_000..99_999_999) { "Unsafe application UID." }
    val down = shellQuote(downstreamInterface)
    val downAddress = shellQuote(downstreamAddressCidr)
    val downNetwork = shellQuote(downstreamNetworkCidr)
    val tunnel = shellQuote(tunnelInterface)
    val state = shellQuote(stateFile)
    return """
        ${rootTetheringCleanupScript(stateFile)}
        set -eu
        committed=0
        rollback_tethering() {
          if [ "${'$'}committed" != 1 ]; then
            ${rootTetheringCleanupScript(stateFile)}
          fi
        }
        trap rollback_tethering EXIT
        trap 'rollback_tethering; exit 1' HUP INT TERM
        [ -d "/sys/class/net/$downstreamInterface" ]
        [ -d "/sys/class/net/$tunnelInterface" ]
        ip -o -4 addr show dev $down | grep -F -q -- " $downAddress "
        previous_forward="${'$'}(cat /proc/sys/net/ipv4/ip_forward)"
        case "${'$'}previous_forward" in 0|1) ;; *) exit 1 ;; esac
        printf 'v1|%s|%s|%s\n' "${'$'}previous_forward" $down $tunnel > $state
        chown $appUid:$appUid $state
        chmod 600 $state
        printf 1 > /proc/sys/net/ipv4/ip_forward
        ip -4 route add default dev $tunnel table $ROOT_TETHERING_ROUTE_TABLE
        ip -4 rule add priority $ROOT_TETHERING_RULE_PRIORITY iif $down lookup $ROOT_TETHERING_ROUTE_TABLE

        iptables -t filter -N VIROUTEFS_TETHER_FWD
        iptables -t filter -A VIROUTEFS_TETHER_FWD -i $down -o $tunnel -j ACCEPT
        iptables -t filter -A VIROUTEFS_TETHER_FWD -i $tunnel -o $down -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT
        iptables -t filter -A VIROUTEFS_TETHER_FWD -i $down -j REJECT
        iptables -t filter -A VIROUTEFS_TETHER_FWD -o $down -j REJECT
        iptables -t filter -A VIROUTEFS_TETHER_FWD -j RETURN
        iptables -t filter -I FORWARD 1 -j VIROUTEFS_TETHER_FWD

        iptables -t nat -N VIROUTEFS_TETHER_NAT
        iptables -t nat -A VIROUTEFS_TETHER_NAT -s $downNetwork -o $tunnel -j MASQUERADE
        iptables -t nat -A VIROUTEFS_TETHER_NAT -j RETURN
        iptables -t nat -I POSTROUTING 1 -j VIROUTEFS_TETHER_NAT

        iptables -t mangle -N VIROUTEFS_TETHER_MSS
        iptables -t mangle -A VIROUTEFS_TETHER_MSS -i $down -o $tunnel -p tcp --tcp-flags SYN,RST SYN -j TCPMSS --clamp-mss-to-pmtu
        iptables -t mangle -A VIROUTEFS_TETHER_MSS -j RETURN
        iptables -t mangle -I FORWARD 1 -j VIROUTEFS_TETHER_MSS

        ip6tables -t filter -N VIROUTEFS_TETHER_FWD
        ip6tables -t filter -A VIROUTEFS_TETHER_FWD -i $down -j REJECT
        ip6tables -t filter -A VIROUTEFS_TETHER_FWD -o $down -j REJECT
        ip6tables -t filter -A VIROUTEFS_TETHER_FWD -j RETURN
        ip6tables -t filter -I FORWARD 1 -j VIROUTEFS_TETHER_FWD

        ip -4 rule show | grep -q "^$ROOT_TETHERING_RULE_PRIORITY:.*iif $downstreamInterface.*lookup $ROOT_TETHERING_ROUTE_TABLE"
        iptables -t filter -C FORWARD -j VIROUTEFS_TETHER_FWD
        iptables -t nat -C POSTROUTING -j VIROUTEFS_TETHER_NAT
        iptables -t mangle -C FORWARD -j VIROUTEFS_TETHER_MSS
        ip6tables -t filter -C FORWARD -j VIROUTEFS_TETHER_FWD
        committed=1
        trap - EXIT HUP INT TERM
        printf 'viroutefs_vpn_tethering=running\n'
    """.trimIndent()
}

private fun normalizePrivateIpv4Network(cidr: String): String? {
    val address = cidr.substringBefore('/')
    val prefix = cidr.substringAfter('/', missingDelimiterValue = "").toIntOrNull() ?: return null
    if (prefix !in 8..30) return null
    val octets = address.split('.').map { it.toIntOrNull() ?: return null }
    if (octets.size != 4 || octets.any { it !in 0..255 }) return null
    val privateAddress = octets[0] == 10 ||
        octets[0] == 172 && octets[1] in 16..31 ||
        octets[0] == 192 && octets[1] == 168
    if (!privateAddress) return null
    val value = octets.fold(0L) { result, octet -> (result shl 8) or octet.toLong() }
    val mask = (0xffff_ffffL shl (32 - prefix)) and 0xffff_ffffL
    val network = value and mask
    return listOf(24, 16, 8, 0).joinToString(".") { shift -> ((network shr shift) and 0xff).toString() } + "/$prefix"
}

private fun isSafeInterfaceName(value: String): Boolean =
    value.matches(Regex("[A-Za-z0-9_.-]{1,32}"))

private val TUNNEL_INTERFACE_PATTERNS = listOf(
    Regex("tun[0-9]*"),
    Regex("wg[0-9]*"),
    Regex("sing.*"),
    Regex("ppp[0-9]*"),
)
private val DOWNSTREAM_INTERFACE_PATTERNS = listOf(
    Regex("ap[0-9]+"),
    Regex("wlan[1-9][0-9]*"),
    Regex("swlan[0-9]*"),
    Regex("softap[0-9]*"),
    Regex("rndis[0-9]*"),
    Regex("usb[0-9]*"),
    Regex("bnep[0-9]*"),
    Regex("bt-pan"),
)

private val ROOT_TETHERING_PROBE_SCRIPT = """
    if ! command -v ip >/dev/null 2>&1; then exit 2; fi
    ip -o -4 route show default 2>/dev/null | awk '{for (i=1;i<=NF;i++) if (${'$'}i=="dev" && i<NF) print "default=" ${'$'}(i+1)}' | head -n 16
    ip -o -4 addr show 2>/dev/null | awk '{print "addr=" ${'$'}2 "|" ${'$'}4}' | head -n $ROOT_TETHERING_MAX_INTERFACES
""".trimIndent()

private const val ROOT_TETHERING_ROUTE_TABLE = 62_241
private const val ROOT_TETHERING_RULE_PRIORITY = 16_220
private const val ROOT_TETHERING_MAX_INTERFACES = 64
private const val ROOT_TETHERING_PROBE_TIMEOUT_MILLIS = 20_000L
private const val ROOT_TETHERING_START_TIMEOUT_MILLIS = 25_000L
