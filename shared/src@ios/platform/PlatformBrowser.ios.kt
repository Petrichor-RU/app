@file:Suppress("MatchingDeclarationName") // platform actual of the common expect object

package nl.petrichor.app.platform

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual object PlatformBrowser {
    actual fun open(url: String) {
        // Kotlin/Native imports the ObjC selector openURL:options:completionHandler: as openURL
        // (only the single-argument openURL: is deprecated since iOS 10).
        NSURL(string = url).let {
            UIApplication.sharedApplication.openURL(it, options = emptyMap<Any?, Any?>(), completionHandler = null)
        }
    }
}
