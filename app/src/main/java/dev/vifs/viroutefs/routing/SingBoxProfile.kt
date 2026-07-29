// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import org.json.JSONObject

data class SingBoxProfileConfig(
    val kind: SingBoxProfileKind,
    val optionsJson: String,
)

enum class SingBoxProfileKind {
    Outbound,
    Endpoint,
}

data class SingBoxProtocolSchema(
    val kind: SingBoxProfileKind,
    val engineType: String,
    val beginnerHint: String,
)

fun singBoxProtocolSchema(type: TunnelType): SingBoxProtocolSchema? = when (type) {
    TunnelType.VMess -> outbound("vmess", "Вставьте объект outbound из конфигурации провайдера VMess.")
    TunnelType.Trojan -> outbound("trojan", "Нужны server, server_port, password и обычно tls.")
    TunnelType.Shadowsocks,
    TunnelType.Shadowsocks2022 -> outbound("shadowsocks", "Нужны server, server_port, method и password.")
    TunnelType.Hysteria -> outbound("hysteria", "Нужны server, server_port, скорость up/down, auth_str и tls.")
    TunnelType.Hysteria2 -> outbound("hysteria2", "Нужны server, server_port, password и tls.")
    TunnelType.Snell -> outbound("snell", "Нужны server, server_port, version и psk.")
    TunnelType.Tuic -> outbound("tuic", "Нужны server, server_port, uuid, password и tls.")
    TunnelType.AnyTls -> outbound("anytls", "Нужны server, server_port, password и tls.")
    TunnelType.ShadowTls -> outbound("shadowtls", "Вставьте проверенный ShadowTLS outbound; внутренний detour также должен существовать.")
    TunnelType.HttpProxy,
    TunnelType.HttpsProxy -> outbound("http", "Нужны server и server_port; для HTTPS включите tls.enabled.")
    TunnelType.SshTunnel -> outbound("ssh", "Нужны server, server_port, user и один способ аутентификации.")
    TunnelType.Tor -> outbound("tor", "Tor запускается локально; проверьте путь и дополнительные параметры, если они указаны.")
    TunnelType.WireGuard -> endpoint("wireguard", "Вставьте WireGuard endpoint: address, private_key и peers.")
    TunnelType.OpenVpn -> endpoint(
        "openvpn-client",
        "OpenVPN-клиент sing-box 1.14 alpha. Укажите server/server_port, network, логин/пароль и tls либо перенесите параметры из проверенного .ovpn.",
    )
    TunnelType.OpenConnectAnyConnect -> endpoint(
        "openconnect",
        "Корпоративный OpenConnect: Cisco AnyConnect, GlobalProtect, Fortinet, F5, Pulse или Juniper. Для первого профиля используйте логин/пароль либо cookie.",
    )
    TunnelType.TailscaleCompatible,
    TunnelType.HeadscaleCompatible -> endpoint(
        "tailscale",
        "Для Tailscale/Headscale нужны auth_key или ранее созданное состояние; для Headscale укажите control_url.",
    )
    else -> null
}

