package nl.petrichor.app.data.recommendation

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.petrichor.app.data.outfit.ModelState
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.ndarray.data.D1Array

/**
 * Versioned serialisable model payload persisted inside [ModelState.artifactJson].
 */
@Serializable
data class RecommendationModelArtifact(
    val featureSchemaVersion: Int = FeatureExtractor.FEATURE_SCHEMA_VERSION,
    val scaler: FeatureScaler,
    val ridgeWeights: List<Double>,
)

/**
 * In-memory decoded representation of a persisted [ModelState], parsed once per scoring run
 * rather than per candidate.
 */
internal class DecodedModel(
    val schemaVersion: Int,
    val scaler: FeatureScaler,
    val weights: D1Array<Double>,
) {
    companion object {
        private val artifactJson = Json { ignoreUnknownKeys = true }

        @Suppress("TooGenericExceptionCaught") // malformed persisted JSON should safely fall back rather than crash ranking
        fun decode(state: ModelState?): DecodedModel? {
            val validState = state?.takeIf { it.schemaVersion == FeatureExtractor.FEATURE_SCHEMA_VERSION } ?: return null
            return try {
                val artifact = artifactJson.decodeFromString<RecommendationModelArtifact>(validState.artifactJson)
                val validArtifact =
                    artifact.featureSchemaVersion == FeatureExtractor.FEATURE_SCHEMA_VERSION &&
                        artifact.scaler.isValid() &&
                        artifact.ridgeWeights.size == FeatureExtractor.FEATURE_COUNT &&
                        artifact.ridgeWeights.all { it.isFinite() }
                if (!validArtifact) {
                    null
                } else {
                    DecodedModel(
                        schemaVersion = validState.schemaVersion,
                        scaler = artifact.scaler,
                        weights = mk.ndarray(artifact.ridgeWeights.toDoubleArray()),
                    )
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
