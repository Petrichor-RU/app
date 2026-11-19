package nl.petrichor.app.data.recommendation

import org.jetbrains.kotlinx.multik.ndarray.data.D2Array
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Constrained Thompson sampler over posterior Cholesky draws (`theta = m + L * z`) plus unseen
 * garment prior variance, estimating empirical selection propensity via Monte Carlo draws for
 * offline Inverse Propensity Scoring (Li et al., 2010).
 */
internal object ThompsonSampler {
    private const val PROPENSITY_MC_DRAWS = 64
    private const val ALPHA_THERMAL_WEIGHT = 0.45
    private const val RATING_SPAN = 4.0

    fun sampleSafeSlot3(
        model: DecodedModel,
        safePool: List<EvaluatedCandidate>,
        random: Random,
    ): Pair<EvaluatedCandidate, Double> {
        if (safePool.size == 1) return safePool.first() to 1.0

        val ratingChol = MultikOps.choleskyLower(model.ratingPosterior.covariance)
        val thermalChol = MultikOps.choleskyLower(model.thermalPosterior.covariance)
        val unseenPieceIndex =
            safePool
                .flatMap { it.unseenPieceIds }
                .distinct()
                .sorted()
                .withIndex()
                .associate { (idx, id) -> id to idx }

        val chosen = drawWinner(model, safePool, ratingChol, thermalChol, unseenPieceIndex, random)
        val propensityRandom = Random(random.nextLong())
        var wins = 1
        repeat(PROPENSITY_MC_DRAWS) {
            val winner = drawWinner(model, safePool, ratingChol, thermalChol, unseenPieceIndex, propensityRandom)
            if (winner === chosen) wins++
        }
        val empiricalPropensity = (wins.toDouble() / (PROPENSITY_MC_DRAWS + 1)).coerceIn(0.0, 1.0)
        return chosen to empiricalPropensity
    }

    @Suppress("LongParameterList") // Cholesky factors & shared unseen index avoid repeated allocations in MC loop
    private fun drawWinner(
        model: DecodedModel,
        safePool: List<EvaluatedCandidate>,
        ratingChol: D2Array<Double>,
        thermalChol: D2Array<Double>,
        unseenPieceIndex: Map<Long, Int>,
        random: Random,
    ): EvaluatedCandidate {
        val thetaRating = MultikOps.samplePosteriorVector(model.ratingPosterior.mean, ratingChol, random)
        val thetaThermal = MultikOps.samplePosteriorVector(model.thermalPosterior.mean, thermalChol, random)
        val unseenCount = unseenPieceIndex.size
        val unseenRatingOffsets = samplePriorOffsets(unseenCount, BayesianRatingModel.GARMENT_PRIOR_VARIANCE, random)
        val unseenThermalOffsets = samplePriorOffsets(unseenCount, BayesianThermalModel.GARMENT_PRIOR_VARIANCE, random)

        return safePool.maxBy { eval ->
            sampleUtility(
                model = model,
                eval = eval,
                thetaRating = thetaRating,
                thetaThermal = thetaThermal,
                unseenPieceIndex = unseenPieceIndex,
                unseenRatingOffsets = unseenRatingOffsets,
                unseenThermalOffsets = unseenThermalOffsets,
            )
        }
    }

    private fun samplePriorOffsets(
        count: Int,
        priorVariance: Double,
        random: Random,
    ): DoubleArray {
        if (count <= 0) return DoubleArray(0)
        val std = sqrt(priorVariance)
        return StatsFacade.sampleStandardNormal(count, random).apply {
            for (i in indices) this[i] *= std
        }
    }

    @Suppress("LongParameterList") // linear evaluation over sampled parameter vectors
    private fun sampleUtility(
        model: DecodedModel,
        eval: EvaluatedCandidate,
        thetaRating: DoubleArray,
        thetaThermal: DoubleArray,
        unseenPieceIndex: Map<Long, Int>,
        unseenRatingOffsets: DoubleArray,
        unseenThermalOffsets: DoubleArray,
    ): Double {
        val raw = eval.raw
        val features = eval.features
        if (raw == null || features == null) return eval.baseScore

        var unseenRatingSum = 0.0
        var unseenThermalSum = 0.0
        for (pieceId in eval.unseenPieceIds) {
            val slot = unseenPieceIndex[pieceId] ?: continue
            unseenRatingSum += unseenRatingOffsets[slot]
            unseenThermalSum += unseenThermalOffsets[slot]
        }

        val sampledRating =
            (
                RidgeRatingModel.priorPrediction(raw) +
                    dotArrays(features, thetaRating) +
                    unseenRatingSum
            ).coerceIn(1.0, 5.0)
        val sampledLatent =
            BayesianThermalModel.priorSensation(raw) +
                dotArrays(features, thetaThermal) +
                unseenThermalSum
        val tau = model.thermalPosterior.thresholdTau
        val pPerfect =
            (
                StatsFacade.standardNormalCdf(tau - sampledLatent) -
                    StatsFacade.standardNormalCdf(-tau - sampledLatent)
            ).coerceIn(0.0, 1.0)
        val normalisedRating = ((sampledRating - 1.0) / RATING_SPAN).coerceIn(0.0, 1.0)
        val unitUtility = ALPHA_THERMAL_WEIGHT * pPerfect + (1.0 - ALPHA_THERMAL_WEIGHT) * normalisedRating
        return (1.0 + RATING_SPAN * unitUtility) - eval.tempPenalty
    }

    private fun dotArrays(
        left: DoubleArray,
        right: DoubleArray,
    ): Double {
        var acc = 0.0
        for (i in left.indices) {
            acc += left[i] * right[i]
        }
        return acc
    }
}
