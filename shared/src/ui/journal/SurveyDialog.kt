package nl.petrichor.app.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.petrichor.app.data.outfit.OutfitEntryWithPieces
import nl.petrichor.app.data.outfit.WarmthVerdict
import kotlin.math.roundToInt

/**
 * Evening survey (plan section 5.4): two required questions, feeling score 1-5 and warmth
 * verdict. Submit is disabled until both are answered. With [entry] == null the empty-state
 * variant is shown (20:00 deep link when nothing was logged today): "Log outfit first".
 */
@Composable
fun SurveyDialog(
    entry: OutfitEntryWithPieces?,
    onDismiss: () -> Unit,
    onSubmit: suspend (entryId: Long, score: Int, verdict: WarmthVerdict) -> Boolean,
    onLogOutfitFirst: () -> Unit,
) {
    var score by remember { mutableStateOf<Int?>(entry?.entry?.surveyFeelingScore) }
    var verdict by remember { mutableStateOf<WarmthVerdict?>(entry?.entry?.surveyWarmthVerdict) }
    var isSubmitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("How did it go today?") },
        text = {
            if (entry == null) {
                SurveyEmptyState(onLogOutfitFirst = onLogOutfitFirst)
            } else {
                SurveyForm(
                    score = score,
                    onScoreChange = { score = it },
                    verdict = verdict,
                    onVerdictChange = { verdict = it },
                )
            }
        },
        confirmButton = {
            if (entry != null) {
                SurveySubmitButton(
                    entry = entry,
                    submit =
                        SurveySubmitState(
                            score = score,
                            verdict = verdict,
                            isSubmitting = isSubmitting,
                            onSubmittingChange = { isSubmitting = it },
                            onSubmit = onSubmit,
                            onDismiss = onDismiss,
                        ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun SurveyEmptyState(onLogOutfitFirst: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("You haven't logged an outfit yet today.")
        Button(onClick = onLogOutfitFirst, modifier = Modifier.fillMaxWidth()) {
            Text("Log outfit first")
        }
    }
}

@Composable
private fun SurveyForm(
    score: Int?,
    onScoreChange: (Int) -> Unit,
    verdict: WarmthVerdict?,
    onVerdictChange: (WarmthVerdict) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("How did you feel in this outfit?", style = MaterialTheme.typography.bodyMedium)
            ScoreRow(score = score, onScoreChange = onScoreChange)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Was it too cold, perfect, or too warm?", style = MaterialTheme.typography.bodyMedium)
            VerdictRow(verdict = verdict, onVerdictChange = onVerdictChange)
        }
    }
}

@Composable
private fun ScoreRow(
    score: Int?,
    onScoreChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = if (score != null) "Score: $score / 5" else "Slide to choose 1-5",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = (score ?: 3).toFloat(),
            onValueChange = { onScoreChange(it.roundToInt().coerceIn(1, 5)) },
            valueRange = 1f..5f,
            steps = 3, // discrete stops at 1, 2, 3, 4, 5
            modifier = Modifier.fillMaxWidth().testTag("survey_score_slider"),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("1", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("5", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VerdictRow(
    verdict: WarmthVerdict?,
    onVerdictChange: (WarmthVerdict) -> Unit,
) {
    // FlowRow so the three verdict buttons wrap onto a second line on narrow screens.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (option in WarmthVerdict.entries) {
            val selected = verdict == option
            if (selected) {
                Button(onClick = { onVerdictChange(option) }) { Text(option.verdictLabel()) }
            } else {
                OutlinedButton(onClick = { onVerdictChange(option) }) { Text(option.verdictLabel()) }
            }
        }
    }
}

/** Bundled submit state for [SurveySubmitButton]. */
private class SurveySubmitState(
    val score: Int?,
    val verdict: WarmthVerdict?,
    val isSubmitting: Boolean,
    val onSubmittingChange: (Boolean) -> Unit,
    val onSubmit: suspend (entryId: Long, score: Int, verdict: WarmthVerdict) -> Boolean,
    val onDismiss: () -> Unit,
)

@Composable
private fun SurveySubmitButton(
    entry: OutfitEntryWithPieces,
    submit: SurveySubmitState,
) {
    val coroutineScope = rememberCoroutineScope()
    TextButton(
        onClick = {
            val selectedScore = submit.score
            val selectedVerdict = submit.verdict
            if (selectedScore != null && selectedVerdict != null) {
                submit.onSubmittingChange(true)
                coroutineScope.launch {
                    val saved = submit.onSubmit(entry.entry.id, selectedScore, selectedVerdict)
                    submit.onSubmittingChange(false)
                    if (saved) submit.onDismiss()
                }
            }
        },
        enabled = !submit.isSubmitting && submit.score != null && submit.verdict != null,
    ) {
        Text(if (entry.entry.surveyFeelingScore != null) "Save Changes" else "Submit")
    }
}

/** User-visible label for a warmth verdict, shared by the journal rows and detail sheet. */
fun WarmthVerdict.verdictLabel(): String =
    when (this) {
        WarmthVerdict.TOO_COLD -> "Too cold"
        WarmthVerdict.PERFECT -> "Perfect"
        WarmthVerdict.TOO_WARM -> "Too warm"
    }
