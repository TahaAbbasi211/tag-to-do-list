package com.tag.routine.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Pure-JVM structural guards for the widget manifest, ProGuard rules, provider info,
 * and RemoteViews layouts so release-only regressions (duplicate include IDs, unexported
 * provider, vector previewImage, missing keep rules) fail fast in unit tests.
 */
class WidgetConfigTest {

    private val appDir: File = run {
        listOf(File("src/main"), File("app/src/main"), File("../app/src/main"))
            .firstOrNull { it.isDirectory }
            ?.absoluteFile?.parentFile?.parentFile
            ?: error("could not locate app module from ${File(".").absolutePath}")
    }

    private fun read(relativePath: String): String =
        File(appDir, relativePath).readText(StandardCharsets.UTF_8)

    @Test
    fun manifestExportsTodayWidgetProvider() {
        val manifest = read("src/main/AndroidManifest.xml")
        val block = Regex("""<receiver[^>]*TodayWidgetProvider.*?</receiver>""", RegexOption.DOT_MATCHES_ALL)
            .find(manifest)?.value
            ?: error("TodayWidgetProvider receiver missing in AndroidManifest.xml")
        assertTrue("TodayWidgetProvider must be exported=true", block.contains("""android:exported="true""""))
        assertTrue("TodayWidgetProvider must reference @xml/widget_today_info", block.contains("""@xml/widget_today_info"""))
    }

    @Test
    fun proguardKeepsWidgetReceivers() {
        val rules = read("proguard-rules.pro")
        assertTrue(rules.contains("com.tag.routine.widget.TodayWidgetProvider"))
        assertTrue(rules.contains("com.tag.routine.widget.WidgetUpdateReceiver"))
    }

    @Test
    fun wideLayoutHasUniqueIdsAndOnlyRemoteViewTags() {
        val wide = read("src/main/res/layout/widget_today_wide.xml")
        assertFalse("wide layout must not use <include> with shared ids", wide.contains("<include"))
        val ids = Regex("""android:id="@\+id/([^"]+)"""").findAll(wide).map { it.groupValues[1] }.toList()
        val duplicates = ids.groupingBy { it }.eachCount().filter { it.value > 1 }
        assertEquals("duplicate ids in widget_today_wide.xml: $duplicates", emptyMap<String, Int>(), duplicates)
        for (idx in 1..3) {
            assertTrue("missing widget_item_icon_$idx", "widget_item_icon_$idx" in ids)
            assertTrue("missing widget_item_title_$idx", "widget_item_title_$idx" in ids)
            assertTrue("missing widget_item_time_$idx", "widget_item_time_$idx" in ids)
        }
        assertOnlyRemoteViewsSafe(wide, "widget_today_wide.xml")
    }

    @Test
    fun initialLayoutShowsNeutralLoadingWithoutFakeData() {
        val small = read("src/main/res/layout/widget_today_small.xml")
        assertTrue(small.contains("""android:text="@string/widget_title_today""""))
        assertTrue(small.contains("""android:text="@string/widget_loading""""))
        assertOnlyRemoteViewsSafe(small, "widget_today_small.xml")
    }

    @Test
    fun providerInfoUsesRasterPngPreviewAndDedicatedPreviewLayout() {
        val info = read("src/main/res/xml/widget_today_info.xml")
        assertTrue(info.contains("""android:previewImage="@drawable/widget_preview""""))
        assertTrue(info.contains("""android:previewLayout="@layout/widget_today_preview""""))
        assertTrue(
            "expected raster widget_preview.png in drawable-nodpi",
            File(appDir, "src/main/res/drawable-nodpi/widget_preview.png").isFile,
        )
        assertFalse(
            "vector widget_preview.xml must not shadow widget_preview.png",
            File(appDir, "src/main/res/drawable/widget_preview.xml").exists(),
        )
        val preview = read("src/main/res/layout/widget_today_preview.xml")
        assertOnlyRemoteViewsSafe(preview, "widget_today_preview.xml")
    }

    @Test
    fun rendererMapsBothLegacyKeysAndCurrentEmojis() {
        assertEquals(WidgetRenderer.iconRes("sunrise"), WidgetRenderer.iconRes("☀️"))
        assertEquals(WidgetRenderer.iconRes("water"), WidgetRenderer.iconRes("💧"))
        assertEquals(WidgetRenderer.iconRes("spark"), WidgetRenderer.iconRes("✨"))
        assertEquals(WidgetRenderer.iconRes("sleep"), WidgetRenderer.iconRes("😴"))
    }

    private fun assertOnlyRemoteViewsSafe(xml: String, fileName: String) {
        val forbidden = listOf("ConstraintLayout", "CardView", "<Space", "<View", "Material")
        for (token in forbidden) {
            assertFalse("$fileName contains forbidden RemoteViews token '$token'", xml.contains(token))
        }
        assertFalse("$fileName must not reference ?attr/", xml.contains("?attr/"))
    }
}
