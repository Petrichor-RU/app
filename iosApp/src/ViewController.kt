package nl.petrichor.app

import androidx.compose.ui.window.ComposeUIViewController

@Suppress("FunctionNaming", "ktlint:standard:function-naming") // name is part of the Swift interop surface
fun ViewController() =
    ComposeUIViewController {
        App()
    }
