// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import java.net.URI
import java.util.Base64
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class WireGuardProfileImportResult(
    val optionsJson: String,
    val suggestedName: String,
    val warnings: List<String>,
)

/**
 * Converts a standard wg-quick client file into a sing-box 1.14 WireGuard
 * endpoint. Commands such as PostUp are deliberately ignored and never run.
 */
fun importWireGuardProfile(source: String): WireGuardProfileImportResult {
    val parsed = parseWireGuardIni(source)
    val interfaceValues = requireNotNull(parsed.interfaceValues) {
        "WireGuard-файл не содержит секцию [Interface]."
    }
    require(parsed.peers.isNotEmpty()) {
        "WireGuard-файл не содержит ни одной секции [Peer]."
    }

    val privateKey = interfaceValues.singleRequired("privatekey", "PrivateKey")
    requireWireGuardKey(privateKey, "PrivateKey")
    val addresses = interfaceValues.commaSeparated("address")
    require(addresses.isNotEmpty()) { "В [Interface] не заполнено поле Address." }
    require(addresses.all(::isValidCidr)) {
        "Address должен содержать корректные IPv4/IPv6-префиксы, например 10.0.0.2/32."
    }

    val warnings = mutableListOf<String>()
    val root = JSONObject()
        .put("type", "wireguard")
        .put("system", false)
        .put("address", JSONArray(addresses))
        .put("private_key", privateKey)

    interfaceValues.singleOptionalInt("mtu", "MTU", 576..65_535)?.let {
        root.put("mtu", it)
    }
    interfaceValues.singleOptionalInt("listenport", "ListenPort", 1..65_535)?.let {
        root.put("listen_port", it)
    }

    val ignoredInterfaceFields = interfaceValues.keys - SUPPORTED_INTERFACE_FIELDS
    if (interfaceValues.containsKey("dns")) {
        warnings += "DNS из WireGuard-файла не перенесён автоматически: выберите DNS-политику для профиля в ViRouteFS."
    }
    if (ignoredInterfaceFields.isNotEmpty()) {
        warnings += "Поля [Interface] ${ignoredInterfaceFields.sorted().joinToString()} не исполняются и были пропущены."
    }

    val peers = JSONArray()
    parsed.peers.forEachIndexed { index, values ->
        val number = index + 1
        val publicKey = values.singleRequired("publickey", "PublicKey в [Peer] #$number")
        requireWireGuardKey(publicKey, "PublicKey в [Peer] #$number")
        val allowedIps = values.commaSeparated("allowedips")
        require(allowedIps.isNotEmpty()) { "В [Peer] #$number не заполнено поле AllowedIPs." }
        require(allowedIps.all(::isValidCidr)) {
            "AllowedIPs в [Peer] #$number содержит некорректный IPv4/IPv6-префикс."
        }
        val (host, port) = parseWireGuardEndpoint(
            values.singleRequired("endpoint", "Endpoint в [Peer] #$number"),
        )
        val peer = JSONObject()
            .put("address", host)
            .put("port", port)
            .put("public_key", publicKey)
            .put("allowed_ips", JSONArray(allowedIps))

        values.singleOptional("presharedkey", "PresharedKey в [Peer] #$number")
            ?.let { key ->
                requireWireGuardKey(key, "PresharedKey в [Peer] #$number")
                peer.put("pre_shared_key", key)
            }
        values.singleOptionalInt(
            "persistentkeepalive",
            "PersistentKeepalive в [Peer] #$number",
            0..65_535,
        )?.let { peer.put("persistent_keepalive_interval", it) }

        val ignoredPeerFields = values.keys - SUPPORTED_PEER_FIELDS
        if (ignoredPeerFields.isNotEmpty()) {
            warnings += "Поля [Peer] #$number ${ignoredPeerFields.sorted().joinToString()} были пропущены."
        }
        peers.put(peer)
    }
    root.put("peers", peers)

    val firstPeer = peers.getJSONObject(0)
    val suggestedName = "WireGuard ${firstPeer.getString("address")}"
    return WireGuardProfileImportResult(
        optionsJson = root.toString(),
        suggestedName = suggestedName,
        warnings = warnings.distinct(),
    )
}

