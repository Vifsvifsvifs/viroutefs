// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.runtime.tcp

internal data class TcpSessionStateStats(
    val counts: Map<TcpSessionState, Int>,
) {
    val activeSessions: Int = counts
        .filterKeys { it != TcpSessionState.Closed && it != TcpSessionState.Failed }
        .values
        .sum()

    fun count(state: TcpSessionState): Int = counts[state] ?: 0

    companion object {
        val Empty = TcpSessionStateStats(emptyMap())
    }
}

internal class TcpSessionManager {
    private val sessions = LinkedHashMap<TcpSessionId, TcpSessionMetadata>()

    @Synchronized
    fun createSession(
        sourceIp: String,
        sourcePort: Int,
        destinationIp: String,
        destinationPort: Int,
        now: Long = System.currentTimeMillis(),
        id: TcpSessionId = TcpSessionId.random(),
    ): TcpSessionMetadata {
        require(sourcePort in 1..MAX_PORT) { "Source port must be between 1 and $MAX_PORT." }
        require(destinationPort in 1..MAX_PORT) { "Destination port must be between 1 and $MAX_PORT." }
        val session = TcpSessionMetadata(
            id = id,
            sourceIp = sourceIp,
            sourcePort = sourcePort,
            destinationIp = destinationIp,
            destinationPort = destinationPort,
            createdAt = now,
            lastActivityAt = now,
        )
        sessions[id] = session
        return session
    }

    @Synchronized
    fun lookupSession(id: TcpSessionId): TcpSessionMetadata? = sessions[id]

    @Synchronized
    fun updateCounters(
        id: TcpSessionId,
        bytesInDelta: Long = 0L,
        bytesOutDelta: Long = 0L,
        now: Long = System.currentTimeMillis(),
    ): TcpSessionMetadata? {
        require(bytesInDelta >= 0L) { "bytesInDelta must not be negative." }
        require(bytesOutDelta >= 0L) { "bytesOutDelta must not be negative." }
        val current = sessions[id] ?: return null
        val updated = current.copy(
            bytesIn = current.bytesIn + bytesInDelta,
            bytesOut = current.bytesOut + bytesOutDelta,
            lastActivityAt = now,
        )
        sessions[id] = updated
        return updated
    }

    @Synchronized
    fun updateState(
        id: TcpSessionId,
        state: TcpSessionState,
        now: Long = System.currentTimeMillis(),
    ): TcpSessionMetadata? {
        val current = sessions[id] ?: return null
        val updated = current.copy(state = state, lastActivityAt = now)
        sessions[id] = updated
        return updated
    }

    @Synchronized
    fun closeSession(id: TcpSessionId, now: Long = System.currentTimeMillis()): TcpSessionMetadata? =
        updateState(id = id, state = TcpSessionState.Closed, now = now)

    @Synchronized
    fun cleanupIdleSessions(
        idleTimeoutMillis: Long,
        now: Long = System.currentTimeMillis(),
    ): List<TcpSessionMetadata> {
        require(idleTimeoutMillis >= 0L) { "idleTimeoutMillis must not be negative." }
        val cutoff = now - idleTimeoutMillis
        val idle = sessions.values.filter { it.lastActivityAt <= cutoff }
        idle.forEach { sessions.remove(it.id) }
        return idle
    }

    @Synchronized
    fun snapshot(): List<TcpSessionMetadata> = sessions.values.toList()

    @Synchronized
    fun stateStats(): TcpSessionStateStats = TcpSessionStateStats(
        sessions.values.groupingBy { it.state }.eachCount(),
    )

    companion object {
        private const val MAX_PORT = 65_535
    }
}
