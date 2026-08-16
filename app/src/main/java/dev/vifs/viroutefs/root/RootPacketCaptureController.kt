// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.root

import android.content.Context
import android.net.Uri
import android.os.Process
import java.io.File
import java.security.MessageDigest

enum class RootPacketCaptureMode(val displayName: String) {
    AllTraffic("Весь трафик"),
    WebAndDns("Только Web и DNS"),
}

data class RootPacketCaptureSnapshot(
    val running: Boolean,
    val captureAvailable: Boolean,
    val captureBytes: Long,
)

data class RootPacketCaptureResult(
    val successful: Boolean,
    val running: Boolean,
    val message: String,
)

class RootPacketCaptureController(context: Context) {
    private val appContext = context.applicationContext
    private val access = RootAccessController(appContext)
    private val executor = RootCommandExecutor()
    private val stateRepository = RootRuntimeStateRepository(appContext)
    private val recovery = RootNetworkRecoveryController(appContext)

    fun snapshot(): RootPacketCaptureSnapshot {
        val pidFile = stateRepository.packetCapturePidFile()
        val modulePresent = RootManagedModule.PacketCapture in stateRepository.load()?.modules.orEmpty()
        val hasPlausiblePid = pidFile.isFile && runCatching {
            pidFile.readText(Charsets.US_ASCII).trim().matches(Regex("[1-9][0-9]{0,11}"))
        }.getOrDefault(false)
        if (modulePresent && !hasPlausiblePid) {
            stateRepository.removeModule(RootManagedModule.PacketCapture)
        }
        val capture = stateRepository.packetCaptureFile()
        val available = isValidCapture(capture)
        return RootPacketCaptureSnapshot(
            running = modulePresent && hasPlausiblePid,
            captureAvailable = available,
            captureBytes = if (available) capture.length() else 0L,
        )
    }

    fun start(durationSeconds: Int, mode: RootPacketCaptureMode): RootPacketCaptureResult {
        require(durationSeconds in ALLOWED_CAPTURE_DURATIONS_SECONDS) { "Unsafe capture duration." }
        val probe = access.requestAndProbe()
        if (!probe.granted) {
            return RootPacketCaptureResult(false, false, probe.message)
        }
        val binary = runCatching { verifiedBinary() }.getOrElse { error ->
            return RootPacketCaptureResult(
                successful = false,
                running = false,
                message = error.localizedMessage ?: "Не удалось проверить локальный движок PCAP.",
            )
        }
        val pidFile = stateRepository.packetCapturePidFile()
        runCatching { pidFile.writeText("", Charsets.US_ASCII) }.getOrElse {
            return RootPacketCaptureResult(false, false, "Не удалось подготовить приватное состояние записи.")
        }
        stateRepository.markPending(RootManagedModule.PacketCapture, "bundled_tcpdump")
        val script = packetCaptureStartScript(
            binary = binary,
            pidFile = pidFile.absolutePath,
            logFile = stateRepository.packetCaptureLogFile().absolutePath,
            captureFile = stateRepository.packetCaptureFile().absolutePath,
            appUid = Process.myUid(),
            durationSeconds = durationSeconds,
            mode = mode,
        )
        val result = executor.execute(script, PACKET_CAPTURE_START_TIMEOUT_MILLIS)
        if (!result.completed || result.exitCode != 0) {
            val recovered = recovery.recoverPacketCapture()
            return RootPacketCaptureResult(
                successful = false,
                running = false,
                message = if (recovered.successful) {
                    "Запись PCAP не запустилась; её процесс и временное состояние автоматически удалены."
                } else {
                    "Запуск PCAP прерван, но откат не подтверждён. Используйте общую очистку в root-центре."
                },
            )
        }
        return RootPacketCaptureResult(
            successful = true,
            running = true,
            message = "Локальная запись запущена на $durationSeconds секунд. Она завершится сама; лимит — 25 000 пакетов и менее 4 МБ.",
        )
    }

    fun stop(): RootPacketCaptureResult {
        val result = recovery.recoverPacketCapture()
        val captureAvailable = isValidCapture(stateRepository.packetCaptureFile())
        return RootPacketCaptureResult(
            successful = result.successful,
            running = !result.successful,
            message = if (result.successful && captureAvailable) {
                "Запись остановлена. PCAP остался только в приватном каталоге приложения и готов к ручному экспорту."
            } else {
                result.message
            },
        )
    }

    fun exportCapture(destination: Uri): RootPacketCaptureResult {
        val capture = stateRepository.packetCaptureFile()
        if (!isValidCapture(capture)) {
            return RootPacketCaptureResult(false, false, "Корректный локальный PCAP не найден.")
        }
        return runCatching {
            val output = requireNotNull(appContext.contentResolver.openOutputStream(destination, "w")) {
                "Android не открыл выбранный файл для записи."
            }
            output.buffered().use { target -> capture.inputStream().buffered().use { it.copyTo(target) } }
            RootPacketCaptureResult(true, false, "PCAP экспортирован вручную в выбранное место.")
        }.getOrElse { error ->
            RootPacketCaptureResult(false, false, error.localizedMessage ?: "Не удалось экспортировать PCAP.")
        }
    }

    fun deleteCapture(): RootPacketCaptureResult {
        val current = snapshot()
        if (current.running) {
            return RootPacketCaptureResult(false, true, "Сначала остановите запись.")
        }
        val capture = stateRepository.packetCaptureFile()
        val removed = !capture.exists() || capture.delete()
        return RootPacketCaptureResult(
            successful = removed,
            running = false,
            message = if (removed) "Локальный PCAP удалён." else "Не удалось удалить локальный PCAP.",
        )
    }

