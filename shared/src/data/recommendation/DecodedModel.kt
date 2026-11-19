package nl.petrichor.app.data.recommendation

import kotlinx.serialization.json.Json
import nl.petrichor.app.data.outfit.ModelState

/**
 * In-memory decoded representation of a persisted [ModelState], parsed once per scoring run
 * rather than per candidate.
 */
internal class DecodedModel(
    val schemaVersion: Int,
    val coefficients: DoubleArray,
) {
    companion object {
        @Suppress("TooGenericExceptionCaught") // malformed persisted JSON should safely fall back rather than crash ranking
        fun decode(state: ModelState?): DecodedModel? {
            if (state == null || state.schemaVersion != ModelState.LEGACY_FEATURE_SCHEMA_VERSION) return null
            return try {
                val coefficients = Json.decodeFromString<List<Double>>(state.coefficientsJson).toDoubleArray()
                if (coefficients.isEmpty() || coefficients.any { !it.isFinite() }) {
                    null
                } else {
                    DecodedModel(schemaVersion = state.schemaVersion, coefficients = coefficients)
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
