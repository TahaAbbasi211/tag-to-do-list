package com.tag.routine.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Guards the translation files against the two failure modes that have actually
 * bitten this project:
 *
 *  1. a locale drifting out of sync with the English base (missing keys), and
 *  2. double-encoded (mojibake) text, which silently ships unreadable strings.
 *
 * The tests read the resource XML straight off disk, so they need no Android runtime.
 */
class LocaleResourcesTest {

    /**
     * The unit-test working directory differs between Gradle invocations and IDEs,
     * so locate the resource folder by walking up from the working directory.
     */
    private val resDir: File = run {
        val candidates = listOf(
            File("src/main/res"),
            File("app/src/main/res"),
            File("../app/src/main/res"),
        )
        candidates.firstOrNull { it.isDirectory }
            ?: error("could not locate src/main/res from ${File(".").absolutePath}")
    }

    private val locales = listOf("fa", "fr", "de", "tr")

    /** Extracts `name -> value` for every `<string>` element, without an XML parser. */
    private fun readStrings(file: File): Map<String, String> {
        assertTrue("missing resource file: ${file.path}", file.isFile)
        val text = file.readText(StandardCharsets.UTF_8)
        // Only match <string ...>NAME</string>; skip translatable="false" boilerplate
        // and any commented-out entries.
        val pattern = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        return pattern.findAll(text).associate { m ->
            m.groupValues[1] to m.groupValues[2]
        }
    }

    private fun baseStrings(): Map<String, String> =
        readStrings(File(resDir, "values/strings.xml"))

    @Test
    fun `resource directory is where the test expects it`() {
        assertTrue("res dir not found at ${resDir.absolutePath}", resDir.isDirectory)
    }

    @Test
    fun `every locale defines exactly the same keys as the base`() {
        val baseKeys = baseStrings().keys
        assertTrue("base strings look empty", baseKeys.size > 100)
        for (locale in locales) {
            val keys = readStrings(File(resDir, "values-$locale/strings.xml")).keys
            val missing = baseKeys - keys
            val extra = keys - baseKeys
            assertEquals("$locale is missing keys: ${missing.sorted()}", emptySet<String>(), missing)
            assertEquals("$locale has unknown extra keys: ${extra.sorted()}", emptySet<String>(), extra)
        }
    }

    @Test
    fun `no locale contains mojibake`() {
        for (locale in locales) {
            val strings = readStrings(File(resDir, "values-$locale/strings.xml"))
            for ((name, value) in strings) {
                assertTrue(
                    "$locale.$name looks double-encoded: '$value'",
                    !looksDoubleEncoded(value),
                )
            }
        }
    }

    @Test
    fun `persian contains no placeholder or latin-1 residue`() {
        val strings = readStrings(File(resDir, "values-fa/strings.xml"))
        for ((name, value) in strings) {
            // A repaired Persian string must never degrade into question marks.
            assertTrue("fa.$name contains '??': '$value'", !value.contains("??"))
            // Accented Latin-1 letters are residue from a bad cp1252 round-trip.
            val residue = value.any { it.code in 0x00A0..0x00FF }
            assertTrue("fa.$name has Latin-1 residue: '$value'", !residue)
        }
    }

    @Test
    fun `format specifiers match the base for every locale`() {
        val specifier = Regex("""%\d+\$[sd]""")
        val base = baseStrings()
        for (locale in locales) {
            val strings = readStrings(File(resDir, "values-$locale/strings.xml"))
            for ((name, value) in strings) {
                val expected = base[name]?.let { specifier.findAll(it).map { m -> m.value }.toSortedSet() }
                    ?: continue
                val actual = specifier.findAll(value).map { m -> m.value }.toSortedSet()
                assertEquals("$locale.$name specifiers differ", expected, actual)
            }
        }
    }

    @Test
    fun `no locale has an unescaped apostrophe that would break aapt`() {
        // Android requires \' inside string resources; a bare ' fails resource flattening.
        for (locale in locales) {
            val file = File(resDir, "values-$locale/strings.xml")
            val text = file.readText(StandardCharsets.UTF_8)
            val pattern = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            for (m in pattern.findAll(text)) {
                val value = m.groupValues[2]
                val unescaped = Regex("""(?<!\\)'""").findAll(value).count()
                assertEquals(
                    "$locale.${m.groupValues[1]} has an unescaped apostrophe: '$value'",
                    0,
                    unescaped,
                )
            }
        }
    }

    /**
     * Mojibake from a UTF-8-read-as-cp1252 round trip shows up as sequences like
     * "Ã©", "Ø§", or "â€" that never occur in genuine French, German, or Turkish text.
     */
    private fun looksDoubleEncoded(value: String): Boolean {
        val markers = listOf("Ã", "Â", "â€", "Ø", "Ù", "Û", "Ú")
        return markers.any { value.contains(it) }
    }
}
