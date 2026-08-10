// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenVpnPkcs12Test {
    @Test
    fun passwordProtectedIdentityIsConvertedToOpenVpnPemMaterial() {
        val material = importOpenVpnPkcs12(
            bytes = Base64.getDecoder().decode(TEST_PKCS12_BASE64),
            password = TEST_PASSWORD.toCharArray(),
        )

        assertEquals("client", material.alias)
        assertEquals(1, material.certificateCount)
        assertTrue(material.leafSubject.contains("CN=ViRouteFS Test Client"))
        assertTrue(material.clientCertificatePem.contains("-----BEGIN CERTIFICATE-----"))
        assertTrue(material.clientKeyPem.contains("-----BEGIN PRIVATE KEY-----"))
        assertNull(material.certificateAuthorityPem)
        assertTrue(material.warnings.isEmpty())
    }

    @Test
    fun wrongPasswordIsRejectedWithoutReturningAnyKeyMaterial() {
        val error = assertFailsWith<IllegalArgumentException> {
            importOpenVpnPkcs12(
                bytes = Base64.getDecoder().decode(TEST_PKCS12_BASE64),
                password = "wrong-password".toCharArray(),
            )
        }

        assertTrue(error.message.orEmpty().contains("парол", ignoreCase = true))
    }
}

private const val TEST_PASSWORD = "correct-horse"

