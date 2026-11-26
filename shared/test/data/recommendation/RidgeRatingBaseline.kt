package nl.petrichor.app.data.recommendation

import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingType
import org.jetbrains.kotlinx.multik.api.linalg.dot
import org.jetbrains.kotlinx.multik.api.linalg.solve
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.api.zeros
import org.jetbrains.kotlinx.multik.ndarray.data.D1Array
import org.jetbrains.kotlinx.multik.ndarray.data.D2Array
import org.jetbrains.kotlinx.multik.ndarray.data.get
import org.jetbrains.kotlinx.multik.ndarray.data.set

/**
 * Batch ridge baseline retained strictly in test code for ablation comparisons in
 * [EvaluationHarness] and zero-drift Kalman equivalence checks in [BayesianComfortModelTest].
 */
internal object RidgeRatingBaseline {
    const val DEFAULT_PRIOR_PRECISION = 1.0
    private const val DEFAULT_SOLVE_JITTER = 1e-8

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

    fun fitWithDiagonalPrecision(
        samples: List<Pair<RawOutfitSignals, Double>>,
        precisionDiagonal: DoubleArray,
    ): RecommendationModelArtifact {
        require(samples.isNotEmpty()) { "Cannot fit a ridge model without samples" }
        val d = FeatureExtractor.CORE_FEATURE_COUNT
        require(precisionDiagonal.size == d) { "Expected $d precision entries" }
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
                target - BayesianRatingModel.priorPrediction(signals)
            }

        val rVector = mk.ndarray(residuals)
        val xt = xMatrix.transpose()
        val normalMatrix = mk.linalg.dot(xt, xMatrix)
        for (i in 0 until d) {
            normalMatrix[i, i] = normalMatrix[i, i] + precisionDiagonal[i]
        }
        val rhs = mk.linalg.dot(xt, rVector)
        val beta = solveLinearSystem(normalMatrix, rhs)

        return RecommendationModelArtifact(
            featureSchemaVersion = FeatureExtractor.FEATURE_SCHEMA_VERSION,
            scaler = scaler,
            ratingMean = List(d) { idx -> beta[idx] },
        )
    }

    fun predict(
        model: DecodedModel,
        tempC: Double,
        isRainy: Boolean,
        pieces: List<ClothingPiece>,
        typeById: Map<Long, ClothingType>,
    ): Double {
        val raw = FeatureExtractor.extractRaw(tempC, isRainy, pieces, typeById)
        val features = FeatureExtractor.buildCoreFeatures(raw, model.scaler)
        val mean = model.ratingPosterior.mean
        val coreWeights =
            if (mean.size == FeatureExtractor.CORE_FEATURE_COUNT) {
                mean
            } else {
                mk.ndarray(DoubleArray(FeatureExtractor.CORE_FEATURE_COUNT) { idx -> mean[idx] })
            }
        return (BayesianRatingModel.priorPrediction(raw) + mk.linalg.dot(coreWeights, mk.ndarray(features))).coerceIn(1.0, 5.0)
    }

    fun predict(
        artifact: RecommendationModelArtifact,
        raw: RawOutfitSignals,
    ): Double {
        val features = FeatureExtractor.buildCoreFeatures(raw, artifact.scaler)
        val coreWeights = mk.ndarray(DoubleArray(FeatureExtractor.CORE_FEATURE_COUNT) { idx -> artifact.ratingMean[idx] })
        return (BayesianRatingModel.priorPrediction(raw) + mk.linalg.dot(coreWeights, mk.ndarray(features))).coerceIn(1.0, 5.0)
    }

    fun solveLinearSystem(
        matrix: D2Array<Double>,
        rhs: D1Array<Double>,
        jitter: Double = DEFAULT_SOLVE_JITTER,
    ): D1Array<Double> {
        val direct = runCatching { mk.linalg.solve(matrix, rhs) }.getOrNull()
        if (direct != null && (0 until direct.size).all { direct[it].isFinite() }) return direct
        val damped = matrix.copy()
        for (i in 0 until rhs.size) {
            damped[i, i] = damped[i, i] + jitter
        }
        return mk.linalg.solve(damped, rhs)
    }
}