fun validateSingBoxProfile(type: TunnelType, config: SingBoxProfileConfig): List<String> = buildList {
    val schema = singBoxProtocolSchema(type)
    if (schema == null) {
        add("${type.label} не поддерживает расширенный профиль в текущей сборке.")
        return@buildList
    }
    if (config.kind != schema.kind) {
        add("Ожидается объект типа ${schema.kind.name.lowercase()}, а не ${config.kind.name.lowercase()}.")
    }
    val root = runCatching { JSONObject(config.optionsJson) }.getOrElse {
        add("JSON не читается: ${it.message.orEmpty()}")
        return@buildList
    }
    val actualType = root.optString("type")
    if (actualType != schema.engineType) {
        add("Поле type должно быть '${schema.engineType}', сейчас '${actualType.ifBlank { "не указано" }}'.")
    }
    if (root.has("inbounds") || root.has("outbounds") || root.has("route") || root.has("dns")) {
        add("Нужен один объект профиля, а не полная конфигурация sing-box.")
    }
    when (schema.engineType) {
        "vmess" -> requireFields(root, listOf("server", "server_port", "uuid"), this)
        "trojan", "anytls" -> requireFields(root, listOf("server", "server_port", "password"), this)
        "shadowsocks" -> requireFields(root, listOf("server", "server_port", "method", "password"), this)
        "hysteria" -> requireFields(root, listOf("server", "server_port"), this)
        "hysteria2" -> requireFields(root, listOf("server", "server_port"), this)
        "snell" -> requireFields(root, listOf("server", "server_port", "version", "psk"), this)
        "tuic" -> requireFields(root, listOf("server", "server_port", "uuid", "password"), this)
        "http" -> requireFields(root, listOf("server", "server_port"), this)
        "ssh" -> requireFields(root, listOf("server", "server_port", "user"), this)
        "wireguard" -> {
            requireFields(root, listOf("address", "private_key", "peers"), this)
            val addresses = root.optJSONArray("address")
            if (addresses == null || addresses.length() == 0) {
                add("WireGuard address не может быть пустым.")
            }
            val peers = root.optJSONArray("peers")
            if (peers == null || peers.length() == 0) {
                add("WireGuard peers не может быть пустым.")
            } else {
                repeat(peers.length()) { index ->
                    val peer = peers.optJSONObject(index)
                    if (peer == null) {
                        add("WireGuard peer #${index + 1} должен быть объектом.")
                    } else {
                        requireFields(
                            peer,
                            listOf("address", "port", "public_key", "allowed_ips"),
                            this,
                        )
                        if (peer.optInt("port", -1) !in 1..65_535) {
                            add("WireGuard peer #${index + 1} содержит некорректный port.")
                        }
                        if (peer.optJSONArray("allowed_ips")?.length() == 0) {
                            add("WireGuard peer #${index + 1} содержит пустой allowed_ips.")
                        }
                    }
                }
            }
        }
        "openvpn-client" -> {
            val hasSingleServer = root.optString("server").isNotBlank() && root.has("server_port")
            val hasServers = root.optJSONArray("servers")?.length()?.let { it > 0 } == true
            if (!hasSingleServer && !hasServers) {
                add("Укажите server и server_port либо непустой массив servers.")
            }
            if (root.optString("mode", "tls") == "tls") {
                val tls = root.optJSONObject("tls")
                if (tls == null) {
                    add("Для OpenVPN TLS нужен объект tls с CA-сертификатом или fingerprint сервера.")
                } else {
                    val hasTrust = tls.hasNonEmptyValue("certificate") ||
                        tls.optString("certificate_path").isNotBlank() ||
                        tls.hasNonEmptyValue("peer_fingerprint")
                    if (!hasTrust) {
                        add("Для OpenVPN выберите CA-сертификат или задайте проверенный peer_fingerprint.")
                    }
                    val hasClientCertificate = tls.hasNonEmptyValue("client_certificate") ||
                        tls.optString("client_certificate_path").isNotBlank()
                    val hasClientKey = tls.hasNonEmptyValue("client_key") ||
                        tls.optString("client_key_path").isNotBlank()
                    if (hasClientCertificate != hasClientKey) {
                        add("Клиентский сертификат OpenVPN и его закрытый ключ нужно указать вместе.")
                    }
                }
            }
        }
        "openconnect" -> requireFields(root, listOf("server"), this)
        "tailscale" -> {
            if (root.optString("auth_key").isBlank() && root.optString("state_directory").isBlank()) {
                add("Укажите auth_key либо state_directory существующего узла.")
            }
        }
    }
}

internal fun normalizedSingBoxProfileObject(
    type: TunnelType,
    config: SingBoxProfileConfig,
    tag: String,
): JSONObject {
    val errors = validateSingBoxProfile(type, config)
    require(errors.isEmpty()) { errors.joinToString(" ") }
    return JSONObject(config.optionsJson)
        .put("tag", tag)
        .apply {
            when (type) {
                TunnelType.WireGuard,
                TunnelType.OpenVpn,
                TunnelType.OpenConnectAnyConnect -> put("system", false)
                TunnelType.TailscaleCompatible,
                TunnelType.HeadscaleCompatible -> put("system_interface", false)
                else -> Unit
            }
        }
}

