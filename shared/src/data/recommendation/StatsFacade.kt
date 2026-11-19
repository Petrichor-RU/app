package nl.petrichor.app.data.recommendation

import org.jetbrains.kotlinx.multik.api.linalg.dot
import org.jetbrains.kotlinx.multik.api.linalg.solve
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.ndarray.data.D1Array
import org.jetbrains.kotlinx.multik.ndarray.data.D2Array
import org.jetbrains.kotlinx.multik.ndarray.data.get
import org.jetbrains.kotlinx.multik.ndarray.data.set
import org.oremif.kstats.descriptive.OnlineStatistics
import org.oremif.kstats.distributions.NormalDistribution

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
}
