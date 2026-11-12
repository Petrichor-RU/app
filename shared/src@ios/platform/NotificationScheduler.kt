package nl.petrichor.app.platform

import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

/**
 * iOS implementation (plan section 2.2), written blind (no macOS toolchain here): two
 * repeating UNCalendarNotificationTrigger requests at the reminder hours. Tapping a
 * notification is delivered to the UNUserNotificationCenterDelegate installed in
 * iosApp's ViewController, which forwards the `action` userInfo entry to the JournalActionBus.
 */
actual object NotificationScheduler {
    const val ADD_OUTFIT_ID = "journal.add_outfit"
    const val SURVEY_ID = "journal.survey"

    const val ACTION_ADD_OUTFIT = "add_outfit"
    const val ACTION_SURVEY = "survey"

    actual fun scheduleDailyReminders() {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removePendingNotificationRequestsWithIdentifiers(listOf(ADD_OUTFIT_ID, SURVEY_ID))
        schedule(
            center,
            ReminderSpec(
                identifier = ADD_OUTFIT_ID,
                hour = ReminderTimes.MORNING_HOUR,
                title = "Log today's outfit",
                body = "Take a selfie or pick your pieces.",
                action = ACTION_ADD_OUTFIT,
            ),
        )
        schedule(
            center,
            ReminderSpec(
                identifier = SURVEY_ID,
                hour = ReminderTimes.EVENING_HOUR,
                title = "How did it feel today?",
                body = "Fill in your evening survey.",
                action = ACTION_SURVEY,
            ),
        )
    }

    /** Requests notification authorization once; safe to call on every launch. */
    fun requestAuthorization() {
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
        ) { _, _ -> }
    }

    private fun schedule(
        center: UNUserNotificationCenter,
        spec: ReminderSpec,
    ) {
        val content =
            UNMutableNotificationContent().apply {
                setTitle(spec.title)
                setBody(spec.body)
                userInfo = mapOf("action" to spec.action)
            }
        val components =
            NSDateComponents().apply {
                setHour(spec.hour.toLong())
            }
        val trigger = UNCalendarNotificationTrigger.calendarNotificationTriggerWithDateComponents(components, true)
        val request = UNNotificationRequest.notificationRequestWithIdentifier(spec.identifier, content, trigger)
        center.addNotificationRequest(request) { _ -> }
    }
}

/** One reminder schedule entry. */
private class ReminderSpec(
    val identifier: String,
    val hour: Int,
    val title: String,
    val body: String,
    val action: String,
)
