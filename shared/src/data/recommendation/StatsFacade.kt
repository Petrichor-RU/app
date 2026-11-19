package nl.petrichor.app.data.recommendation

import org.jetbrains.kotlinx.multik.api.linalg.dot
import org.jetbrains.kotlinx.multik.api.linalg.solve
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
 * Thin internal adapter isolating `kstats` and `multik-kotlin` numerical primitives behind
 * stable signatures with divide-by-zero and near-singularity safeguards.
 */
internal object StatsFacade {
    const val MIN_STD = 1e-3
    private const val DEFAULT_SOLVE_JITTER = 1e-8
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

    fun dot(
        left: D1Array<Double>,
        right: D1Array<Double>,
    ): Double = mk.linalg.dot(left, right)

    fun matMul(
        left: D2Array<Double>,
        right: D2Array<Double>,
    ): D2Array<Double> = mk.linalg.dot(left, right)

    fun matVec(
        matrix: D2Array<Double>,
        vector: D1Array<Double>,
    ): D1Array<Double> = mk.linalg.dot(matrix, vector)

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

    /**
     * Solves `A * x = b` via Multik PLU factorisation, adding tiny diagonal jitter only if
     * the direct solve fails or produces non-finite entries.
     */
    fun solveLinearSystem(
        matrix: D2Array<Double>,
        rhs: D1Array<Double>,
        jitter: Double = DEFAULT_SOLVE_JITTER,
    ): D1Array<Double> {
        val direct = runCatching { mk.linalg.solve(matrix, rhs) }.getOrNull()
        if (direct != null && isFinite(direct)) return direct
        val size = rhs.size
        val damped = matrix.copy()
        for (i in 0 until size) {
            damped[i, i] = damped[i, i] + jitter
        }
        return mk.linalg.solve(damped, rhs)
    }

    private fun isFinite(vector: D1Array<Double>): Boolean {
        for (i in 0 until vector.size) {
            if (!vector[i].isFinite()) return false
        }
        return true
    }

    private const val TAIL_GUARD_Z = 6.0
    private const val MILLS_CF_DEPTH = 10
}

/** Focused Multik matrix helpers used by Bayesian posterior updates. */
internal object MultikOps {
    fun quadForm(
        vector: D1Array<Double>,
        matrix: D2Array<Double>,
    ): Double = StatsFacade.dot(vector, StatsFacade.matVec(matrix, vector))

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
            for (j in 0..i) {
                lower[i, j] = computeCholeskyEntry(matrix, lower, i, j, minPivot)
            }
        }
        return lower
    }

    private fun computeCholeskyEntry(
        matrix: D2Array<Double>,
        lower: D2Array<Double>,
        i: Int,
        j: Int,
        minPivot: Double,
    ): Double {
        var sum = matrix[i, j]
        for (k in 0 until j) {
            sum -= lower[i, k] * lower[j, k]
        }
        return if (i == j) {
            val safePivot = if (sum.isFinite() && sum >= minPivot) sum else minPivot
            sqrt(safePivot)
        } else {
            val diag = lower[j, j]
            if (diag > 0.0 && sum.isFinite()) sum / diag else 0.0
        }
    }

    fun samplePosteriorVector(
        mean: D1Array<Double>,
        lowerCholesky: D2Array<Double>,
        random: Random,
    ): DoubleArray {
        val d = mean.size
        val z = StatsFacade.sampleStandardNormal(d, random)
        return DoubleArray(d) { i ->
            var value = mean[i]
            for (j in 0..i) {
                value += lowerCholesky[i, j] * z[j]
            }
            value
        }
    }
}
