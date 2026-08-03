// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vpn

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XudpBaseKeyTest {
    @Test
    fun generatedKeyUsesRawUrlBase64ForExactly32Bytes() {
        val key = generateXudpBaseKey()

        assertFalse('=' in key)
        assertEquals(32, Base64.getUrlDecoder().decode(key).size)
        assertTrue(isValidXudpBaseKey(key))
    }

    @Test
    fun legacyUuidAndWrongLengthBase64AreRejected() {
        assertFalse(isValidXudpBaseKey("12345678-1234-1234-1234-123456789abc"))
        assertFalse(isValidXudpBaseKey(Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(31))))
    }

    @Test
    fun paddedBase64IsRejectedBecauseXrayUsesRawUrlEncoding() {
        val padded = Base64.getUrlEncoder().encodeToString(ByteArray(32))

        assertFalse(isValidXudpBaseKey(padded))
    }
}
