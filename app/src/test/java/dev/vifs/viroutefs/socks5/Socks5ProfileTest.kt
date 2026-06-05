// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.socks5

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Socks5ProfileTest {
    @Test
    fun validationRejectsInvalidPortAndHost() {
        val errors = validateSocks5Profile(Socks5ProfileConfig(name = "", host = "", port = 70_000))

        assertTrue(errors.any { it.contains("name") })
        assertTrue(errors.any { it.contains("host") })
        assertTrue(errors.any { it.contains("1..65535") })
    }

    @Test
    fun validationRejectsDuplicateNames() {
        val existing = listOf(Socks5ProfileConfig(name = "Office", host = "127.0.0.1", port = 1080))

        val errors = validateSocks5Profile(
            candidate = Socks5ProfileConfig(name = "office", host = "localhost", port = 1081),
            existingProfiles = existing,
        )

        assertTrue(errors.any { it.contains("unique") })
    }

    @Test
    fun maskedSummaryNeverContainsPassword() {
        val profile = Socks5ProfileConfig(
            name = "Local",
            host = "127.0.0.1",
            port = 1080,
            username = "user",
            password = "super-secret-value",
        )

        assertFalse(profile.maskedSummary().contains("super-secret-value"))
        assertTrue(profile.maskedSummary().contains("auth=provided"))
    }

    @Test
    fun sanitizerMasksPasswordLikeFields() {
        assertEquals("Failed password=***", "Failed password=super-secret-value".sanitizeSocks5Diagnostic())
    }
}