fun singBoxProfileTemplate(type: TunnelType): String = when (type) {
    TunnelType.VMess -> """{"type":"vmess","server":"vpn.example.com","server_port":443,"uuid":"00000000-0000-0000-0000-000000000001","security":"auto","tls":{"enabled":true,"server_name":"vpn.example.com"}}"""
    TunnelType.Trojan -> """{"type":"trojan","server":"vpn.example.com","server_port":443,"password":"replace-me","tls":{"enabled":true,"server_name":"vpn.example.com"}}"""
    TunnelType.Shadowsocks -> """{"type":"shadowsocks","server":"vpn.example.com","server_port":8388,"method":"aes-256-gcm","password":"replace-me"}"""
    TunnelType.Shadowsocks2022 -> """{"type":"shadowsocks","server":"vpn.example.com","server_port":8388,"method":"2022-blake3-aes-256-gcm","password":"replace-me"}"""
    TunnelType.Hysteria -> """{"type":"hysteria","server":"vpn.example.com","server_port":443,"up":"50 Mbps","down":"100 Mbps","auth_str":"replace-me","tls":{"enabled":true,"server_name":"vpn.example.com"}}"""
    TunnelType.Hysteria2 -> """{"type":"hysteria2","server":"vpn.example.com","server_port":443,"password":"replace-me","tls":{"enabled":true,"server_name":"vpn.example.com"}}"""
    TunnelType.Snell -> """{"type":"snell","server":"vpn.example.com","server_port":443,"version":4,"psk":"replace-me"}"""
    TunnelType.Tuic -> """{"type":"tuic","server":"vpn.example.com","server_port":443,"uuid":"00000000-0000-0000-0000-000000000001","password":"replace-me","congestion_control":"bbr","tls":{"enabled":true,"server_name":"vpn.example.com"}}"""
    TunnelType.AnyTls -> """{"type":"anytls","server":"vpn.example.com","server_port":443,"password":"replace-me","tls":{"enabled":true,"server_name":"vpn.example.com"}}"""
    TunnelType.ShadowTls -> """{"type":"shadowtls","server":"vpn.example.com","server_port":443,"version":3,"password":"replace-me","tls":{"enabled":true,"server_name":"vpn.example.com"}}"""
    TunnelType.HttpProxy -> """{"type":"http","server":"proxy.example.com","server_port":8080}"""
    TunnelType.HttpsProxy -> """{"type":"http","server":"proxy.example.com","server_port":443,"tls":{"enabled":true,"server_name":"proxy.example.com"}}"""
    TunnelType.SshTunnel -> """{"type":"ssh","server":"ssh.example.com","server_port":22,"user":"replace-me","password":"replace-me"}"""
    TunnelType.Tor -> """{"type":"tor"}"""
    TunnelType.WireGuard -> """{"type":"wireguard","address":["10.0.0.2/32"],"private_key":"replace-me","peers":[{"address":"vpn.example.com","port":51820,"public_key":"replace-me","allowed_ips":["0.0.0.0/0","::/0"]}]}"""
    TunnelType.OpenVpn -> """{"type":"openvpn-client","mode":"tls","server":"vpn.example.com","server_port":1194,"network":"udp","tls":{"server_name":"vpn.example.com"},"data_ciphers":["AES-256-GCM","AES-128-GCM"],"auth":"SHA256"}"""
    TunnelType.OpenConnectAnyConnect -> """{"type":"openconnect","server":"vpn.example.com","flavor":"anyconnect","username":"replace-me","password":"replace-me","tls":{"server_name":"vpn.example.com"}}"""
    TunnelType.TailscaleCompatible -> """{"type":"tailscale","state_directory":"tailscale","auth_key":"replace-me","hostname":"viroutefs","accept_routes":true}"""
    TunnelType.HeadscaleCompatible -> """{"type":"tailscale","state_directory":"headscale","auth_key":"replace-me","control_url":"https://headscale.example.com","hostname":"viroutefs","accept_routes":true}"""
    else -> "{}"
}

private fun outbound(type: String, hint: String) =
    SingBoxProtocolSchema(SingBoxProfileKind.Outbound, type, hint)

private fun endpoint(type: String, hint: String) =
    SingBoxProtocolSchema(SingBoxProfileKind.Endpoint, type, hint)

private fun requireFields(
    root: JSONObject,
    fields: List<String>,
    errors: MutableList<String>,
) {
    fields.forEach { field ->
        if (!root.has(field) || root.isNull(field) || root.optString(field).isBlank() && !root.hasArrayOrObject(field)) {
            errors += "Обязательное поле '$field' не заполнено."
        }
    }
}

private fun JSONObject.hasArrayOrObject(name: String): Boolean =
    optJSONArray(name) != null || optJSONObject(name) != null

private fun JSONObject.hasNonEmptyValue(name: String): Boolean =
    when (val value = opt(name)) {
        is org.json.JSONArray -> value.length() > 0
        is String -> value.isNotBlank()
        else -> value != null && value != JSONObject.NULL
    }
