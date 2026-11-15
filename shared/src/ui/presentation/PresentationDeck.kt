package nl.petrichor.app.ui.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols

/**
 * Scrim alpha applied behind presentation slide content (writeups/2026-11-15-PRESENTATION.md,
 * section 5): a single light layer instead of the app's per-tab scrim table. Tunable.
 */
internal const val PRESENTATION_SCRIM_ALPHA = 0.35f

private const val SLIDE_TRANSITION_MILLIS = 350

/** What a deck keyboard shortcut does. */
internal enum class DeckKeyAction {
    FORWARD,
    BACKWARD,
}

/**
 * Keyboard-to-deck mapping (plan section 3.2): ArrowRight and Spacebar go forward, ArrowLeft and
 * Backspace go backward, anything else is left to the embedded interactive content. Pure so it
 * can be tested without a composed deck.
 */
internal fun deckKeyActionFor(key: Key): DeckKeyAction? =
    when (key) {
        Key.DirectionRight, Key.Spacebar -> DeckKeyAction.FORWARD
        Key.DirectionLeft, Key.Backspace -> DeckKeyAction.BACKWARD
        else -> null
    }

/**
 * Deck slide bookkeeping, kept free of Compose state so the bounds rules can be tested directly.
 * The end slides are the limits: no looping around.
 */
internal class PresentationDeckState(
    val slideCount: Int,
    initialSlide: Int = 0,
) {
    var slideIndex: Int = initialSlide.coerceIn(0, (slideCount - 1).coerceAtLeast(0))
        private set

    val canGoBack: Boolean get() = slideIndex > 0
    val canGoForward: Boolean get() = slideIndex < slideCount - 1

    /** Advances one slide; no-op on the last slide. Returns true when the index changed. */
    fun next(): Boolean {
        if (!canGoForward) return false
        slideIndex++
        return true
    }

    /** Retreats one slide; no-op on the first slide. Returns true when the index changed. */
    fun previous(): Boolean {
        if (!canGoBack) return false
        slideIndex--
        return true
    }
}

/**
 * The presentation deck (plan section 3): renders the current slide over the park scene with a
 * horizontal slide transition — next enters from the right, backward mirrors it — plus the
 * bottom-left/bottom-right navigation buttons and a small slide indicator.
 *
 * The deck is deliberately not swipeable and never scrolls: slides change only through the
 * buttons and the keyboard. Keyboard handling runs in the bubbling phase on the deck root, so
 * keys consumed while typing in a text field of an embedded tab screen never change slides.
 */
@Composable
internal fun PresentationDeck(
    slides: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
) {
    val deckState = remember(slides.size) { PresentationDeckState(slides.size) }
    var slideIndex by remember(slides.size) { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    fun goBackward() {
        if (deckState.previous()) slideIndex = deckState.slideIndex
    }

    fun goForward() {
        if (deckState.next()) slideIndex = deckState.slideIndex
    }

    Box(
        modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onKeyEvent false
                when (deckKeyActionFor(event.key)) {
                    DeckKeyAction.FORWARD -> {
                        goForward()
                        true
                    }

                    DeckKeyAction.BACKWARD -> {
                        goBackward()
                        true
                    }

                    null -> {
                        false
                    }
                }
            }.focusable(),
    ) {
        AnimatedContent(
            targetState = slideIndex,
            transitionSpec = {
                val forward = targetState > initialState
                val slideSpec = tween<IntOffset>(SLIDE_TRANSITION_MILLIS)
                val fadeSpec = tween<Float>(SLIDE_TRANSITION_MILLIS)
                (
                    slideInHorizontally(animationSpec = slideSpec) { full -> if (forward) full else -full } +
                        fadeIn(fadeSpec)
                ) togetherWith (
                    slideOutHorizontally(animationSpec = slideSpec) { full -> if (forward) -full else full } +
                        fadeOut(fadeSpec)
                )
            },
            modifier = Modifier.fillMaxSize(),
            label = "Slide transition",
        ) { index ->
            slides[index]()
        }

        DeckNavigationOverlay(
            deckState = deckState,
            slideIndex = slideIndex,
            onBackward = ::goBackward,
            onForward = ::goForward,
        )
    }

    // Keep the deck root focused so keyboard shortcuts keep working across slide changes; a
    // root that cannot take focus (such as inside a preview) simply ignores the request.
    LaunchedEffect(slideIndex) {
        runCatching { focusRequester.requestFocus() }
    }
}

/** The always-visible slide indicator and the two navigation buttons, drawn above the scrim. */
@Composable
private fun DeckNavigationOverlay(
    deckState: PresentationDeckState,
    slideIndex: Int,
    onBackward: () -> Unit,
    onForward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Text(
            text = "${slideIndex + 1} / ${deckState.slideCount}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 30.dp),
        )

        FilledIconButton(
            onClick = onBackward,
            enabled = deckState.canGoBack,
            modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(24.dp),
        ) {
            MaterialSymbol(
                iconName = MaterialSymbols.CHEVRON_LEFT,
                contentDescription = "Previous slide",
            )
        }
        FilledIconButton(
            onClick = onForward,
            enabled = deckState.canGoForward,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(24.dp),
        ) {
            MaterialSymbol(
                iconName = MaterialSymbols.CHEVRON_RIGHT,
                contentDescription = "Next slide",
            )
        }
    }
}