    private fun verifiedBinary(): File {
        val binary = File(appContext.applicationInfo.nativeLibraryDir, PACKET_CAPTURE_BINARY_NAME)
        require(binary.isFile && binary.length() in 1..PACKET_CAPTURE_BINARY_MAX_BYTES) {
            "В APK отсутствует локальный движок PCAP."
        }
        require(binary.sha256() == PACKET_CAPTURE_BINARY_SHA256) {
            "Локальный движок PCAP не прошёл проверку целостности."
        }
        return binary
    }
}

internal fun packetCaptureStartScript(
    binary: File,
    pidFile: String,
    logFile: String,
    captureFile: String,
    appUid: Int,
    durationSeconds: Int,
    mode: RootPacketCaptureMode,
): String {
    require(appUid in 10_000..99_999_999) { "Unsafe application UID." }
    require(durationSeconds in ALLOWED_CAPTURE_DURATIONS_SECONDS) { "Unsafe capture duration." }
    val binaryArg = shellQuote(binary.absolutePath)
    val pid = shellQuote(pidFile)
    val log = shellQuote(logFile)
    val capture = shellQuote(captureFile)
    val filter = when (mode) {
        RootPacketCaptureMode.AllTraffic -> ""
        RootPacketCaptureMode.WebAndDns -> " " + shellQuote(
            "tcp port 80 or tcp port 443 or udp port 443 or port 53 or port 853",
        )
    }
    return """
        ${packetCaptureCleanupScript(pidFile, logFile, captureFile, appUid)}
        set -eu
        umask 077
        rm -f $capture $log
        : > $capture
        chown $appUid:$appUid $capture
        chmod 600 $capture
        $binaryArg -i any -p -n -s 128 -U -B 1024 -c $PACKET_CAPTURE_MAX_PACKETS -w $capture$filter >$log 2>&1 &
        capture_pid="${'$'}!"
        printf '%s\n' "${'$'}capture_pid" > $pid
        chown $appUid:$appUid $pid
        chmod 600 $pid
        sleep 1
        kill -0 "${'$'}capture_pid"
        capture_cmd="${'$'}(tr '\000' ' ' < "/proc/${'$'}capture_pid/cmdline" 2>/dev/null)"
        case "${'$'}capture_cmd" in
          *libtcpdump.so*) ;;
          *) kill "${'$'}capture_pid" 2>/dev/null || true; rm -f $pid; exit 1 ;;
        esac
        (
          sleep $durationSeconds
          if [ -r $pid ]; then
            current_pid="${'$'}(tr -cd '0-9' < $pid | head -c 12)"
            if [ "${'$'}current_pid" = "${'$'}capture_pid" ] && [ -r "/proc/${'$'}capture_pid/cmdline" ]; then
              current_cmd="${'$'}(tr '\000' ' ' < "/proc/${'$'}capture_pid/cmdline" 2>/dev/null)"
              case "${'$'}current_cmd" in *libtcpdump.so*) kill -2 "${'$'}capture_pid" 2>/dev/null || true ;; esac
            fi
            sleep 1
            chown $appUid:$appUid $capture 2>/dev/null || true
            chmod 600 $capture 2>/dev/null || true
            rm -f $pid
          fi
        ) >/dev/null 2>&1 &
        printf 'viroutefs_packet_capture=running\n'
    """.trimIndent()
}

internal fun isValidCapture(file: File): Boolean = runCatching {
    if (!file.isFile || file.length() !in 24..PACKET_CAPTURE_FILE_MAX_BYTES) return@runCatching false
    val magic = ByteArray(4)
    if (file.inputStream().use { it.read(magic) } != magic.size) return@runCatching false
    magic.contentEquals(byteArrayOf(0xd4.toByte(), 0xc3.toByte(), 0xb2.toByte(), 0xa1.toByte())) ||
        magic.contentEquals(byteArrayOf(0xa1.toByte(), 0xb2.toByte(), 0xc3.toByte(), 0xd4.toByte())) ||
        magic.contentEquals(byteArrayOf(0x4d, 0x3c, 0xb2.toByte(), 0xa1.toByte())) ||
        magic.contentEquals(byteArrayOf(0xa1.toByte(), 0xb2.toByte(), 0x3c, 0x4d)) ||
        magic.contentEquals(byteArrayOf(0x0a, 0x0d, 0x0d, 0x0a))
}.getOrDefault(false)

private fun File.sha256(): String = inputStream().buffered().use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
    }
    digest.digest().joinToString("") { "%02x".format(it) }
}

internal val ALLOWED_CAPTURE_DURATIONS_SECONDS = setOf(15, 30, 60)
private const val PACKET_CAPTURE_BINARY_NAME = "libtcpdump.so"
private const val PACKET_CAPTURE_BINARY_SHA256 = "adb46aa539d42efb6d07c1afc42edc39954fd59a46c09561411bb98bb176c4da"
private const val PACKET_CAPTURE_BINARY_MAX_BYTES = 4L * 1024L * 1024L
private const val PACKET_CAPTURE_FILE_MAX_BYTES = 4L * 1024L * 1024L
private const val PACKET_CAPTURE_MAX_PACKETS = 25_000
private const val PACKET_CAPTURE_START_TIMEOUT_MILLIS = 20_000L