private data class ParsedWireGuardIni(
    val interfaceValues: Map<String, List<String>>?,
    val peers: List<Map<String, List<String>>>,
)

private fun parseWireGuardIni(source: String): ParsedWireGuardIni {
    var section: String? = null
    var interfaceValues: MutableMap<String, MutableList<String>>? = null
    val peers = mutableListOf<MutableMap<String, MutableList<String>>>()

    source.removePrefix("\uFEFF").lineSequence().forEachIndexed { index, rawLine ->
        val line = rawLine.trim()
        if (line.isBlank() || line.startsWith("#") || line.startsWith(";")) return@forEachIndexed
        if (line.startsWith("[") && line.endsWith("]")) {
            section = line.substring(1, line.length - 1).trim().lowercase(Locale.ROOT)
            when (section) {
                "interface" -> {
                    require(interfaceValues == null) {
                        "WireGuard-файл содержит несколько секций [Interface]."
                    }
                    interfaceValues = linkedMapOf()
                }
                "peer" -> peers += linkedMapOf()
                else -> error("Неизвестная секция WireGuard [$section] в строке ${index + 1}.")
            }
            return@forEachIndexed
        }
        val separator = line.indexOf('=')
        require(separator > 0) {
            "Строка ${index + 1} WireGuard-файла должна иметь вид Ключ = Значение."
        }
        val key = line.substring(0, separator).trim().lowercase(Locale.ROOT)
        val value = line.substring(separator + 1).trim()
        require(value.isNotBlank()) { "Поле $key в строке ${index + 1} не заполнено." }
        val target = when (section) {
            "interface" -> requireNotNull(interfaceValues)
            "peer" -> peers.lastOrNull() ?: error("Поле [Peer] найдено до секции [Peer].")
            else -> error("Поле $key найдено до секции [Interface] или [Peer].")
        }
        target.getOrPut(key) { mutableListOf() } += value
    }
    return ParsedWireGuardIni(interfaceValues, peers)
}

private fun Map<String, List<String>>.singleRequired(key: String, label: String): String =
    singleOptional(key, label) ?: error("$label не заполнен.")

private fun Map<String, List<String>>.singleOptional(key: String, label: String): String? {
    val values = get(key).orEmpty()
    require(values.size <= 1) { "$label указан несколько раз." }
    return values.singleOrNull()?.trim()?.takeIf(String::isNotBlank)
}

private fun Map<String, List<String>>.singleOptionalInt(
    key: String,
    label: String,
    range: IntRange,
): Int? = singleOptional(key, label)?.let { value ->
    val parsed = value.toIntOrNull()
    require(parsed != null && parsed in range) {
        "$label должен быть числом от ${range.first} до ${range.last}."
    }
    parsed
}

private fun Map<String, List<String>>.commaSeparated(key: String): List<String> =
    get(key).orEmpty()
        .flatMap { it.split(',') }
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()

private fun requireWireGuardKey(value: String, label: String) {
    val decoded = runCatching { Base64.getDecoder().decode(value) }.getOrNull()
    require(decoded?.size == WIREGUARD_KEY_BYTES) {
        "$label должен быть стандартным WireGuard-ключом Base64 длиной 32 байта."
    }
}

private fun parseWireGuardEndpoint(value: String): Pair<String, Int> {
    val uri = runCatching { URI("wg://$value") }
        .getOrElse { error("Endpoint «$value» не содержит корректный адрес и порт.") }
    val host = uri.host
        ?.trim()
        ?.removePrefix("[")
        ?.removeSuffix("]")
        ?.takeIf(String::isNotBlank)
        ?: error("Endpoint «$value» не содержит корректный адрес.")
    val port = uri.port.takeIf { it in 1..65_535 }
        ?: error("Endpoint «$value» не содержит корректный порт.")
    return host to port
}

private const val WIREGUARD_KEY_BYTES = 32
private val SUPPORTED_INTERFACE_FIELDS = setOf(
    "privatekey",
    "address",
    "mtu",
    "listenport",
    "dns",
)
private val SUPPORTED_PEER_FIELDS = setOf(
    "publickey",
    "presharedkey",
    "allowedips",
    "endpoint",
    "persistentkeepalive",
)
