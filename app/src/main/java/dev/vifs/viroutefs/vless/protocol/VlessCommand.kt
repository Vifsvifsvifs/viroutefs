// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vless.protocol

enum class VlessCommand(val wireValue: Byte) {
    Tcp(0x01),
}
