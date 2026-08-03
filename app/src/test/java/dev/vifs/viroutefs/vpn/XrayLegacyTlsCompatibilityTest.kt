// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vpn

import java.nio.file.Files
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XrayLegacyTlsCompatibilityTest {
    @Test
    fun nestedLegacyAllowInsecureIsReplacedWithPersistentPinFields() {
        val target = mutableListOf<XrayTlsPinTarget>()
        val result = normalizeLegacyXrayTlsConfig(legacyConfig()) {
            target += it
            "AA:BB:CC"
        }
        val tls = nestedTls(result.json)

        assertEquals(1, result.migratedPins)
        assertEquals(
            XrayTlsPinTarget("download.example", 8443, "front.example"),
            target.single(),
        )
        assertFalse(tls.has("allowInsecure"))
        assertEquals("AA:BB:CC", tls.getString("pinnedPeerCertSha256"))
        assertFalse(tls.has("verifyPeerCertByName"))
    }

    @Test
    fun existingPinOnlyDropsTheRemovedLegacyField() {
        val root = JSONObject(legacyConfig())
        nestedTls(root).put("pinnedPeerCertSha256", "11:22:33")
        val source = root.toString()
        var resolverCalled = false
        val result = normalizeLegacyXrayTlsConfig(source) {
            resolverCalled = true
            "unexpected"
        }

        assertFalse(resolverCalled)
        assertEquals(0, result.migratedPins)
        assertFalse(nestedTls(result.json).has("allowInsecure"))
        assertEquals("11:22:33", nestedTls(result.json).getString("pinnedPeerCertSha256"))
    }

    @Test
    fun falseLegacyFieldIsRemovedWithoutNetworkLookup() {
        val root = JSONObject(legacyConfig())
        nestedTls(root).put("allowInsecure", false)
        val source = root.toString()
        var resolverCalled = false
        val result = normalizeLegacyXrayTlsConfig(source) {
            resolverCalled = true
            "unexpected"
        }

        assertFalse(resolverCalled)
        assertFalse(nestedTls(result.json).has("allowInsecure"))
        assertTrue(result.migratedPins == 0)
    }

    @Test
    fun obsoleteBooleanVerifyByNameIsConvertedToCurrentStringSchema() {
        val root = JSONObject(legacyConfig())
        val tls = nestedTls(root)
        tls.remove("allowInsecure")
        tls.put("verifyPeerCertByName", true)

        val result = normalizeLegacyXrayTlsConfig(root.toString()) { "unexpected" }

        assertEquals("front.example", nestedTls(result.json).getString("verifyPeerCertByName"))
        assertTrue(tls.getBoolean("verifyPeerCertByName"))
    }

    @Test
    fun certificatePinStoreReusesTheFirstPersistedPin() {
        val directory = Files.createTempDirectory("xray-pin-store-test").toFile()
        try {
            val target = XrayTlsPinTarget("download.example", 443, "front.example")
            val store = XrayCertificatePinStore(directory.resolve("pins.json"))
            var resolverCalls = 0

            assertEquals("AA:BB:CC", store.getOrResolve(target) {
                resolverCalls += 1
                "AA:BB:CC"
            })
            assertEquals("AA:BB:CC", XrayCertificatePinStore(directory.resolve("pins.json"))
                .getOrResolve(target) {
                    resolverCalls += 1
                    "unexpected"
                })
            assertEquals(1, resolverCalls)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun corruptedCertificatePinStoreFailsClosed() {
        val directory = Files.createTempDirectory("xray-pin-store-corrupt-test").toFile()
        try {
            val file = directory.resolve("pins.json").apply { writeText("not-json") }
            val store = XrayCertificatePinStore(file)
            var resolverCalled = false

            val error = assertFailsWith<IllegalStateException> {
                store.getOrResolve(XrayTlsPinTarget("download.example", 443, "front.example")) {
                    resolverCalled = true
                    "AA:BB:CC"
                }
            }

            assertFalse(resolverCalled)
            assertTrue(error.message.orEmpty().contains("refusing to trust"))
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun nestedTls(json: String): JSONObject = nestedTls(JSONObject(json))

    private fun nestedTls(root: JSONObject): JSONObject = root
        .getJSONArray("outbounds")
        .getJSONObject(0)
        .getJSONObject("streamSettings")
        .getJSONObject("xhttpSettings")
        .getJSONObject("extra")
        .getJSONObject("downloadSettings")
        .getJSONObject("tlsSettings")

    private fun legacyConfig(): String = """
        {
          "outbounds": [{
            "protocol": "vless",
            "settings": {
              "vnext": [{"address": "upload.example", "port": 443}]
            },
            "streamSettings": {
              "network": "xhttp",
              "xhttpSettings": {
                "extra": {
                  "downloadSettings": {
                    "address": "download.example",
                    "port": 8443,
                    "tlsSettings": {
                      "serverName": "front.example",
                      "allowInsecure": true
                    }
                  }
                }
              }
            }
          }]
        }
    """.trimIndent()
}
