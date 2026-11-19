package nl.petrichor.app.data.recommendation

import nl.petrichor.app.data.outfit.WarmthVerdict
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingType
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.api.zeros
import org.jetbrains.kotlinx.multik.ndarray.data.D1Array
import org.jetbrains.kotlinx.multik.ndarray.data.D2Array
import org.jetbrains.kotlinx.multik.ndarray.data.get
import org.jetbrains.kotlinx.multik.ndarray.data.set
import kotlin.math.exp
import kotlin.math.sqrt

internal data class ThermalPosterior(
    val mean: D1Array<Double>,
    val covariance: D2Array<Double>,
    val thresholdTau: Double = BayesianThermalModel.DEFAULT_THRESHOLD,
) {
    val dimension: Int
        get() = mean.size
}

internal data class ThermalProbabilities(
    val tooCold: Double,
    val perfect: Double,
    val tooWarm: Double,
    val latentMean: Double,
    val latentStd: Double,
    val epistemicStd: Double,
)

internal data class BayesianThermalStep(
    val raw: RawOutfitSignals,
    val features: DoubleArray,
    val verdict: WarmthVerdict,
)

/**
 * Online ordinal probit model using Assumed-Density Filtering (Chu & Ghahramani / Minka)
 * with unit observation noise `sigma = 1` and symmetric thresholds `(-tau, +tau)`.
 * Uses pooled rain/wind condition priors (`10..13`) and strongly shrunk per-garment dry-warmth
 * residuals (`14+`) with variance [GARMENT_PRIOR_VARIANCE]; unseen or rarely worn pieces retain
 * zero residual mean and [GARMENT_PRIOR_VARIANCE] epistemic variance.
 */
internal object BayesianThermalModel {
    const val DEFAULT_THRESHOLD = 0.75
    const val GARMENT_PRIOR_VARIANCE = 0.06
    private const val PRIOR_IDEAL_INTERCEPT = 2.4
    private const val PRIOR_IDEAL_SLOPE = 0.07
    private const val PRIOR_RAINY_IDEAL_BONUS = 0.2
    private const val PRIOR_SENSATION_SCALE = 3.0

    private const val OFFSET_PRIOR_VARIANCE = 0.8
    private const val LINEAR_PRIOR_VARIANCE = 0.15
    private const val INTERACTION_PRIOR_VARIANCE = 0.06
    private const val CONDITION_PRIOR_VARIANCE = 0.4
    private const val MIN_VARIANCE = 1e-9
    private const val TAIL_GUARD_Z = 6.0
    private const val MAX_DELTA2 = 0.999999
    private const val FIRST_INTERACTION_INDEX = 7
    private const val LAST_CORE_INTERACTION_INDEX = 9

    fun defaultPriorVariances(dimension: Int = FeatureExtractor.CORE_FEATURE_COUNT): DoubleArray =
        DoubleArray(dimension) { index ->
            when {
                index == 0 -> OFFSET_PRIOR_VARIANCE
                index < FIRST_INTERACTION_INDEX -> LINEAR_PRIOR_VARIANCE
                index <= LAST_CORE_INTERACTION_INDEX -> INTERACTION_PRIOR_VARIANCE
                index < FeatureExtractor.CORE_FEATURE_COUNT -> CONDITION_PRIOR_VARIANCE
                else -> GARMENT_PRIOR_VARIANCE
            }
        }

    fun prior(
        dimension: Int = FeatureExtractor.CORE_FEATURE_COUNT,
        thresholdTau: Double = DEFAULT_THRESHOLD,
        priorVariances: DoubleArray = defaultPriorVariances(dimension),
    ): ThermalPosterior {
        require(priorVariances.size == dimension) {
            "Expected $dimension prior variances, got ${priorVariances.size}"
        }
        return ThermalPosterior(
            mean = mk.zeros<Double>(dimension),
            covariance = MultikOps.diagonalMatrix(priorVariances),
            thresholdTau = thresholdTau,
        )
    }

