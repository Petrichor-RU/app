package nl.petrichor.app.ui.journal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class JournalPendingAction {
    ADD_OUTFIT,
    SURVEY,
}

/**
 * Deep-link action bus for notification entry points (plan section 2.2). Platform entry points
 * (Android MainActivity, iOS notification delegate) call [request] before/at composition; the
 * common app shell animates to the Journal tab and hands the action to [JournalScreen].
 */
object JournalActionBus {
    private val _action = MutableStateFlow<JournalPendingAction?>(null)
    val action: StateFlow<JournalPendingAction?> = _action.asStateFlow()

    fun request(action: JournalPendingAction) {
        _action.value = action
    }

    fun consume() {
        _action.value = null
    }
}
