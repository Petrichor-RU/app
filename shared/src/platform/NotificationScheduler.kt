package nl.petrichor.app.platform

/**
 * Platform entry point for scheduling the two daily journal reminders (plan section 2.2):
 * 08:00 "log today's outfit" and 20:00 "evening survey". Idempotent — calling it again
 * replaces the previous schedule. Tapping a notification deep-links into the Journal tab via
 * [nl.petrichor.app.ui.journal.JournalActionBus].
 */
expect object NotificationScheduler {
    fun scheduleDailyReminders()
}