    /**
     * Maps raw outfit signals into latent thermal sensation units around zero: negative means
     * colder than the prior ideal warmth, positive means warmer than the prior ideal warmth.
     */
    fun priorSensation(raw: RawOutfitSignals): Double {
        val ideal = PRIOR_IDEAL_INTERCEPT - PRIOR_IDEAL_SLOPE * raw.tempC + if (raw.isRainy) PRIOR_RAINY_IDEAL_BONUS else 0.0
        return PRIOR_SENSATION_SCALE * (raw.totalWarmth - ideal)
    }

    fun update(
        posterior: ThermalPosterior,
        raw: RawOutfitSignals,
        features: DoubleArray,
        verdict: WarmthVerdict,
        observationVariance: Double = 1.0,
    ): ThermalPosterior {
        val d = posterior.dimension
        require(features.size == d) { "Feature dimension ${features.size} != posterior dimension $d" }

        val noiseVariance = observationVariance.coerceAtLeast(1.0)
        val x = mk.ndarray(features)
        val pX = StatsFacade.matVec(posterior.covariance, x)
        val sSquared = (noiseVariance + StatsFacade.dot(x, pX)).coerceAtLeast(noiseVariance)
        val s = sqrt(sSquared)
        val mu = priorSensation(raw) + StatsFacade.dot(x, posterior.mean)

        val (delta1, delta2) = truncatedNormalDeltaMoments(verdict, mu, s, posterior.thresholdTau)
        val meanScale = delta1 / s
        val covScale = delta2 / sSquared

        val nextMean = mk.ndarray(DoubleArray(d) { idx -> posterior.mean[idx] + pX[idx] * meanScale })
        val rawCov = mk.zeros<Double>(d, d)
        for (i in 0 until d) {
            for (j in 0 until d) {
                rawCov[i, j] = posterior.covariance[i, j] - covScale * pX[i] * pX[j]
            }
        }
        val nextCov = MultikOps.symmetriseAndFloor(rawCov, MIN_VARIANCE)
        return posterior.copy(mean = nextMean, covariance = nextCov)
    }

    fun fitReplay(
        steps: List<BayesianThermalStep>,
        dimension: Int = steps.firstOrNull()?.features?.size ?: FeatureExtractor.CORE_FEATURE_COUNT,
        thresholdTau: Double = DEFAULT_THRESHOLD,
        priorVariances: DoubleArray = defaultPriorVariances(dimension),
    ): ThermalPosterior {
        var posterior = prior(dimension, thresholdTau, priorVariances)
        for (step in steps) {
            posterior = update(posterior, step.raw, step.features, step.verdict)
        }
        return posterior
    }

