package com.tag.routine.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure notification-permission decision.
 *
 * POST_NOTIFICATIONS only exists from API 33, so older releases depend solely on the
 * system-wide notifications switch, while 33+ additionally needs the runtime grant.
 */
class NotificationPolicyTest {

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
}
