import SwiftUI
import UserNotifications
import KotlinModules

class JournalNotificationDelegate: NSObject, UNUserNotificationCenterDelegate {
    // Store delegate as static to prevent deallocation
    static let shared = JournalNotificationDelegate()

    func userNotificationCenter(_ center: UNUserNotificationCenter, 
                                willPresent notification: UNNotification, 
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        if #available(iOS 14.0, *) {
            completionHandler([.banner, .sound])
        } else {
            completionHandler([.alert, .sound])
        }
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, 
                                didReceive response: UNNotificationResponse, 
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        if let action = response.notification.request.content.userInfo["action"] as? String {
            switch action {
            case "add_outfit":
                JournalActionBus.shared.request(action: JournalPendingAction.addOutfit)
            case "survey":
                JournalActionBus.shared.request(action: JournalPendingAction.survey)
            default:
                break
            }
        }
        completionHandler()
    }
}

// Keep a strong reference to the delegate to prevent deallocation
private let notificationDelegate = JournalNotificationDelegate.shared

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> some UIViewController {
        ViewControllerKt.ViewController()
    }
    func updateUIViewController(_ uiViewController: UIViewControllerType, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView().ignoresSafeArea()
    }
}

@main
struct iosApp: App {
    init() {
        UNUserNotificationCenter.current().delegate = JournalNotificationDelegate.shared
        NotificationScheduler.shared.requestAuthorization()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
