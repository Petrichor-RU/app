package nl.petrichor.app.data.recommendation

import org.jetbrains.kotlinx.multik.api.linalg.dot
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.zeros
import org.jetbrains.kotlinx.multik.ndarray.data.D1Array
import org.jetbrains.kotlinx.multik.ndarray.data.D2Array
import org.jetbrains.kotlinx.multik.ndarray.data.get
import org.jetbrains.kotlinx.multik.ndarray.data.set
import org.oremif.kstats.descriptive.OnlineStatistics
import org.oremif.kstats.distributions.NormalDistribution
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Thin internal adapter over `kstats` distributions/statistics and `multik-kotlin` covariance
 * operations, centralising divide-by-zero, tail-stability, and positive-definite safeguards.
 */
internal object StatsFacade {
    const val MIN_STD = 1e-3
    private const val TAIL_GUARD_Z = 6.0
    private const val MILLS_CF_DEPTH = 10
    private val standardNormal = NormalDistribution(0.0, 1.0)

    fun standardNormalPdf(z: Double): Double = standardNormal.pdf(z)

    fun standardNormalCdf(z: Double): Double = standardNormal.cdf(z)

    fun sampleStandardNormal(
        dimension: Int,
        random: Random,
    ): DoubleArray = standardNormal.sample(dimension, random)

    /**
     * Computes sample mean and standard deviation using `kstats` [OnlineStatistics].
     * Near-constant sequences (`size < 2` or `std < minStd`) clamp `std` to `1.0` so
     * standardisation centres constant features at `0.0` without scaling spikes.
     */
    fun meanAndStd(
        values: DoubleArray,
        minStd: Double = MIN_STD,
    ): Pair<Double, Double> =
        if (values.isEmpty()) {
            0.0 to 1.0
        } else {
            val stats = OnlineStatistics()
            stats.addAll(values)
            val mean = stats.mean
            val std =
                if (values.size < 2) {
                    1.0
                } else {
                    val rawStd = stats.standardDeviation()
                    if (rawStd.isFinite() && rawStd >= minStd) rawStd else 1.0
                }
            mean to std
        }

    /**
     * Inverse Mills ratio `m(z) = phi(z) / (1 - Phi(z))` paired with `(m(z) - z)` so that
     * truncated-Gaussian variance computations avoid `0 / 0` and catastrophic cancellation in
     * extreme upper tails (`z > 6`).
     */
    fun upperTailMillsRatio(z: Double): Pair<Double, Double> {
        if (z <= TAIL_GUARD_Z) {
            val tail = standardNormalCdf(-z)
            val m = if (tail > 0.0) standardNormalPdf(z) / tail else 0.0
            return m to (m - z)
        }
        // M(z) = z + 1 / (z + 2 / (z + 3 / ...)), so M(z) - z = 1 / denom after folding k = 10 downTo 2.
        var denom = z
        for (k in MILLS_CF_DEPTH downTo 2) {
            denom = z + k.toDouble() / denom
        }
        val mMinusZ = 1.0 / denom
        return (z + mMinusZ) to mMinusZ
    }

    fun quadForm(
        vector: D1Array<Double>,
        matrix: D2Array<Double>,
    ): Double = mk.linalg.dot(vector, mk.linalg.dot(matrix, vector))

    fun diagonalMatrix(diagonal: DoubleArray): D2Array<Double> {
        val size = diagonal.size
        val result = mk.zeros<Double>(size, size)
        for (i in 0 until size) {
            result[i, i] = diagonal[i]
        }
        return result
    }

    fun symmetriseAndFloor(
        matrix: D2Array<Double>,
        minDiagonal: Double = 1e-9,
    ): D2Array<Double> {
        val size = matrix.shape[0]
        val result = mk.zeros<Double>(size, size)
        for (i in 0 until size) {
            val diag = matrix[i, i]
            result[i, i] = if (diag.isFinite() && diag >= minDiagonal) diag else minDiagonal
            for (j in (i + 1) until size) {
                val avg = 0.5 * (matrix[i, j] + matrix[j, i])
                val safe = if (avg.isFinite()) avg else 0.0
                result[i, j] = safe
                result[j, i] = safe
            }
        }
        return result
    }

    /**
     * Computes a lower-triangular Cholesky factor `L` with `L * L^T ~ matrix` for symmetric
     * covariance matrices, adding a tiny floor on non-positive pivots for numerical safety.
     */
    fun choleskyLower(
        matrix: D2Array<Double>,
        minPivot: Double = 1e-8,
    ): D2Array<Double> {
        val size = matrix.shape[0]
        val lower = mk.zeros<Double>(size, size)
        for (i in 0 until size) {
            for (j in 0 until i) {
                var sum = matrix[i, j]
                for (k in 0 until j) sum -= lower[i, k] * lower[j, k]
                val diag = lower[j, j]
                lower[i, j] = if (diag > 0.0 && sum.isFinite()) sum / diag else 0.0
            }
            var diagSum = matrix[i, i]
            for (k in 0 until i) diagSum -= lower[i, k] * lower[i, k]
            lower[i, i] = sqrt(if (diagSum.isFinite() && diagSum >= minPivot) diagSum else minPivot)
        }
        return lower
    }

    fun samplePosteriorVector(
        mean: D1Array<Double>,
        lowerCholesky: D2Array<Double>,
        random: Random,
    ): DoubleArray {
        val d = mean.size
        val z = sampleStandardNormal(d, random)
        return DoubleArray(d) { i ->
            var value = mean[i]
            for (j in 0..i) {
                value += lowerCholesky[i, j] * z[j]
            }
            value
        }
    }
}
