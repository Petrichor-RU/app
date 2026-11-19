package nl.petrichor.app.data.recommendation

import org.jetbrains.kotlinx.multik.api.linalg.solve
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.ndarray.data.get
import org.oremif.kstats.descriptive.OnlineStatistics
import org.oremif.kstats.distributions.NormalDistribution
import kotlin.test.Test
import kotlin.test.assertEquals

class MathDependenciesTest {
    @Test
    fun `multik solves a two by two linear system`() {
        val matrix = mk.ndarray(mk[mk[2.0, 1.0], mk[1.0, 3.0]])
        val rhs = mk.ndarray(mk[5.0, 7.0])
        val solution = mk.linalg.solve(matrix, rhs)
        assertEquals(1.6, solution[0], 1e-9)
        assertEquals(1.8, solution[1], 1e-9)
    }

    @Test
    fun `kstats evaluates normal distribution and online statistics`() {
        val standardNormal = NormalDistribution(0.0, 1.0)
        assertEquals(0.5, standardNormal.cdf(0.0), 1e-12)

        val stats = OnlineStatistics()
        stats.addAll(doubleArrayOf(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0))
        assertEquals(5.0, stats.mean, 1e-12)
        assertEquals(4.571428571428571, stats.variance(), 1e-12)
    }
}
