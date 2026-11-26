package nl.petrichor.app.data.recommendation

import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingType
import org.jetbrains.kotlinx.multik.api.linalg.dot
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.api.zeros
import org.jetbrains.kotlinx.multik.ndarray.data.D1Array
import org.jetbrains.kotlinx.multik.ndarray.data.D2Array
import org.jetbrains.kotlinx.multik.ndarray.data.get
import org.jetbrains.kotlinx.multik.ndarray.data.set
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

internal data class RatingPosterior(
    val mean: D1Array<Double>,
    val covariance: D2Array<Double>,
) {
    val dimension: Int
        get() = mean.size
}

internal data class RatingPrediction(
    val mean: Double,
    val unclippedMean: Double,
    val predictiveVariance: Double,
    val epistemicStd: Double,
)

internal data class BayesianRatingStep(
    val features: DoubleArray,
    val residual: Double,
    val deltaDays: Double = 0.0,
)

private const val FIRST_INTERACTION_INDEX = 7

internal fun buildPriorVariances(
    dimension: Int,
    offsetVariance: Double,
    linearVariance: Double,
    interactionVariance: Double,
    garmentVariance: Double,
): DoubleArray =
    DoubleArray(dimension) { index ->
        when {
            index == 0 -> offsetVariance
            index < FIRST_INTERACTION_INDEX -> linearVariance
            index < FeatureExtractor.CORE_FEATURE_COUNT -> interactionVariance
            else -> garmentVariance
        }
    }

/**
 * Recursive Bayesian linear rating model using Joseph-form Kalman filter updates on Multik
 * arrays.
 *
 * Models residuals around [priorPrediction] with diagonal prior covariance: wide on personal
 * offset (`0`), moderate on linear terms (`1..6`), narrower on interactions (`7..9`), and
 * strongly shrunk on active per-garment residuals (`10+`) with variance [GARMENT_PRIOR_VARIANCE].
 * Unseen or rarely worn pieces inherit zero residual mean while retaining [GARMENT_PRIOR_VARIANCE]
 * epistemic variance per piece.
 */
internal object BayesianRatingModel {
    const val DEFAULT_OBSERVATION_VARIANCE = 0.64
    const val DEFAULT_OFFSET_DRIFT_PER_DAY = 0.002
    const val OFFSET_PRIOR_VARIANCE = 2.0
    const val GARMENT_PRIOR_VARIANCE = 0.08
    private const val LINEAR_PRIOR_VARIANCE = 0.8
    private const val INTERACTION_PRIOR_VARIANCE = 0.4
    private const val MIN_VARIANCE = 1e-9

    fun priorPrediction(raw: RawOutfitSignals): Double {
        val ideal = priorIdealWarmth(raw.tempC, raw.isRainy)
        return (5.0 - min(4.0, abs(raw.totalWarmth - ideal))).coerceIn(1.0, 5.0)
    }

    fun defaultPriorVariances(dimension: Int = FeatureExtractor.CORE_FEATURE_COUNT): DoubleArray =
        buildPriorVariances(
            dimension = dimension,
            offsetVariance = OFFSET_PRIOR_VARIANCE,
            linearVariance = LINEAR_PRIOR_VARIANCE,
            interactionVariance = INTERACTION_PRIOR_VARIANCE,
            garmentVariance = GARMENT_PRIOR_VARIANCE,
        )

    fun prior(
        dimension: Int = FeatureExtractor.CORE_FEATURE_COUNT,
        priorVariances: DoubleArray = defaultPriorVariances(dimension),
    ): RatingPosterior {
        require(priorVariances.size == dimension) {
            "Expected $dimension prior variances, got ${priorVariances.size}"
        }
        return RatingPosterior(
            mean = mk.zeros<Double>(dimension),
            covariance = StatsFacade.diagonalMatrix(priorVariances),
        )
    }

