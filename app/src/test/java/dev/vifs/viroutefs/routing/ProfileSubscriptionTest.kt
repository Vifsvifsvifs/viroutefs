// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.Base64
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileSubscriptionTest {
    @Test
    fun base64UriSubscriptionUsesExistingMaskedImporter() {
        val source = listOf(
            "vless://11111111-2222-3333-4444-555555555555@one.example:443?security=tls#One",
            "trojan://secret-password@two.example:443?sni=two.example#Two",
        ).joinToString("\n")
        val encoded = Base64.getEncoder().encodeToString(source.toByteArray())

        val preview = previewProfileSubscriptionImport(encoded)

        assertEquals(2, preview.candidates.size)
        assertTrue(preview.candidates.all { !it.profile.enabled })
        assertFalse(preview.candidates.joinToString { it.maskedPreview }.contains("secret-password"))
    }

    @Test
    fun commonClashYamlProfilesAreConvertedWithoutExecutingYamlTypes() {
        val yaml = """
            proxies:
              - name: Office SS
                type: ss
                server: ss.example.com
                port: 443
                cipher: aes-128-gcm
                password: private-password
              - name: Office VLESS
                type: vless
                server: vless.example.com
                port: 443
                uuid: 11111111-2222-3333-4444-555555555555
                tls: true
                network: ws
                ws-opts:
                  path: /route
                  headers:
                    Host: edge.example.com
        """.trimIndent()

        val preview = previewProfileSubscriptionImport(yaml)

        assertEquals(listOf("Office SS", "Office VLESS"), preview.candidates.map { it.profile.name })
        assertEquals(setOf(TunnelType.Shadowsocks, TunnelType.VLESS), preview.candidates.map { it.profile.type }.toSet())
        assertTrue(preview.candidates.all { !it.profile.enabled })
        assertFalse(preview.candidates.joinToString { it.maskedPreview }.contains("private-password"))
    }

    @Test
    fun updateKeepsStableProfileIdRouteAndUserEnabledState() {
        val subscription = ProfileSubscription(
            id = "subscription-office",
            name = "Office",
            url = "https://subscription.example/list?token=private",
        )
        val firstImport = previewProfileSubscriptionImport(
            "vless://11111111-2222-3333-4444-555555555555@old.example:443?security=tls#Office",
        )
        val defaults = RoutingConfigDefaults.defaultConfig()
        val firstApplied = applyProfileSubscriptionUpdate(
            defaults,
            previewProfileSubscriptionUpdate(defaults, subscription, firstImport, 100L),
        )
        val firstProfile = firstApplied.profiles.first { it.sourceSubscriptionId == subscription.id }
        val route = RouteRule(
            id = "office-route",
            name = "Office route",
            type = RouteRuleType.DOMAIN,
            targetProfileId = firstProfile.id,
            priority = 100,
            matchers = listOf("domain:office.example"),
            reason = "test",
            technicalDetails = "test",
            recommendedAction = "test",
        )
        val customized = firstApplied.copy(
            profiles = firstApplied.profiles.map {
                if (it.id == firstProfile.id) {
                    it.copy(enabled = true, vless = it.vless?.copy(enabled = true))
                } else {
                    it
                }
            },
            rules = firstApplied.rules + route,
        )
        val secondImport = previewProfileSubscriptionImport(
            listOf(
                "vless://aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee@new.example:443?security=tls#Office",
                "trojan://new-secret@second.example:443#Second",
            ).joinToString("\n"),
        )

        val update = previewProfileSubscriptionUpdate(customized, subscription, secondImport, 200L)
        val applied = applyProfileSubscriptionUpdate(customized, update)
        val updatedOffice = applied.profiles.first {
            it.sourceSubscriptionId == subscription.id && it.name == "Office"
        }
        val addedSecond = applied.profiles.first {
            it.sourceSubscriptionId == subscription.id && it.name == "Second"
        }

        assertEquals(1, update.updatedCount)
        assertEquals(1, update.addedCount)
        assertEquals(firstProfile.id, updatedOffice.id)
        assertTrue(updatedOffice.enabled)
        assertEquals("new.example", updatedOffice.vless?.host)
        assertFalse(addedSecond.enabled)
        assertEquals(firstProfile.id, applied.rules.first { it.id == route.id }.targetProfileId)
    }

    @Test
    fun removedServerIsRetainedDisabledSoRoutesDoNotBreak() {
        val subscription = ProfileSubscription(
            id = "subscription-office",
            name = "Office",
            url = "https://subscription.example/list",
        )
        val defaults = RoutingConfigDefaults.defaultConfig()
        val initial = applyProfileSubscriptionUpdate(
            defaults,
            previewProfileSubscriptionUpdate(
                defaults,
                subscription,
                previewProfileSubscriptionImport(
                    "vless://11111111-2222-3333-4444-555555555555@one.example:443?security=tls#One",
                ),
                100L,
            ),
        )
        val oldProfile = initial.profiles.first { it.sourceSubscriptionId == subscription.id }
        val route = initial.rules.first { it.type == RouteRuleType.DEFAULT }.copy(
            targetProfileId = oldProfile.id,
        )
        val routed = initial.copy(
            profiles = initial.profiles.map {
                if (it.id == oldProfile.id) it.copy(enabled = true, vless = it.vless?.copy(enabled = true)) else it
            },
            rules = initial.rules.map { if (it.id == route.id) route else it },
            defaultProfileId = oldProfile.id,
        )
        val replacement = previewProfileSubscriptionImport(
            "trojan://secret@two.example:443#Two",
        )

        val preview = previewProfileSubscriptionUpdate(routed, subscription, replacement, 200L)
        val applied = applyProfileSubscriptionUpdate(routed, preview)
        val retained = applied.profiles.first { it.id == oldProfile.id }

        assertEquals(listOf(oldProfile.id), preview.removedProfiles.map { it.id })
        assertFalse(retained.enabled)
        assertEquals(subscription.id, retained.sourceSubscriptionId)
        assertEquals(oldProfile.id, applied.defaultProfileId)
        assertEquals(oldProfile.id, applied.rules.first { it.id == route.id }.targetProfileId)
        assertTrue(retained.platformNotes.orEmpty().contains("оставлен выключенным"))
    }

    @Test
    fun deletingSubscriptionDetachesDisabledProfilesAndPreservesRoutes() {
        val subscriptionId = "subscription-delete"
        val defaults = RoutingConfigDefaults.defaultConfig()
        val profile = defaults.profiles.first().copy(
            id = "managed",
            name = "Managed",
            enabled = true,
            sourceSubscriptionId = subscriptionId,
            sourceEntryKey = "entry:0",
        )
        val config = defaults.copy(
            profiles = defaults.profiles + profile,
            subscriptions = listOf(
                ProfileSubscription(subscriptionId, "Delete", "https://example.com/list"),
            ),
        )

        val deleted = config.withoutSubscription(subscriptionId)
        val detached = deleted.profiles.first { it.id == profile.id }

        assertTrue(deleted.subscriptions.isEmpty())
        assertFalse(detached.enabled)
        assertNull(detached.sourceSubscriptionId)
        assertNull(detached.sourceEntryKey)
    }

    @Test
    fun urlPolicyRejectsLocalAndAcceptsOnlyResolvedPublicHttps() {
        val publicResolver = SubscriptionHostResolver {
            arrayOf(InetAddress.getByName("93.184.216.34"))
        }
        val localResolver = SubscriptionHostResolver {
            arrayOf(InetAddress.getByName("127.0.0.1"))
        }

        assertEquals(
            "https://example.com/list?token=secret",
            validateSubscriptionUrl(
                "https://example.com/list?token=secret",
                publicResolver,
            ).toString(),
        )
        assertTrue(validateSubscriptionUrlSyntax("http://example.com/list").orEmpty().contains("HTTPS"))
        assertTrue(validateSubscriptionUrlSyntax("https://user:pass@example.com/list").orEmpty().contains("логин"))
        assertTrue(runCatching {
            validateSubscriptionUrl("https://example.com/list", localResolver)
        }.isFailure)
        assertEquals("https://example.com/…", maskSubscriptionUrl("https://example.com/private?token=secret"))
        assertTrue(validateSubscriptionUrlSyntax("https://example.com/${"x".repeat(8_500)}") != null)
        assertFalse(
            ProfileSubscription(
                "safe-log",
                "Safe log",
                "https://example.com/list?token=must-not-log",
            ).toString().contains("must-not-log"),
        )
    }

    @Test
    fun repositoryEncryptsFullSubscriptionUrlAndRestoresIt() = runTest {
        val tempDir = createTempDirectory("viroutefs-subscription-secret").toFile()
        try {
            val profileSecrets = InMemoryProfileSecretStore()
            val subscriptionSecrets = InMemorySubscriptionSecretStore()
            val repository = RoutingConfigRepository(
                filesDirectory = tempDir,
                secretStore = profileSecrets,
                subscriptionSecretStore = subscriptionSecrets,
            )
            val secretUrl = "https://subscription.example/list?token=never-write-plain"
            val config = RoutingConfigDefaults.defaultConfig().copy(
                subscriptions = listOf(
                    ProfileSubscription("office", "Office", secretUrl),
                ),
            )

            repository.save(config)

            val json = File(tempDir, RoutingConfigRepository.ROUTING_CONFIG_FILENAME).readText()
            val restored = repository.load().config
            assertFalse(json.contains(secretUrl))
            assertFalse(json.contains("never-write-plain"))
            assertTrue(json.contains(REDACTED_SECRET))
            assertEquals(secretUrl, subscriptionSecrets.load()["office"])
            assertEquals(secretUrl, restored.subscriptions.single().url)
            assertNotEquals(secretUrl, repository.exportJson(config))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun passwordProtectedBackupRestoresSubscriptionUrl() {
        val secretUrl = "https://subscription.example/list?token=backup-secret"
        val config = RoutingConfigDefaults.defaultConfig().copy(
            subscriptions = listOf(
                ProfileSubscription("backup", "Backup", secretUrl),
            ),
        )
        val password = "strong-backup-password".toCharArray()

        val encrypted = RoutingConfigBackup.encrypt(config, password)
        val restored = RoutingConfigBackup.decrypt(encrypted, password)

        assertFalse(encrypted.toString(Charsets.UTF_8).contains("backup-secret"))
        assertEquals(secretUrl, restored.subscriptions.single().url)
    }

    @Test
    fun clientRevalidatesHttpsRedirectAndReadsBoundedUtf8() {
        val requestedHosts = mutableListOf<String>()
        val resolver = SubscriptionHostResolver {
            arrayOf(InetAddress.getByName("93.184.216.34"))
        }
        val client = ProfileSubscriptionClient(
            resolver = resolver,
            connectionFactory = { url ->
                requestedHosts += url.host
                if (url.host == "first.example") {
                    FakeHttpConnection(
                        url = url,
                        status = HttpURLConnection.HTTP_MOVED_TEMP,
                        headers = mapOf("Location" to "https://second.example/list"),
                    )
                } else {
                    FakeHttpConnection(
                        url = url,
                        status = HttpURLConnection.HTTP_OK,
                        body = "vless://test".toByteArray(),
                    )
                }
            },
        )

        val result = client.fetch("https://first.example/list")

        assertEquals(listOf("first.example", "second.example"), requestedHosts)
        assertEquals("vless://test", result.body)
    }

    @Test
    fun clientRejectsOversizedDeclaredResponseBeforeReadingBody() {
        val client = ProfileSubscriptionClient(
            resolver = SubscriptionHostResolver {
                arrayOf(InetAddress.getByName("93.184.216.34"))
            },
            connectionFactory = { url ->
                FakeHttpConnection(
                    url = url,
                    status = HttpURLConnection.HTTP_OK,
                    body = "ignored".toByteArray(),
                    declaredLength = MAX_SUBSCRIPTION_BYTES.toLong() + 1L,
                )
            },
        )

        val failure = runCatching { client.fetch("https://example.com/list") }

        assertTrue(failure.isFailure)
        assertTrue(failure.exceptionOrNull()?.message.orEmpty().contains("2 МБ"))
    }

    private class FakeHttpConnection(
        url: URL,
        private val status: Int,
        private val body: ByteArray = ByteArray(0),
        private val headers: Map<String, String> = emptyMap(),
        private val declaredLength: Long = body.size.toLong(),
    ) : HttpURLConnection(url) {
        override fun connect() = Unit

        override fun disconnect() = Unit

        override fun usingProxy(): Boolean = false

        override fun getResponseCode(): Int = status

        override fun getHeaderField(name: String?): String? = headers[name]

        override fun getContentLengthLong(): Long = declaredLength

        override fun getInputStream(): InputStream = ByteArrayInputStream(body)
    }
}
