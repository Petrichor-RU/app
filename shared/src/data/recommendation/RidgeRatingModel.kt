package nl.petrichor.app.data.recommendation

import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingType
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.api.zeros
import org.jetbrains.kotlinx.multik.ndarray.data.D1Array
import org.jetbrains.kotlinx.multik.ndarray.data.get
import org.jetbrains.kotlinx.multik.ndarray.data.set
import kotlin.math.abs
import kotlin.math.min

/**
 * Prior-plus-residual ridge regression model backed by `multik-kotlin`.
 *
 * Fits residuals `r_i = y_i - priorPrediction(x_i)` over the core condition-aware feature vector
 * with a fixed prior precision `lambda` (independent of `n`, aligning with Gaussian prior variance
 * `sigma^2 / lambda`) and solves `(X^T X + Lambda) beta = X^T r` via PLU decomposition, keeping
 * the intercept unpenalised.
 */
internal object RidgeRatingModel {
    const val DEFAULT_PRIOR_PRECISION = 1.0

    fun priorPrediction(raw: RawOutfitSignals): Double {
        val ideal = priorIdealWarmth(raw.tempC, raw.isRainy)
        return (5.0 - min(4.0, abs(raw.totalWarmth - ideal))).coerceIn(1.0, 5.0)
    }

    fun fit(
        samples: List<Pair<RawOutfitSignals, Double>>,
        priorPrecision: Double = DEFAULT_PRIOR_PRECISION,
    ): RecommendationModelArtifact {
        val precisionDiagonal =
            DoubleArray(FeatureExtractor.CORE_FEATURE_COUNT) { idx ->
                if (idx == 0) 0.0 else priorPrecision
            }
        return fitWithDiagonalPrecision(samples, precisionDiagonal)
    }

    internal fun fitWithDiagonalPrecision(
        samples: List<Pair<RawOutfitSignals, Double>>,
        precisionDiagonal: DoubleArray,
    ): RecommendationModelArtifact {
        require(samples.isNotEmpty()) { "Cannot fit a ridge model without samples" }
        val d = FeatureExtractor.CORE_FEATURE_COUNT
        require(precisionDiagonal.size == d) {
            "Expected $d precision entries"
        }
        val rawSignals = samples.map { it.first }
        val scaler = FeatureScaler.fit(rawSignals)
        val n = samples.size
        val xMatrix = mk.zeros<Double>(n, d)
        val residuals =
            DoubleArray(n) { row ->
                val features = FeatureExtractor.buildCoreFeatures(rawSignals[row], scaler)
                for (col in 0 until d) {
                    xMatrix[row, col] = features[col]
                }
                val (signals, target) = samples[row]
                target - priorPrediction(signals)
            }

        val rVector = mk.ndarray(residuals)
        val xt = xMatrix.transpose()
        val normalMatrix = StatsFacade.matMul(xt, xMatrix)
        for (i in 0 until d) {
            normalMatrix[i, i] = normalMatrix[i, i] + precisionDiagonal[i]
        }
        val rhs = StatsFacade.matVec(xt, rVector)
        val beta = StatsFacade.solveLinearSystem(normalMatrix, rhs)
        val weights = List(d) { idx -> beta[idx] }

        return RecommendationModelArtifact(
            featureSchemaVersion = FeatureExtractor.FEATURE_SCHEMA_VERSION,
            scaler = scaler,
            ridgeWeights = weights,
        )
    }

    fun predict(
        model: DecodedModel,
        tempC: Double,
        isRainy: Boolean,
        pieces: List<ClothingPiece>,
        typeById: Map<Long, ClothingType>,
        windSpeedMs: Double? = null,
        precipitationProbability: Double? = null,
        rainVolumeMm: Double? = null,
    ): Double {
        val raw =
            FeatureExtractor.extractRaw(
                tempC = tempC,
                isRainy = isRainy,
                pieces = pieces,
                typeById = typeById,
                windSpeedMs = windSpeedMs,
                precipitationProbability = precipitationProbability,
                rainVolumeMm = rainVolumeMm,
            )
        val features = FeatureExtractor.buildCoreFeatures(raw, model.scaler)
        val coreWeights =
            if (model.weights.size == FeatureExtractor.CORE_FEATURE_COUNT) {
                model.weights
            } else {
                mk.ndarray(DoubleArray(FeatureExtractor.CORE_FEATURE_COUNT) { idx -> model.weights[idx] })
            }
        return predictFromFeatures(raw, features, coreWeights)
    }

    fun predict(
        artifact: RecommendationModelArtifact,
        raw: RawOutfitSignals,
    ): Double {
        val features = FeatureExtractor.buildCoreFeatures(raw, artifact.scaler)
        val coreWeights = mk.ndarray(DoubleArray(FeatureExtractor.CORE_FEATURE_COUNT) { idx -> artifact.ridgeWeights[idx] })
        return predictFromFeatures(raw, features, coreWeights)
    }

    private fun predictFromFeatures(
        raw: RawOutfitSignals,
        features: DoubleArray,
        weights: D1Array<Double>,
    ): Double {
        val residual = StatsFacade.dot(weights, mk.ndarray(features))
        return (priorPrediction(raw) + residual).coerceIn(1.0, 5.0)
    }
}