    @Suppress("LongParameterList") // mathematical state-space parameters kept explicit
    fun update(
        posterior: RatingPosterior,
        features: DoubleArray,
        residual: Double,
        deltaDays: Double = 0.0,
        observationVariance: Double = DEFAULT_OBSERVATION_VARIANCE,
        qOffset: Double = DEFAULT_OFFSET_DRIFT_PER_DAY,
        priorOffsetVariance: Double = OFFSET_PRIOR_VARIANCE,
    ): RatingPosterior {
        val d = posterior.dimension
        require(features.size == d) { "Feature dimension ${features.size} != posterior dimension $d" }

        val predictedCov = posterior.covariance.copy()
        if (qOffset > 0.0 && deltaDays > 0.0) {
            predictedCov[0, 0] = min(priorOffsetVariance, predictedCov[0, 0] + qOffset * deltaDays)
        }

        val x = mk.ndarray(features)
        val pX = mk.linalg.dot(predictedCov, x)
        val epistemicVar = mk.linalg.dot(x, pX).coerceAtLeast(0.0)
        val innovationVar = (observationVariance + epistemicVar).coerceAtLeast(MIN_VARIANCE)
        val gain = DoubleArray(d) { idx -> pX[idx] / innovationVar }
        val innovation = residual - mk.linalg.dot(x, posterior.mean)

        val nextMean = mk.ndarray(DoubleArray(d) { idx -> posterior.mean[idx] + gain[idx] * innovation })
        val joseph = computeJosephCovariance(d, gain, features, predictedCov, observationVariance)
        return RatingPosterior(mean = nextMean, covariance = joseph)
    }

    private fun computeJosephCovariance(
        dimension: Int,
        gain: DoubleArray,
        features: DoubleArray,
        predictedCov: D2Array<Double>,
        observationVariance: Double,
    ): D2Array<Double> {
        // A = I - k * x^T
        val aMatrix = mk.zeros<Double>(dimension, dimension)
        for (i in 0 until dimension) {
            for (j in 0 until dimension) {
                val identityEntry = if (i == j) 1.0 else 0.0
                aMatrix[i, j] = identityEntry - gain[i] * features[j]
            }
        }
        val aP = mk.linalg.dot(aMatrix, predictedCov)
        val aPaT = mk.linalg.dot(aP, aMatrix.transpose())
        for (i in 0 until dimension) {
            for (j in 0 until dimension) {
                aPaT[i, j] = aPaT[i, j] + observationVariance * gain[i] * gain[j]
            }
        }
        return StatsFacade.symmetriseAndFloor(aPaT, MIN_VARIANCE)
    }

    fun fitReplay(
        steps: List<BayesianRatingStep>,
        dimension: Int = steps.firstOrNull()?.features?.size ?: FeatureExtractor.CORE_FEATURE_COUNT,
        priorVariances: DoubleArray = defaultPriorVariances(dimension),
        observationVariance: Double = DEFAULT_OBSERVATION_VARIANCE,
        qOffset: Double = DEFAULT_OFFSET_DRIFT_PER_DAY,
    ): RatingPosterior {
        var posterior = prior(dimension, priorVariances)
        for (step in steps) {
            posterior =
                update(
                    posterior = posterior,
                    features = step.features,
                    residual = step.residual,
                    deltaDays = step.deltaDays,
                    observationVariance = observationVariance,
                    qOffset = qOffset,
                    priorOffsetVariance = priorVariances[0],
                )
        }
        return posterior
    }

    @Suppress("LongParameterList") // mathematical prediction parameters including unseen-garment variance
    fun predict(
        posterior: RatingPosterior,
        raw: RawOutfitSignals,
        features: DoubleArray,
        observationVariance: Double = DEFAULT_OBSERVATION_VARIANCE,
        unseenPieceCount: Int = 0,
        garmentPriorVariance: Double = GARMENT_PRIOR_VARIANCE,
    ): RatingPrediction {
        val x = mk.ndarray(features)
        val unclipped = priorPrediction(raw) + mk.linalg.dot(x, posterior.mean)
        val activeVar = StatsFacade.quadForm(x, posterior.covariance).coerceAtLeast(0.0)
        val epistemicVar = (activeVar + unseenPieceCount * garmentPriorVariance).coerceAtLeast(0.0)
        return RatingPrediction(
            mean = unclipped.coerceIn(1.0, 5.0),
            unclippedMean = unclipped,
            predictiveVariance = observationVariance + epistemicVar,
            epistemicStd = sqrt(epistemicVar),
        )
    }

    fun predict(
        model: DecodedModel,
        tempC: Double,
        isRainy: Boolean,
        pieces: List<ClothingPiece>,
        typeById: Map<Long, ClothingType>,
    ): RatingPrediction {
        val extracted = FeatureExtractor.extractForModel(model, tempC, isRainy, pieces, typeById)
        return predict(model.ratingPosterior, extracted.raw, extracted.features, unseenPieceCount = extracted.unseenPieceIds.size)
    }
}
