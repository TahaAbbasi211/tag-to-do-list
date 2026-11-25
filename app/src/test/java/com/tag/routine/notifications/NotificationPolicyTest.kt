package com.tag.routine.notifications

import com.tag.routine.data.AppConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Unit tests for notification permission policy, exact-alarm fallback policy, manifest
 * compliance, and digest slider snapping/clamping bounds.
 */
class NotificationPolicyTest {

    private val appDir: File = run {
        listOf(File("src/main"), File("app/src/main"), File("../app/src/main"))
            .firstOrNull { it.isDirectory }
            ?.absoluteFile?.parentFile?.parentFile
            ?: error("could not locate app module from ${File(".").absolutePath}")
    }

    @Test
    fun `pre-33 posts when notifications are enabled even without runtime permission`() {
        assertTrue(
            NotificationPolicy.shouldPost(
                sdkInt = 32,
                permissionGranted = false,
                notificationsEnabled = true,
            ),
        )
    }

    @Test
    fun `pre-33 respects the system notifications switch`() {
        assertFalse(
            NotificationPolicy.shouldPost(
                sdkInt = 32,
                permissionGranted = true,
                notificationsEnabled = false,
            ),
        )
    }

    @Test
    fun `33 requires the runtime permission`() {
        assertFalse(
            NotificationPolicy.shouldPost(
                sdkInt = 33,
                permissionGranted = false,
                notificationsEnabled = true,
            ),
        )
    }

    @Test
    fun `33 posts when permission is granted and notifications are enabled`() {
        assertTrue(
            NotificationPolicy.shouldPost(
                sdkInt = 33,
                permissionGranted = true,
                notificationsEnabled = true,
            ),
        )
    }

    @Test
    fun `34 blocks when notifications are disabled even with permission`() {
        assertFalse(
            NotificationPolicy.shouldPost(
                sdkInt = 34,
                permissionGranted = true,
                notificationsEnabled = false,
            ),
        )
    }

    @Test
    fun `notification action cancellation requires mark-done or snooze with non-blank routineId`() {
        val routineId = "routine-abc"
        org.junit.Assert.assertEquals(
            routineId.hashCode(),
            NotificationActionPolicy.notificationIdFor(routineId),
        )
        assertTrue(
            NotificationActionPolicy.shouldCancelNotification(
                NotificationReceiver.ACTION_MARK_DONE,
                routineId,
            ),
        )
        assertTrue(
            NotificationActionPolicy.shouldCancelNotification(
                NotificationReceiver.ACTION_SNOOZE,
                routineId,
            ),
        )
        org.junit.Assert.assertEquals(
            routineId.hashCode(),
            NotificationActionPolicy.notificationIdToCancel(
                NotificationReceiver.ACTION_MARK_DONE,
                routineId,
            ),
        )
        org.junit.Assert.assertEquals(
            routineId.hashCode(),
            NotificationActionPolicy.notificationIdToCancel(
                NotificationReceiver.ACTION_SNOOZE,
                routineId,
            ),
        )

        assertFalse(
            NotificationActionPolicy.shouldCancelNotification(
                NotificationReceiver.ACTION_MARK_DONE,
                null,
            ),
        )
        assertFalse(
            NotificationActionPolicy.shouldCancelNotification(
                NotificationReceiver.ACTION_SNOOZE,
                "   ",
            ),
        )
        assertFalse(
            NotificationActionPolicy.shouldCancelNotification(
                "com.tag.routine.OTHER",
                routineId,
            ),
        )
        org.junit.Assert.assertNull(
            NotificationActionPolicy.notificationIdToCancel(null, routineId),
        )
    }

    @Test
    fun `runtime notification permission requests only on contextual trigger on API 33+`() {
        assertFalse(
            NotificationPolicy.shouldRequestRuntimePermission(
                sdkInt = 32,
                permissionGranted = false,
                contextualTrigger = true,
            ),
        )
        assertFalse(
            NotificationPolicy.shouldRequestRuntimePermission(
                sdkInt = 33,
                permissionGranted = false,
                contextualTrigger = false,
            ),
        )
        assertFalse(
            NotificationPolicy.shouldRequestRuntimePermission(
                sdkInt = 33,
                permissionGranted = true,
                contextualTrigger = true,
            ),
        )
        assertTrue(
            NotificationPolicy.shouldRequestRuntimePermission(
                sdkInt = 33,
                permissionGranted = false,
                contextualTrigger = true,
            ),
        )
    }

