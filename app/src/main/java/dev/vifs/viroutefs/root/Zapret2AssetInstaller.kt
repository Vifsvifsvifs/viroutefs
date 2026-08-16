// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.root

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

internal data class Zapret2RuntimeFiles(
    val binary: File,
    val library: File,
    val antiDpi: File,
    val automatic: File,
)

internal class Zapret2AssetInstaller(context: Context) {
    private val appContext = context.applicationContext
    private val runtimeDirectory = File(appContext.noBackupFilesDir, ZAPRET2_RUNTIME_DIRECTORY)

    fun prepare(): Zapret2RuntimeFiles {
        val binary = File(appContext.applicationInfo.nativeLibraryDir, ZAPRET2_BINARY_NAME)
        require(binary.isFile) { "В APK отсутствует root-движок адаптации соединений." }
        require(binary.sha256() == ZAPRET2_BINARY_SHA256) { "Root-движок не прошёл проверку целостности." }
        require(runtimeDirectory.exists() || runtimeDirectory.mkdirs()) { "Не удалось подготовить каталог root-движка." }
        val library = installAsset("zapret-lib.lua", ZAPRET2_LIBRARY_SHA256)
        val antiDpi = installAsset("zapret-antidpi.lua", ZAPRET2_ANTIDPI_SHA256)
        val automatic = installAsset("zapret-auto.lua", ZAPRET2_AUTO_SHA256)
        return Zapret2RuntimeFiles(binary, library, antiDpi, automatic)
    }

    private fun installAsset(name: String, expectedSha256: String): File {
        require(name.matches(Regex("[a-z0-9.-]+"))) { "Invalid zapret2 asset name." }
        val target = File(runtimeDirectory, name)
        if (target.isFile && target.length() in 1..ZAPRET2_ASSET_MAX_BYTES && target.sha256() == expectedSha256) {
            return target
        }
        val bytes = appContext.assets.open("zapret2/$name").use { input ->
            val loaded = input.readBytes()
            require(loaded.size in 1..ZAPRET2_ASSET_MAX_BYTES) { "Некорректный размер $name." }
            loaded
        }
        val actual = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
        require(actual == expectedSha256) { "Ресурс $name не прошёл проверку целостности." }
        val temporary = File(runtimeDirectory, "$name.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        if (target.exists() && !target.delete()) error("Не удалось заменить $name.")
        if (!temporary.renameTo(target)) error("Не удалось сохранить $name.")
        return target
    }
}

private fun File.sha256(): String = inputStream().buffered().use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
    }
    digest.digest().toHex()
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private const val ZAPRET2_RUNTIME_DIRECTORY = "zapret2-v1.0.4"
private const val ZAPRET2_BINARY_NAME = "libzapret2.so"
private const val ZAPRET2_ASSET_MAX_BYTES = 256 * 1024
private const val ZAPRET2_BINARY_SHA256 = "2e1a0e950e0bc7189b5662e54fdd66d749d51215b167a647f15659554e7b4090"
private const val ZAPRET2_LIBRARY_SHA256 = "b272d207cca145a3b6174793b7d335489519f6d4299418ff2b870765cea24d5a"
private const val ZAPRET2_ANTIDPI_SHA256 = "31c9dd75b0bd55e98e5306293f2be81e9d2ecadcbbf9157394ff37dcff7dc85a"
private const val ZAPRET2_AUTO_SHA256 = "aacfde0c95c3058f8e95f5d7d244398bdc03ebf846a8f17322129fb543366a3d"