    fun predict(
        posterior: ThermalPosterior,
        raw: RawOutfitSignals,
        features: DoubleArray,
        unseenPieceCount: Int = 0,
        garmentPriorVariance: Double = GARMENT_PRIOR_VARIANCE,
    ): ThermalProbabilities {
        val x = mk.ndarray(features)
        val mu = priorSensation(raw) + StatsFacade.dot(x, posterior.mean)
        val activeVar = MultikOps.quadForm(x, posterior.covariance).coerceAtLeast(0.0)
        val epistemicVar = (activeVar + unseenPieceCount * garmentPriorVariance).coerceAtLeast(0.0)
        val s = sqrt(1.0 + epistemicVar)
        val tau = posterior.thresholdTau
        val zCold = (-tau - mu) / s
        val zWarm = (tau - mu) / s

        val cdfCold = StatsFacade.standardNormalCdf(zCold).coerceIn(0.0, 1.0)
        val cdfWarm = StatsFacade.standardNormalCdf(zWarm).coerceIn(cdfCold, 1.0)
        val pCold = cdfCold
        val pPerfect = (cdfWarm - cdfCold).coerceIn(0.0, 1.0)
        val pWarm = (1.0 - cdfWarm).coerceIn(0.0, 1.0)

        return ThermalProbabilities(
            tooCold = pCold,
            perfect = pPerfect,
            tooWarm = pWarm,
            latentMean = mu,
            latentStd = s,
            epistemicStd = sqrt(epistemicVar),
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
    ): ThermalProbabilities {
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
        val features = FeatureExtractor.buildFeatures(raw, model.scaler, pieces, model.activePieceIndexById)
        val unseenCount = FeatureExtractor.countUnseenPieces(pieces, model.activePieceIndexById)
        return predict(model.thermalPosterior, raw, features, unseenPieceCount = unseenCount)
    }

    /**
     * Computes `(delta1, delta2)` for a standard normal variable truncated to the interval
     * implied by [verdict], using Mills-ratio continued fractions when boundaries lie beyond
     * `|z| > 6`.
     */
    internal fun truncatedNormalDeltaMoments(
        verdict: WarmthVerdict,
        mu: Double,
        s: Double,
        tau: Double,
    ): Pair<Double, Double> =
        when (verdict) {
            WarmthVerdict.TOO_COLD -> {
                val beta = (-tau - mu) / s
                val (m, mMinusNegBeta) = StatsFacade.upperTailMillsRatio(-beta)
                (-m) to (m * mMinusNegBeta).coerceIn(0.0, MAX_DELTA2)
            }

            WarmthVerdict.TOO_WARM -> {
                val alpha = (tau - mu) / s
                val (m, mMinusAlpha) = StatsFacade.upperTailMillsRatio(alpha)
                m to (m * mMinusAlpha).coerceIn(0.0, MAX_DELTA2)
            }

            WarmthVerdict.PERFECT -> {
                val alpha = (-tau - mu) / s
                val beta = (tau - mu) / s
                perfectMoments(alpha, beta)
            }
        }

    private fun perfectMoments(
        alpha: Double,
        beta: Double,
    ): Pair<Double, Double> =
        when {
            alpha > TAIL_GUARD_Z -> {
                perfectRightTailMoments(alpha, beta)
            }

            beta < -TAIL_GUARD_Z -> {
                val (posD1, d2) = perfectRightTailMoments(-beta, -alpha)
                (-posD1) to d2
            }

            else -> {
                val z = StatsFacade.standardNormalCdf(beta) - StatsFacade.standardNormalCdf(alpha)
                if (z <= 1e-12) {
                    val delta = beta - alpha
                    val d1 = 0.5 * (alpha + beta)
                    val d2 = (1.0 - delta * delta / 12.0).coerceIn(0.0, MAX_DELTA2)
                    d1 to d2
                } else {
                    val phiA = StatsFacade.standardNormalPdf(alpha)
                    val phiB = StatsFacade.standardNormalPdf(beta)
                    val d1 = (phiA - phiB) / z
                    val d2 = (d1 * d1 - (alpha * phiA - beta * phiB) / z).coerceIn(0.0, MAX_DELTA2)
                    d1 to d2
                }
            }
        }

    private fun perfectRightTailMoments(
        alpha: Double,
        beta: Double,
    ): Pair<Double, Double> {
        val delta = beta - alpha
        val ratioExp = exp(-0.5 * delta * (alpha + beta))
        val (mAlpha, mAlphaMinus) = StatsFacade.upperTailMillsRatio(alpha)
        val (mBeta, _) = StatsFacade.upperTailMillsRatio(beta)
        val denom = 1.0 / mAlpha - ratioExp / mBeta
        return when {
            ratioExp < 1e-15 -> {
                mAlpha to (mAlpha * mAlphaMinus).coerceIn(0.0, MAX_DELTA2)
            }

            denom <= 1e-12 -> {
                val d1 = 0.5 * (alpha + beta)
                val d2 = (1.0 - delta * delta / 12.0).coerceIn(0.0, MAX_DELTA2)
                d1 to d2
            }

            else -> {
                val d1 = (1.0 - ratioExp) / denom
                val bracket = (alpha - beta * ratioExp) / denom
                val d2 = (d1 * d1 - bracket).coerceIn(0.0, MAX_DELTA2)
                d1 to d2
            }
        }
    }
}
