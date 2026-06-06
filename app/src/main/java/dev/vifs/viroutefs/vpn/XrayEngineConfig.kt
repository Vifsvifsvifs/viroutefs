// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vpn

import dev.vifs.viroutefs.vless.VlessProfileConfig

private const val XRAY_SOCKS_LISTEN = "127.0.0.1"
private const val XRAY_SOCKS_PORT = 10808
private const val XRAY_VLESS_OUTBOUND_TAG = "vless-out"

internal fun buildXrayConfig(profile: VlessProfileConfig): String {
    val serverName = profile.sni?.trim().takeUnless { it.isNullOrBlank() } ?: profile.host.trim()
    val fingerprint = profile.fingerprint?.trim().takeUnless { it.isNullOrBlank() } ?: "chrome"
    val publicKey = requireNotNull(profile.publicKey?.trim().takeUnless { it.isNullOrBlank() }) {
        "VLESS REALITY public key is required."
    }
    val shortId = requireNotNull(profile.shortId?.trim().takeUnless { it.isNullOrBlank() }) {
        "VLESS REALITY short ID is required."
    }
    val flowBlock = profile.flow?.trim().takeUnless { it.isNullOrBlank() }?.let { flow ->
        ",\"flow\":${flow.jsonString()}"
    }.orEmpty()

    return """
        {
          "inbounds": [
            {
              "tag": "socks-in",
              "listen": ${XRAY_SOCKS_LISTEN.jsonString()},
              "port": $XRAY_SOCKS_PORT,
              "protocol": "socks",
              "settings": {
                "auth": "noauth",
                "udp": true
              }
            }
          ],
          "outbounds": [
            {
              "tag": "$XRAY_VLESS_OUTBOUND_TAG",
              "protocol": "vless",
              "settings": {
                "vnext": [
                  {
                    "address": ${profile.host.trim().jsonString()},
                    "port": ${profile.port},
                    "users": [
                      {
                        "id": ${profile.uuid.trim().jsonString()},
                        "encryption": "none"$flowBlock
                      }
                    ]
                  }
                ]
              },
              "streamSettings": {
                "network": "tcp",
                "security": "reality",
                "realitySettings": {
                  "serverName": ${serverName.jsonString()},
                  "fingerprint": ${fingerprint.jsonString()},
                  "publicKey": ${publicKey.jsonString()},
                  "shortId": ${shortId.jsonString()}
                }
              }
            }
          ],
          "routing": {
            "domainStrategy": "AsIs",
            "rules": [
              {
                "type": "field",
                "network": "tcp,udp",
                "outboundTag": "$XRAY_VLESS_OUTBOUND_TAG"
              }
            ]
          }
        }
    """.trimIndent()
}

private fun String.jsonString(): String = buildString(length + 2) {
    append('"')
    for (character in this@jsonString) {
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (character < ' ') {
                append("\\u")
                append(character.code.toString(16).padStart(4, '0'))
            } else {
                append(character)
            }
        }
    }
    append('"')
}
