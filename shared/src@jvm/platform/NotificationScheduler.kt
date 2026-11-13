package nl.petrichor.app.platform

actual object NotificationScheduler {
    actual fun scheduleDailyReminders() {
        // Desktop has no notification scheduling integration; reminders are a no-op.
    }
}
