// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vless.protocol

object VlessProtocol {
    const val VERSION: Byte = 0x00
    const val ADDONS_LENGTH_NONE: Byte = 0x00
    const val ADDRESS_TYPE_IPV4: Byte = 0x01
    const val ADDRESS_TYPE_DOMAIN: Byte = 0x02
    const val ADDRESS_TYPE_IPV6: Byte = 0x03
    const val UUID_LENGTH_BYTES: Int = 16
    const val MAX_DOMAIN_LENGTH_BYTES: Int = 255
}
