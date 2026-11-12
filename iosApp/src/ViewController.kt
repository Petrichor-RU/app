package nl.petrichor.app

import androidx.compose.ui.window.ComposeUIViewController
import nl.petrichor.app.platform.NotificationScheduler
import nl.petrichor.app.ui.journal.JournalActionBus
import nl.petrichor.app.ui.journal.JournalPendingAction
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionAlert
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol

/**
 * Forwards tapped journal reminder notifications to the [JournalActionBus], so a tap deep-links
 * into the Journal tab (plan section 2.2). Written blind: no macOS toolchain available here.
 */
private object JournalNotificationDelegate : UNUserNotificationCenterDelegateProtocol {
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (ULong) -> Unit,
    ) {
        withCompletionHandler(UNNotificationPresentationOptionAlert or UNNotificationPresentationOptionSound)
    }

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit,
    ) {
        val action = didReceiveNotificationResponse.notification.request.content.userInfo["action"] as? String
        when (action) {
            NotificationScheduler.ACTION_ADD_OUTFIT -> JournalActionBus.request(JournalPendingAction.ADD_OUTFIT)
            NotificationScheduler.ACTION_SURVEY -> JournalActionBus.request(JournalPendingAction.SURVEY)
        }
        withCompletionHandler()
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming") // name is part of the Swift interop surface
fun ViewController() =
    ComposeUIViewController {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.delegate = JournalNotificationDelegate
        NotificationScheduler.requestAuthorization()
        App()
    }
