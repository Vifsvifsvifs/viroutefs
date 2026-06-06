// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.runtime.tcp

internal interface TcpBridge {
    fun openSession(metadata: TcpSessionMetadata): TcpSessionId

    fun closeSession(id: TcpSessionId)

    fun send(id: TcpSessionId, data: ByteArray): Int

    fun receive(id: TcpSessionId, maxBytes: Int): ByteArray?
}

internal interface TcpBridgeFactory {
    fun createBridge(): TcpBridge
}
