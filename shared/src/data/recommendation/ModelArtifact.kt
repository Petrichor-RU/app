package nl.petrichor.app.data.recommendation

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.petrichor.app.data.outfit.ModelState
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.api.zeros
import org.jetbrains.kotlinx.multik.ndarray.data.D1Array
import org.jetbrains.kotlinx.multik.ndarray.data.D2Array
import org.jetbrains.kotlinx.multik.ndarray.data.get
import org.jetbrains.kotlinx.multik.ndarray.data.set

/**
 * Versioned serialisable model payload persisted inside [ModelState.artifactJson].
 * Stores standardisation stats, rating posterior (mean + covariance), ordinal thermal posterior
 * (mean + covariance + threshold), active per-garment IDs, and replay fingerprint metadata.
 */
@Serializable
data class RecommendationModelArtifact(
    val featureSchemaVersion: Int = FeatureExtractor.FEATURE_SCHEMA_VERSION,
    val scaler: FeatureScaler,
    val ridgeWeights: List<Double>,
    val ratingMean: List<Double> = ridgeWeights,
    val ratingCovariance: List<Double> = emptyList(),
    val thermalMean: List<Double> = emptyList(),
    val thermalCovariance: List<Double> = emptyList(),
    val thermalThreshold: Double = BayesianThermalModel.DEFAULT_THRESHOLD,
    val activePieceIds: List<Long> = emptyList(),
    val observationCount: Int = 0,
    val lastObservationEpochDays: Long = 0L,
    val lastObservationEntryId: Long = 0L,
    val historyFingerprint: Long = 0L,
)

internal data class ReplayMetadata(
    val observationCount: Int,
    val lastObservationEpochDays: Long,
    val lastObservationEntryId: Long,
    val historyFingerprint: Long,
)

/**
 * In-memory decoded representation of a persisted [ModelState], parsed once per scoring run
 * rather than per candidate.
 */
internal class DecodedModel(
    val schemaVersion: Int,
    val scaler: FeatureScaler,
    val ratingPosterior: RatingPosterior,
    val thermalPosterior: ThermalPosterior,
    val activePieceIds: List<Long>,
    val replay: ReplayMetadata,
) {
    val activePieceIndexById: Map<Long, Int> =
        activePieceIds.withIndex().associate { (index, pieceId) -> pieceId to index }

    val weights: D1Array<Double>
        get() = ratingPosterior.mean

    val dimension: Int
        get() = ratingPosterior.dimension

    val observationCount: Int
        get() = replay.observationCount

    val lastObservationEpochDays: Long
        get() = replay.lastObservationEpochDays

    val lastObservationEntryId: Long
        get() = replay.lastObservationEntryId

    val historyFingerprint: Long
        get() = replay.historyFingerprint

    companion object {
        private val artifactJson = Json { ignoreUnknownKeys = true }

        internal fun flattenMatrix(matrix: D2Array<Double>): List<Double> {
            val rows = matrix.shape[0]
            val cols = matrix.shape[1]
            return buildList(rows * cols) {
                for (i in 0 until rows) {
                    for (j in 0 until cols) {
                        add(matrix[i, j])
                    }
                }
            }
        }

        internal fun flattenVector(vector: D1Array<Double>): List<Double> = List(vector.size) { idx -> vector[idx] }

        @Suppress("TooGenericExceptionCaught") // malformed persisted JSON should safely fall back rather than crash ranking
        fun decode(state: ModelState?): DecodedModel? {
            val validState = state?.takeIf { it.schemaVersion == ModelState.CURRENT_SCHEMA_VERSION }
            return validState?.let {
                try {
                    val artifact = artifactJson.decodeFromString<RecommendationModelArtifact>(it.artifactJson)
                    buildDecodedModel(it.schemaVersion, artifact)
                } catch (_: Exception) {
                    null
                }
            }
        }

        private fun buildDecodedModel(
            schemaVersion: Int,
            artifact: RecommendationModelArtifact,
        ): DecodedModel? {
            if (!isValidArtifact(artifact)) return null
            val d = FeatureExtractor.CORE_FEATURE_COUNT + artifact.activePieceIds.size
            val ratingMean = mk.ndarray(artifact.ratingMean.toDoubleArray())
            val ratingCov = decodeMatrix(artifact.ratingCovariance, d) ?: BayesianRatingModel.prior(d).covariance
            val thermalMean = if (artifact.thermalMean.isEmpty()) mk.zeros(d) else mk.ndarray(artifact.thermalMean.toDoubleArray())
            val thermalCov =
                decodeMatrix(artifact.thermalCovariance, d)
                    ?: BayesianThermalModel.prior(d, artifact.thermalThreshold).covariance
            return DecodedModel(
                schemaVersion = schemaVersion,
                scaler = artifact.scaler,
                ratingPosterior = RatingPosterior(ratingMean, ratingCov),
                thermalPosterior = ThermalPosterior(thermalMean, thermalCov, artifact.thermalThreshold),
                activePieceIds = artifact.activePieceIds,
                replay =
                    ReplayMetadata(
                        observationCount = artifact.observationCount,
                        lastObservationEpochDays = artifact.lastObservationEpochDays,
                        lastObservationEntryId = artifact.lastObservationEntryId,
                        historyFingerprint = artifact.historyFingerprint,
                    ),
            )
        }

        private fun isValidArtifact(artifact: RecommendationModelArtifact): Boolean {
            val validPieces =
                artifact.activePieceIds.size <= FeatureExtractor.MAX_ACTIVE_PIECES &&
                    artifact.activePieceIds.distinct().size == artifact.activePieceIds.size
            val d = FeatureExtractor.CORE_FEATURE_COUNT + artifact.activePieceIds.size
            val validVectors =
                validPieces &&
                    artifact.featureSchemaVersion == FeatureExtractor.FEATURE_SCHEMA_VERSION &&
                    artifact.scaler.isValid() &&
                    isValidVector(artifact.ridgeWeights, d) &&
                    isValidVector(artifact.ratingMean, d) &&
                    (artifact.thermalMean.isEmpty() || isValidVector(artifact.thermalMean, d))
            val validMatrices =
                isValidCovariance(artifact.ratingCovariance, d) &&
                    isValidCovariance(artifact.thermalCovariance, d)
            val validMeta =
                artifact.thermalThreshold.isFinite() &&
                    artifact.thermalThreshold > 0.0 &&
                    artifact.observationCount >= 0
            return validVectors && validMatrices && validMeta
        }

        private fun isValidVector(
            vector: List<Double>,
            dimension: Int,
        ): Boolean = vector.size == dimension && vector.all { it.isFinite() }

        private fun isValidCovariance(
            flat: List<Double>,
            dimension: Int,
        ): Boolean =
            flat.isEmpty() ||
                (
                    flat.size == dimension * dimension &&
                        flat.all { it.isFinite() } &&
                        (0 until dimension).all { i -> flat[i * dimension + i] > 0.0 }
                )

        private fun decodeMatrix(
            flat: List<Double>,
            dimension: Int,
        ): D2Array<Double>? {
            if (flat.isEmpty()) return null
            val matrix = mk.zeros<Double>(dimension, dimension)
            for (i in 0 until dimension) {
                for (j in 0 until dimension) {
                    matrix[i, j] = flat[i * dimension + j]
                }
            }
            return matrix
        }
    }
}
