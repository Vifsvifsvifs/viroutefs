// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vpn

fun minimalSmokeConfig(): String = """
    {
      "inbounds": [
        {
          "tag": "local-socks-smoke",
          "listen": "127.0.0.1",
          "port": 10808,
          "protocol": "socks",
          "settings": {
            "auth": "noauth",
            "udp": false
          }
        }
      ],
      "outbounds": [
        {
          "tag": "direct-smoke",
          "protocol": "freedom"
        }
      ]
    }
""".trimIndent()
