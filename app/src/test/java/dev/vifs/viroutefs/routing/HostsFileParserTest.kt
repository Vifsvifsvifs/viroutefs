package dev.vifs.viroutefs.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HostsFileParserTest {
    @Test
    fun acceptsStandardAndReversedHostsLinesWithCommentsAndAliases() {
        val result = parseHostsFileText(
            """
            # local devices
            10.0.0.5 server.local server-alias.local
            printer.local 10.0.0.20 # reversed form
            ipv6.local = 2001:db8::5
            """.trimIndent(),
        )

        assertTrue(result.errors.isEmpty())
        assertEquals(
            listOf(
                ParsedHostsEntry("server.local", "10.0.0.5"),
                ParsedHostsEntry("server-alias.local", "10.0.0.5"),
                ParsedHostsEntry("printer.local", "10.0.0.20"),
                ParsedHostsEntry("ipv6.local", "2001:db8::5"),
            ),
            result.entries,
        )
    }

    @Test
    fun rejectsCidrAndMissingHostname() {
        val result = parseHostsFileText("10.0.0.0/24 network.local\n192.0.2.1")

        assertEquals(2, result.errors.size)
        assertTrue(result.entries.isEmpty())
    }
}