private const val TEST_PKCS12_BASE64 =
    "MIIKRAIBAzCCCe4GCSqGSIb3DQEHAaCCCd8EggnbMIIJ1zCCBa4GCSqGSIb3DQEHAaCCBZ8EggWbMIIFlzCCBZMGCyqGSIb3DQEMCgECoIIFQDCCBTwwZgYJKoZIhvcNAQUNMFkwOAYJKoZIhvcNAQUMMCsEFFYSZOQFFxWouVC1BbMZiFR+xveKAgInEAIBIDAMBggqhkiG9w0CCQUAMB0GCWCGSAFlAwQBKgQQSiniFP2IVkeOGlqeMzXpsgSCBNCbgqVkraLRiNqYwdKj/PAkvyc/q6Btxwu7mU6WBOEP4bz2v/ULcoiBi0D+LxwT4WveusUARY3ZO4SbBK6impFlWEFxESuvFB1k4PJtdH/vgY7HaOFU3m+tB0SQdXPG8COg3IB/bX9M78pKk7sC6H9FkLT0As33v+BDZMkxcgIj+qSfXYWAIp1TIlyW1ul4rkW1x5NM3f0XK4Nxwm4kH7OrdgEWm6wv32A6theM83iGRnzBYPwOslipX3NXubQfgKnkHZT/rMt/rumnvIk1f8oKx9RX3looZD/izFZg2stXlp/ygM7Uoaw7QdWowX1hB9IPSUYdn4s3wQVWRRNlByCpvP922ldmWUaLCGVJvMSCGy9WS3VumciLI3IujoxZYv2Q4TPkEnVmViMoTnNzO0yeWLA0WK1iyybGlRHx4JkNfP64NuCdSEY4u7hkOU36CUf1PvKZ7he1jKgng88069A7lXYKYbbgQHC+rPd8XXGW/p7+u47f7zzVg0fBrjuegILZJeyGGkuO6Kncl1D6+qIs1gavuLdQACejBj3vcyKUGe/RYRVGCZcJYC4wjsDAv1HzPUsKC47QZRFSWaj/nqTslpsdUbPVlTrwhGVy5PDBiWCZwcuTooW9LaH/qUYbjp3iZJdJnTTkN4wOz1CJfohBomJu9Tx87Q4Mx4I/Nhc5zPSiSUFFmSQcOnL61yZusGoWJbPL46XzpNjbZpss1J3m227nBwDmyuKQ1wSX0dXwM/P07olqB/IwbEwDluu6efSRdrIU2vbgN2CNJHnT1vDcybJUr1qKKVviVzvqiT90553BIiOTEXuAwQPPzKm7jnBNCADQeTxctnpeTjYb+8lGnNqgb+rEiFKwGyNybE2abu0rYG2mvLCfLGcR4Pw1lSh7lZ116hAgRVxhkHPg3407u0egS/CW47V+xcM46Q8alyMLWR3aXE1tfb/8OVm1ufE7VVjytbFsH+3XZCi0H4CRVbCFtBV89H9/RjqvPmpPjzMluxSESDADcdK2kNNwBN9+vcGSo7qwP547H6SzoZ6bQLefmjQwc1L1CdGhwsVYQoxE3C5187f2T0QtYqSyOw8XmLX5MQC/O+ljNHFWoIxnd7lRFZBnfZAC2IvzDx0/nR7d3kb7eg7GhYTBSmxTRLbOiw7eR45GxefNAIFnzyvkNrCiYYyGApKjy9+WAvWqB54RooqJWDrB2gPl0QpE6SifpC3iA8D9mpoBH+Z+7+uRIvSH8y7m9Eefou03Q1vVTY36QzEO7Pk+YahyAWgzMeA+fE5NgTmBkVcVbDNlhOv09a3lP0izBPJFrJYFIP+mk8ECHMH730exvSGpJ8vNvbeXFtii3UQLTmuDCtMdKuxmq+gNoyDYBsPi5HlAfb8iko1IUGQkBu+9YQxoiIyfa9S2kse5JLmpL3tkUcUmMPsVOK+mI57gKZi1V4AP65j6/74t2r3khpCH7Dn9y+Y/JCfjL3/zH3iKI3WvmBDXihgc3+k9U77n6ajU1gBW7xOdtN1u3FGDoFRkOtt/BxyhlrLfvqaxxHTlaUgHEM20GBFJwnNIuzx1sLcSw2euMZGnDHAS1SNywIhEnhxHb6qEbcUzf3gvO43cp+IJId8wUJi0yUsTRlWqSEc53PyDP2pNSTFAMBsGCSqGSIb3DQEJFDEOHgwAYwBsAGkAZQBuAHQwIQYJKoZIhvcNAQkVMRQEElRpbWUgMTc4NjMyMTIxNDkzMzCCBCEGCSqGSIb3DQEHBqCCBBIwggQOAgEAMIIEBwYJKoZIhvcNAQcBMGYGCSqGSIb3DQEFDTBZMDgGCSqGSIb3DQEFDDArBBSppSlZur3McvMQXJgtgECQG+0EhgICJxACASAwDAYIKoZIhvcNAgkFADAdBglghkgBZQMEASoEEOSsbb8qGT/uNMrEeY/XL+aAggOQ8EbtHvtVBkPFUvm06FMy8VqdPSljeyxzWtFgNmWXQuR7H+5qE5yayrJZ3fssd639RvtS5PDqbfHXkgh5q0dgoNXoXuzJM4t5Ql5HAt/QwhlLK00M7Kq3Fs16Pks4NKfoU+N+6r0jVTlwSyzPSmjoTa60J+lQ5oxtSQYWNyWdf9CLtfIPJfUh7TqFCHAE1DuB9cKzcUTSWCf2YA7J8xMRT9xQOmykc/XYBH/kVrBieHsCorFWyMeU1/2Dsq06oEaOmS7ThCpY7bxvOjxgdkq4zF+95BOQfIbBD3Lil3yo0yFFOXbxqvWHCh57+b4qZxIDyNkN84L2uoH7nn6hWvXpF059h4Sdmzi7LVKZ6x9mTDVxbe8EkxJFIyu9pPYNatOd9bfWu/89D68gk9UPm0GvS2f024bKhE9O9hNSCF7K301FKk6m6uMXTZ6kDG9HIngEsGXOg855oBORkahw2s2CUeFm+hgJYCC3TtmwrpyrKXFSHzYvytpiE5rKfOASRhVVDI3eaUMvi2oLQRdLzlMpcthrqM8SiCGUY+lYClMlW6OU5yCJXNNdq63jfq9z5NttOewpO1tLkcDvowG7NQQb2u+wFdlAbEsZbZAr7tNSBgpvXlNzA9x1gB14drKisy6pylJyTh4zH1epMX6oX8WYBoFtdjzoMPjtVGbwEJwb3IP0m8ZINCFpDP9RB8wRCCVVHEX7xMHrXImBQ9bHx5EVmM/gEy6bMFJNsdtGNnoqXVgZbhtxtg4D2WJkL3SIg0KuUNGorBGmxLZVyRh5Z7daEX52dT7rSmQmczPgHQ1DWKbvvUI2u6nsu+7ZOQTMa1giz/D+YtooZYE2mV5XtmHkD5qN9cTY7ow888YbT8ZIXS1rRDKjI0rKTY7NAOW9OzqhlznCTEWPqBXxGMufFusddqWVHAHSN0CL4r/QywScQJkTMcNljGkP2lWafFxZRKnMMbWklNEvsgtoJkXGfY2s1xXvAwSj3scR2kIo8OYOIQe7Lm7yeAdaI1BosH/eH3lrqvZOpJ53UBLPd2JHPEtlfLNW49fu/8U0JvAnFupS61f6hlZMn0nGsZ4plkdDKujPAq78xOIWJMwEeeYaqHKbIxkTHQxG+Ob/u3LuPV7dKGKuWLLyP6jszoJB94ChjfKnrepvjRKHloZ/I2iSk8bOTPdptXtvB03rQuzAtr+lEwKXoIcZPSLCYwZRR5gOA6R9ME0wMTANBglghkgBZQMEAgEFAAQgXbWLxGCLenIbxaEgYDlrK1Eyty5tOe90bNJg0HDnPVoEFJszNp/ncGLdLfCM6JH+xtd6WFSQAgInEA=="
