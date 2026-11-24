package nl.petrichor.app.data.recommendation

/**
 * Ordinary least squares with ridge regularization, solved via normal equations and
 * Gaussian elimination with partial pivoting. Pure common Kotlin, no ML library.
 *
 * The feature vector is defined in [WarmthEncoding.featureVector] (8 features, first one
 * is the bias term 1.0), so a fitted model has 8 coefficients.
 */
object LinearRegression {
    const val RIDGE_LAMBDA = 0.5

    /**
     * Fits coefficients for [samples] (feature vector to target). With fewer samples than
     * features or collinear inputs the ridge term keeps the system solvable and finite.
     */
    fun fit(
        samples: List<Pair<DoubleArray, Double>>,
        lambda: Double = RIDGE_LAMBDA,
    ): DoubleArray {
        require(samples.isNotEmpty()) { "Cannot fit a model without samples" }
        val n = samples.size
        val d = samples.first().first.size
        // Normal equations: (X^T X + lambda * I) beta = X^T y, but never regularize the bias term.
        val ata = Array(d) { DoubleArray(d) }
        val aty = DoubleArray(d)
        for ((x, y) in samples) {
            for (i in 0 until d) {
                aty[i] += x[i] * y
                for (j in 0 until d) {
                    ata[i][j] += x[i] * x[j]
                }
            }
        }
        for (i in 0 until d) {
            ata[i][i] += if (i == 0) 0.0 else lambda * n
        }
        return solve(ata, aty)
    }

    fun predict(
        coefficients: DoubleArray,
        features: DoubleArray,
    ): Double {
        require(coefficients.size == features.size) { "Coefficient/feature size mismatch" }
        return coefficients.indices.sumOf { coefficients[it] * features[it] }
    }

    /** Gaussian elimination with partial pivoting. */
    private fun solve(
        a: Array<DoubleArray>,
        b: DoubleArray,
    ): DoubleArray {
        val n = b.size
        // Augmented matrix rows: a[i] + b[i].
        val m =
            Array(n) { i ->
                DoubleArray(n + 1).also { row ->
                    for (j in 0 until n) row[j] = a[i][j]
                    row[n] = b[i]
                }
            }
        for (col in 0 until n) {
            var pivot = col
            for (row in col + 1 until n) {
                if (kotlin.math.abs(m[row][col]) > kotlin.math.abs(m[pivot][col])) pivot = row
            }
            m[col] = m[pivot].also { m[pivot] = m[col] }
            val pivotValue = m[col][col]
            if (kotlin.math.abs(pivotValue) < 1e-12) continue // ridge keeps this rare; leave 0
            for (row in 0 until n) {
                if (row == col) continue
                val factor = m[row][col] / pivotValue
                for (k in col..n) {
                    m[row][k] -= factor * m[col][k]
                }
            }
        }
        return DoubleArray(n) { row ->
            val pivotValue = m[row][row]
            if (kotlin.math.abs(pivotValue) < 1e-12) 0.0 else m[row][n] / pivotValue
        }
    }
}
