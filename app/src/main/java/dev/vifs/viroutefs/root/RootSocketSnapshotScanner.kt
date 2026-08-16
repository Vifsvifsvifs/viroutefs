// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.root

import android.content.Context
import java.net.InetAddress

data class RootSocketSnapshot(
    val protocol: String,
    val localAddress: String,
    val localPort: Int,
    val remoteAddress: String,
    val remotePort: Int,
    val state: String,
    val uid: Int,
    val packageNames: List<String>,
)

data class RootSocketScanResult(
    val successful: Boolean,
    val sockets: List<RootSocketSnapshot>,
    val message: String,
)

class RootSocketSnapshotScanner(context: Context) {
    private val appContext = context.applicationContext
    private val access = RootAccessController(appContext)
    private val executor = RootCommandExecutor()

    fun scan(): RootSocketScanResult {
        val probe = access.requestAndProbe()
        if (!probe.granted) return RootSocketScanResult(false, emptyList(), probe.message)
        val execution = runCatching {
            executor.execute(ROOT_SOCKET_SNAPSHOT_SCRIPT, ROOT_SOCKET_SCAN_TIMEOUT_MILLIS)
        }.getOrNull()
        if (execution == null || !execution.completed || execution.exitCode != 0) {
            return RootSocketScanResult(
                false,
                emptyList(),
                "Не удалось прочитать ограниченный снимок таблиц сокетов через root.",
            )
        }
        val parsed = parseRootSocketTables(execution.output)
            .take(ROOT_SOCKET_MAX_TOTAL)
            .map { socket ->
                socket.copy(
                    packageNames = appContext.packageManager.getPackagesForUid(socket.uid)
                        .orEmpty()
                        .distinct()
                        .sorted(),
                )
            }
        return RootSocketScanResult(
            true,
            parsed,
            "Root-снимок: ${parsed.size} TCP/UDP-сокетов. Содержимое пакетов и TLS не читались.",
        )
    }
}

internal fun parseRootSocketTables(output: String): List<RootSocketSnapshot> {
    val result = mutableListOf<RootSocketSnapshot>()
    var protocol: String? = null
    output.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        if (line.startsWith(ROOT_SOCKET_MARKER)) {
            protocol = line.removePrefix(ROOT_SOCKET_MARKER).trim().takeIf {
                it in setOf("tcp4", "tcp6", "udp4", "udp6")
            }
            return@forEach
        }
        val currentProtocol = protocol ?: return@forEach
        if (line.isBlank() || line.startsWith("sl")) return@forEach
        val fields = line.split(Regex("\\s+"))
        if (fields.size < 10) return@forEach
        val local = parseProcSocketAddress(fields[1], currentProtocol.endsWith("6")) ?: return@forEach
        val remote = parseProcSocketAddress(fields[2], currentProtocol.endsWith("6")) ?: return@forEach
        val uid = fields[7].toIntOrNull()?.takeIf { it >= 0 } ?: return@forEach
        result += RootSocketSnapshot(
            protocol = currentProtocol.removeSuffix("4").removeSuffix("6").uppercase(),
            localAddress = local.first,
            localPort = local.second,
            remoteAddress = remote.first,
            remotePort = remote.second,
            state = procSocketState(currentProtocol, fields[3]),
            uid = uid,
            packageNames = emptyList(),
        )
    }
    return result.distinctBy { socket ->
        listOf(
            socket.protocol,
            socket.localAddress,
            socket.localPort,
            socket.remoteAddress,
            socket.remotePort,
            socket.uid,
        )
    }
}

private fun parseProcSocketAddress(value: String, ipv6: Boolean): Pair<String, Int>? {
    val addressHex = value.substringBefore(':')
    val port = value.substringAfter(':', "").toIntOrNull(16)?.takeIf { it in 0..65_535 } ?: return null
    val bytes = runCatching {
        if (ipv6) {
            require(addressHex.length == 32)
            addressHex.chunked(8).flatMap { word ->
                word.chunked(2).map { it.toInt(16).toByte() }.reversed()
            }.toByteArray()
        } else {
            require(addressHex.length == 8)
            addressHex.chunked(2).map { it.toInt(16).toByte() }.reversed().toByteArray()
        }
    }.getOrNull() ?: return null
    val address = runCatching { InetAddress.getByAddress(bytes).hostAddress }.getOrNull() ?: return null
    return (address.substringBefore('%')) to port
}

private fun procSocketState(protocol: String, code: String): String {
    if (protocol.startsWith("udp")) {
        return when (code.uppercase()) {
            "01" -> "CONNECTED"
            "07" -> "UNCONNECTED"
            else -> "UDP_$code"
        }
    }
    return when (code.uppercase()) {
        "01" -> "ESTABLISHED"
        "02" -> "SYN_SENT"
        "03" -> "SYN_RECV"
        "04" -> "FIN_WAIT1"
        "05" -> "FIN_WAIT2"
        "06" -> "TIME_WAIT"
        "07" -> "CLOSE"
        "08" -> "CLOSE_WAIT"
        "09" -> "LAST_ACK"
        "0A" -> "LISTEN"
        "0B" -> "CLOSING"
        else -> "TCP_$code"
    }
}

private const val ROOT_SOCKET_MARKER = "@@VIROUTEFS_SOCKET:"
private const val ROOT_SOCKET_LINES_PER_TABLE = 96
private const val ROOT_SOCKET_MAX_TOTAL = 384
private const val ROOT_SOCKET_SCAN_TIMEOUT_MILLIS = 20_000L

private val ROOT_SOCKET_SNAPSHOT_SCRIPT = """
    set -eu
    dump_table() {
      label="${'$'}1"; path="${'$'}2"
      printf '@@VIROUTEFS_SOCKET:%s\n' "${'$'}label"
      if [ -r "${'$'}path" ]; then
        awk 'NR<=${ROOT_SOCKET_LINES_PER_TABLE + 1} { print }' "${'$'}path"
      fi
    }
    dump_table tcp4 /proc/net/tcp
    dump_table tcp6 /proc/net/tcp6
    dump_table udp4 /proc/net/udp
    dump_table udp6 /proc/net/udp6
""".trimIndent()
