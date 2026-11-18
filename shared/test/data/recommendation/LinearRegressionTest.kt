package nl.petrichor.app.data.recommendation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LinearRegressionTest {
    @Test
    fun `fit recovers a linear relationship`() {
        // y = 2 + 3 * x1 with feature vectors [1, x1]; no ridge so the fit is exact
        val samples =
            listOf(
                doubleArrayOf(1.0, 0.0) to 2.0,
                doubleArrayOf(1.0, 1.0) to 5.0,
                doubleArrayOf(1.0, 2.0) to 8.0,
                doubleArrayOf(1.0, 3.0) to 11.0,
            )
        val coefficients = LinearRegression.fit(samples, lambda = 0.0)
        assertEquals(2, coefficients.size)
        assertEquals(2.0, coefficients[0], 1e-9)
        assertEquals(3.0, coefficients[1], 1e-9)
        assertEquals(6.5, LinearRegression.predict(coefficients, doubleArrayOf(1.0, 1.5)), 1e-9)
    }

    @Test
    fun `fit stays finite with fewer samples than features thanks to ridge`() {
        val samples = listOf(doubleArrayOf(1.0, 2.0, 3.0, 4.0) to 4.0)
        val coefficients = LinearRegression.fit(samples)
        assertTrue(coefficients.all { it.isFinite() })
    }

    @Test
    fun `fit rejects empty input`() {
        assertFailsWith<IllegalArgumentException> { LinearRegression.fit(emptyList()) }
    }

    @Test
    fun `predict rejects size mismatch`() {
        assertFailsWith<IllegalArgumentException> {
            LinearRegression.predict(doubleArrayOf(1.0, 2.0), doubleArrayOf(1.0))
        }
    }
}
