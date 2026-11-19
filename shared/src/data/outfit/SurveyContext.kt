package nl.petrichor.app.data.outfit

/**
 * Optional low-friction context question selected by active learning when an outfit survey
 * has high uncertainty, surprise, or rain exposure.
 */
enum class SurveyContextQuestion(
    val prompt: String,
    val positiveLabel: String,
    val negativeLabel: String,
) {
    GOT_WET(
        prompt = "Optional: Did you get wet in the rain?",
        positiveLabel = "Got wet",
        negativeLabel = "Stayed dry",
    ),
    MOSTLY_INDOORS(
        prompt = "Optional: Where did you spend most of the day?",
        positiveLabel = "Mostly indoors",
        negativeLabel = "Mostly outdoors",
    ),
    HIGH_ACTIVITY(
        prompt = "Optional: How active were you today?",
        positiveLabel = "High activity",
        negativeLabel = "Normal activity",
    ),
}

/** Optional nullable answers for the evening survey follow-up context flags. */
data class SurveyContextAnswers(
    val mostlyIndoors: Boolean? = null,
    val highActivity: Boolean? = null,
    val gotWet: Boolean? = null,
) {
    fun answerFor(question: SurveyContextQuestion): Boolean? =
        when (question) {
            SurveyContextQuestion.GOT_WET -> gotWet
            SurveyContextQuestion.MOSTLY_INDOORS -> mostlyIndoors
            SurveyContextQuestion.HIGH_ACTIVITY -> highActivity
        }

    fun withToggle(
        question: SurveyContextQuestion,
        choice: Boolean,
    ): SurveyContextAnswers {
        val next = if (answerFor(question) == choice) null else choice
        return when (question) {
            SurveyContextQuestion.GOT_WET -> copy(gotWet = next)
            SurveyContextQuestion.MOSTLY_INDOORS -> copy(mostlyIndoors = next)
            SurveyContextQuestion.HIGH_ACTIVITY -> copy(highActivity = next)
        }
    }

    companion object {
        fun fromEntry(entry: OutfitEntry?): SurveyContextAnswers =
            SurveyContextAnswers(
                mostlyIndoors = entry?.mostlyIndoors,
                highActivity = entry?.highActivity,
                gotWet = entry?.gotWet,
            )
    }
}
