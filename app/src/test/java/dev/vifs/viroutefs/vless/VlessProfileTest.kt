// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vless

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VlessProfileTest {
    @Test
    fun validVlessConfigAccepted() {
        assertTrue(validateVlessProfile(validProfile()).isEmpty())
    }

    @Test
    fun invalidUuidRejected() {
        val errors = validateVlessProfile(validProfile(uuid = "not-a-uuid"))

        assertTrue(errors.any { it.contains("UUID") })
    }

    @Test
    fun invalidPortRejected() {
        val errors = validateVlessProfile(validProfile(port = 0))

        assertTrue(errors.any { it.contains("1..65535") })
    }

    @Test
    fun safeSummaryDoesNotExposeUuid() {
        val uuid = "123e4567-e89b-12d3-a456-426614174000"
        val summary = validProfile(uuid = uuid).safeSummary()

        assertFalse(summary.contains(uuid))
        assertFalse(summary.contains("123e4567"))
        assertTrue(summary.contains("Lab VLESS @ example.com:443"))
    }

    private fun validProfile(
        port: Int = 443,
        uuid: String = "123e4567-e89b-12d3-a456-426614174000",
    ): VlessProfileConfig = VlessProfileConfig(
        name = "Lab VLESS",
        host = "example.com",
        port = port,
        uuid = uuid,
        securityMode = VlessSecurityMode.TLS,
    )
}
