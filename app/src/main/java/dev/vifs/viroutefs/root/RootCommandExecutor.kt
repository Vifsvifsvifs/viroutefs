// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.root

import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

internal data class RootCommandResult(
    val suCommandVisible: Boolean,
    val completed: Boolean,
    val exitCode: Int?,
    val output: String,
)

/** Executes only scripts assembled inside ViRouteFS; no user text is accepted. */
internal class RootCommandExecutor {
    fun execute(script: String, timeoutMillis: Long): RootCommandResult {
        require(script.isNotBlank()) { "Root script is empty." }
        require(script.length <= ROOT_SCRIPT_LIMIT_CHARS) { "Root script is too large." }
        val process = try {
            ProcessBuilder("su", "-c", script)
                .redirectErrorStream(true)
                .start()
        } catch (_: IOException) {
            return RootCommandResult(
                suCommandVisible = false,
                completed = false,
                exitCode = null,
                output = "",
            )
        }
        val output = StringBuilder()
        val reader = Thread(
            { process.inputStream.use { it.drainBounded(output, ROOT_OUTPUT_LIMIT_BYTES) } },
            "ViRouteFS-RootCommandOutput",
        ).apply {
            isDaemon = true
            start()
        }
        val completed = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
        if (!completed) {
            process.destroy()
            if (!process.waitFor(1, TimeUnit.SECONDS)) process.destroyForcibly()
        }
        reader.join(1_000)
        return RootCommandResult(
            suCommandVisible = true,
            completed = completed,
            exitCode = if (completed) process.exitValue() else null,
            output = output.toString(),
        )
    }
}

internal fun shellQuote(value: String): String {
    require('\u0000' !in value && '\n' !in value && '\r' !in value) { "Unsafe shell value." }
    return "'${value.replace("'", "'\\''")}'"
}

private fun InputStream.drainBounded(output: StringBuilder, limitBytes: Int) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var stored = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) return
        if (stored < limitBytes) {
            val kept = minOf(read, limitBytes - stored)
            output.append(String(buffer, 0, kept, Charsets.UTF_8))
            stored += kept
        }
    }
}

private const val ROOT_SCRIPT_LIMIT_CHARS = 64 * 1024
private const val ROOT_OUTPUT_LIMIT_BYTES = 64 * 1024
