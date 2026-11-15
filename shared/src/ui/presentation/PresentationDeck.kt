package nl.petrichor.app.ui.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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

/** What an arrow key press should do to the deck. */
internal enum class DeckKeyAction {
    FORWARD,
    BACKWARD,
}

/**
 * Maps an arrow key to a deck action: right arrow moves forward, left arrow moves backward, and
 * every other key (including space and backspace) is left alone so text entry in the embedded
 * tab screens is never disturbed.
 */
internal fun deckKeyActionFor(key: Key): DeckKeyAction? =
    when (key) {
        Key.DirectionRight -> DeckKeyAction.FORWARD
        Key.DirectionLeft -> DeckKeyAction.BACKWARD
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
 * The deck is deliberately not swipeable; the only slide shortcuts are the left and right arrow
 * keys on the focused deck root, so typing and cursor movement in the embedded tab screens'
 * text fields is never disturbed.
 */
@Composable
internal fun PresentationDeck(
    slides: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
) {
    val deckState = remember(slides.size) { PresentationDeckState(slides.size) }
    var slideIndex by remember(slides.size) { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val tapInteractionSource = remember { MutableInteractionSource() }

    fun goBackward() {
        if (deckState.previous()) slideIndex = deckState.slideIndex
    }

    fun goForward() {
        if (deckState.next()) slideIndex = deckState.slideIndex
    }

    LaunchedEffect(slideIndex) {
        runCatching { focusRequester.requestFocus() }
    }

    ProvideOnSurfaceContentColor {
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .deckKeyNavigation(
                        focusRequester = focusRequester,
                        tapInteractionSource = tapInteractionSource,
                        onBackward = ::goBackward,
                        onForward = ::goForward,
                    ),
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
    }
}

/**
 * Sets the deck-wide content colour to the scheme's onSurface: slides render straight on the
 * scrim without a Scaffold, and Material3's default local content colour is a fixed black that
 * would otherwise keep text unreadable once the scheme turns dark at night.
 */
@Composable
private fun ProvideOnSurfaceContentColor(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface, content = content)
}

/**
 * Makes the deck root the arrow-key handler: the root takes focus on every slide change and on
 * an unhandled tap (so the keys re-arm after dialogs or text fields), and listens in the
 * bubbling phase on [KeyEventType.KeyUp] so keys an embedded widget consumes — such as text
 * fields' cursor movement — never flip slides.
 */
private fun Modifier.deckKeyNavigation(
    focusRequester: FocusRequester,
    tapInteractionSource: MutableInteractionSource,
    onBackward: () -> Unit,
    onForward: () -> Unit,
): Modifier =
    this
        .focusRequester(focusRequester)
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyUp) return@onKeyEvent false

            when (deckKeyActionFor(event.key)) {
                DeckKeyAction.BACKWARD -> {
                    onBackward()
                    true
                }

                DeckKeyAction.FORWARD -> {
                    onForward()
                    true
                }

                null -> {
                    false
                }
            }
        }.focusable()
        .clickable(interactionSource = tapInteractionSource, indication = null) {
            runCatching { focusRequester.requestFocus() }
        }

/** The always-visible slide indicator and the two navigation buttons. */
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
