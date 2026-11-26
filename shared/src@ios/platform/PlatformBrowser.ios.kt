@file:Suppress("MatchingDeclarationName") // platform actual of the common expect object

package nl.petrichor.app.platform

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual object PlatformBrowser {
    actual fun open(url: String) {
        // openURL is deprecated since iOS 10; use open(_:options:completionHandler:) instead.
        NSURL(string = url).let {
            UIApplication.sharedApplication.open(it, options = emptyMap(), completionHandler = null)
        }
    }
}
