// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SupportQrCodeTest {
    @Test
    fun generatedSupportQrContainsTheExactSberSupportLink() {
        val expected = "https://messenger.online.sberbank.ru/sl/PV0SJRfgsEARtx5Ka"
        val generated = generateSupportQrCode(expected, size = 384)

        assertEquals(
            expected,
            ProfileQrCode.decodeArgb(generated.width, generated.height, generated.argbPixels),
        )
    }

    @Test
    fun supportQrRejectsNonHttpsLinks() {
        assertFailsWith<IllegalArgumentException> {
            generateSupportQrCode("http://example.com/support")
        }
    }
}
