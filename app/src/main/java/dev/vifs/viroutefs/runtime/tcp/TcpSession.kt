// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.runtime.tcp

import java.util.UUID

@JvmInline
internal value class TcpSessionId(val value: String) {
    init {
        require(value.isNotBlank()) { "TCP session id must not be blank." }
    }

    companion object {
        fun random(): TcpSessionId = TcpSessionId(UUID.randomUUID().toString())
    }
}

internal enum class TcpSessionState {
    New,
    Connecting,
    Connected,
    Closing,
    Closed,
    Failed,
}

internal data class TcpSessionMetadata(
    val id: TcpSessionId,
    val sourceIp: String,
    val sourcePort: Int,
    val destinationIp: String,
    val destinationPort: Int,
    val createdAt: Long,
    val lastActivityAt: Long,
    val bytesIn: Long = 0L,
    val bytesOut: Long = 0L,
    val state: TcpSessionState = TcpSessionState.New,
)
