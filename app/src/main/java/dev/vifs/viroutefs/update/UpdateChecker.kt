// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

internal const val GITHUB_RELEASES_API_URL = "https://api.github.com/repos/Vifsvifsvifs/viroutefs/releases?per_page=10"
internal const val GITHUB_RELEASES_WEB_URL = "https://github.com/Vifsvifsvifs/viroutefs/releases"

internal data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val qualifier: String?,
) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch).let {
            if (it != 0) return it
        }
        return compareQualifier(qualifier, other.qualifier)
    }

    override fun toString(): String = buildString {
        append(major).append('.').append(minor).append('.').append(patch)
        qualifier?.let { append('-').append(it) }
    }
}

internal data class ReleaseInfo(
    val version: AppVersion,
    val versionCode: Int?,
    val displayVersion: String,
    val name: String,
    val publishedAt: String?,
    val notes: String?,
    val htmlUrl: String,
)

internal sealed interface UpdateCheckResult {
    data class NewerRelease(val release: ReleaseInfo) : UpdateCheckResult
    data class UpToDate(val latest: ReleaseInfo?) : UpdateCheckResult
    data object NoReleaseFound : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

internal class UpdateChecker(
    private val apiUrl: String = GITHUB_RELEASES_API_URL,
) {
    suspend fun check(currentVersionName: String, currentVersionCode: Int): UpdateCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val currentVersion = parseAppVersion(currentVersionName)
                ?: return@withContext UpdateCheckResult.Error("Current app version is not parseable: $currentVersionName")
            val releases = fetchReleases()
            val latest = releases.maxWithOrNull(compareBy<ReleaseInfo> { it.version }.thenBy { it.versionCode ?: -1 })
                ?: return@withContext UpdateCheckResult.NoReleaseFound

            if (isNewerRelease(latest, currentVersion, currentVersionCode)) {
                UpdateCheckResult.NewerRelease(latest)
            } else {
                UpdateCheckResult.UpToDate(latest)
            }
        }.getOrElse { error ->
            UpdateCheckResult.Error(error.message ?: error::class.java.simpleName)
        }
    }

    private fun fetchReleases(): List<ReleaseInfo> {
        val connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "ViRouteFS-manual-update-check")
        }

        return connection.useConnection {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IllegalStateException("GitHub Releases API returned HTTP $status")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseReleases(body)
        }
    }
}

internal fun parseReleases(json: String): List<ReleaseInfo> {
    val releases = JSONArray(json)
    return buildList {
        for (index in 0 until releases.length()) {
            val release = releases.getJSONObject(index)
            if (release.optBoolean("draft", false)) continue

            val tagName = release.optString("tag_name")
            val releaseName = release.optString("name")
            val versionText = listOf(tagName, releaseName).firstNotNullOfOrNull(::parseAppVersion) ?: continue
            val displayVersion = tagName.ifBlank { versionText.toString() }
            val body = release.optString("body")
            val publishedAt = release.optString("published_at").takeIf { it.isNotBlank() }?.let(::formatPublishedAt)
            val notes = body.takeIf { it.isNotBlank() }?.let(::shortReleaseNotes)
            val htmlUrl = release.optString("html_url").takeIf { it.isNotBlank() } ?: GITHUB_RELEASES_WEB_URL

            add(
                ReleaseInfo(
                    version = versionText,
                    versionCode = parseVersionCode(listOf(tagName, releaseName, body).joinToString("\n")),
                    displayVersion = displayVersion,
                    name = releaseName.ifBlank { displayVersion },
                    publishedAt = publishedAt,
                    notes = notes,
                    htmlUrl = htmlUrl,
                ),
            )
        }
    }
}

internal fun parseVersionCode(value: String): Int? {
    val match = Regex("""\bversionCode\s*[:=]?\s*(\d+)\b""", RegexOption.IGNORE_CASE).find(value) ?: return null
    return match.groupValues[1].toIntOrNull()
}

private fun isNewerRelease(release: ReleaseInfo, currentVersion: AppVersion, currentVersionCode: Int): Boolean = when {
    release.version > currentVersion -> true
    release.version < currentVersion -> false
    release.versionCode != null -> release.versionCode > currentVersionCode
    else -> false
}

internal fun parseAppVersion(value: String): AppVersion? {
    val match = Regex("""\bv?(\d+)\.(\d+)\.(\d+)(?:-([A-Za-z0-9][A-Za-z0-9._-]*))?\b""")
        .find(value.trim()) ?: return null
    return AppVersion(
        major = match.groupValues[1].toInt(),
        minor = match.groupValues[2].toInt(),
        patch = match.groupValues[3].toInt(),
        qualifier = match.groupValues.getOrNull(4)?.takeIf { it.isNotBlank() }?.lowercase(),
    )
}

private fun compareQualifier(left: String?, right: String?): Int = when {
    left == right -> 0
    left == null -> 1
    right == null -> -1
    else -> qualifierRank(left).compareTo(qualifierRank(right)).takeIf { it != 0 } ?: left.compareTo(right)
}

private fun qualifierRank(value: String): Int = when {
    value.startsWith("alpha") -> 0
    value.startsWith("beta") -> 1
    value.startsWith("rc") -> 2
    else -> 3
}

private fun shortReleaseNotes(value: String): String = value
    .lineSequence()
    .map { it.trim() }
    .filter { it.isNotBlank() }
    .joinToString("\n")
    .replace(Regex("[\\p{Cntrl}&&[^\n\t]]"), "")
    .take(500)

private fun formatPublishedAt(value: String): String = runCatching {
    Instant.parse(value).toString()
}.getOrDefault(value)

private inline fun <T> HttpURLConnection.useConnection(block: () -> T): T = try {
    block()
} finally {
    disconnect()
}