    @Test
    fun `routine save requests permission only when a timed reminder is newly enabled`() {
        assertTrue(
            NotificationPolicy.shouldRequestOnRoutineSave(
                sdkInt = 33,
                permissionGranted = false,
                previousReminderEnabled = false,
                newReminderEnabled = true,
                hasReminderTime = true,
            ),
        )
        assertFalse(
            NotificationPolicy.shouldRequestOnRoutineSave(
                sdkInt = 33,
                permissionGranted = false,
                previousReminderEnabled = true,
                newReminderEnabled = true,
                hasReminderTime = true,
            ),
        )
        assertFalse(
            NotificationPolicy.shouldRequestOnRoutineSave(
                sdkInt = 33,
                permissionGranted = false,
                previousReminderEnabled = false,
                newReminderEnabled = true,
                hasReminderTime = false,
            ),
        )
        assertFalse(
            NotificationPolicy.shouldRequestOnRoutineSave(
                sdkInt = 33,
                permissionGranted = false,
                previousReminderEnabled = false,
                newReminderEnabled = false,
                hasReminderTime = true,
            ),
        )
    }

    @Test
    fun `exact alarm policy falls back to inexact window on API 31+ when denied`() {
        assertEquals(AlarmScheduleMode.EXACT, AlarmSchedulingPolicy.modeFor(30, canScheduleExactAlarms = false))
        assertEquals(AlarmScheduleMode.INEXACT_WINDOW, AlarmSchedulingPolicy.modeFor(31, canScheduleExactAlarms = false))
        assertEquals(AlarmScheduleMode.INEXACT_WINDOW, AlarmSchedulingPolicy.modeFor(35, canScheduleExactAlarms = false))
        assertEquals(AlarmScheduleMode.EXACT, AlarmSchedulingPolicy.modeFor(31, canScheduleExactAlarms = true))
        assertEquals(AlarmScheduleMode.EXACT, AlarmSchedulingPolicy.modeFor(35, canScheduleExactAlarms = true))
    }

    @Test
    fun `manifest omits USE_EXACT_ALARM and retains SCHEDULE_EXACT_ALARM`() {
        val manifest = File(appDir, "src/main/AndroidManifest.xml").readText(StandardCharsets.UTF_8)
        assertFalse("Manifest must not declare USE_EXACT_ALARM", manifest.contains("USE_EXACT_ALARM"))
        assertTrue("Manifest must retain SCHEDULE_EXACT_ALARM", manifest.contains("SCHEDULE_EXACT_ALARM"))
    }

    @Test
    fun `digest slider snaps in 15-minute steps while clamp preserves legacy values`() {
        assertEquals(0, AppConstants.clampDigestMinutes(-5))
        assertEquals(1425, AppConstants.clampDigestMinutes(1425))
        assertEquals(1439, AppConstants.clampDigestMinutes(1439))
        assertEquals(1439, AppConstants.clampDigestMinutes(1440))

        assertEquals(1425, AppConstants.MAX_DIGEST_SLIDER_MINUTES)
        assertEquals(94, AppConstants.DIGEST_SLIDER_STEPS)
        assertEquals(0, AppConstants.snapDigestMinutes(-5))
        assertEquals(0, AppConstants.snapDigestMinutes(0))
        assertEquals(0, AppConstants.snapDigestMinutes(7))
        assertEquals(15, AppConstants.snapDigestMinutes(8))
        assertEquals(480, AppConstants.snapDigestMinutes(487))
        assertEquals(495, AppConstants.snapDigestMinutes(488))
        assertEquals(1410, AppConstants.snapDigestMinutes(1417))
        assertEquals(1425, AppConstants.snapDigestMinutes(1418))
        assertEquals(1425, AppConstants.snapDigestMinutes(1425))
        assertEquals(1425, AppConstants.snapDigestMinutes(1439))
        assertEquals(1425, AppConstants.snapDigestMinutes(1440))
    }
}
