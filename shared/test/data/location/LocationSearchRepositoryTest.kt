package nl.petrichor.app.data.location

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocationSearchRepositoryTest {
    @Test
    fun `results sharing coordinates are deduplicated by key`() {
        // Searching "Galway" returns several entries at the same coordinates with different
        // name/region strings; each must still have a unique key for the LazyColumn.
        val body =
            """
            [
              {"name":"Galway","state":"New York","country":"United States","lat":43.0186858,"lon":-74.0315162},
              {"name":"Galway","country":"United States","lat":43.0186858,"lon":-74.0315162},
              {"name":"Galway","state":"New York","country":"US","lat":43.0186858,"lon":-74.0315162}
            ]
            """.trimIndent()

        val results = parseGeoResults(body)

        assertEquals(1, results.size)
        assertEquals("Galway, New York, United States", results.single().run { "$name, $region" })
        assertEquals(listOf("43.0186858,-74.0315162"), results.map { it.key }.distinct())
    }

    @Test
    fun `results at distinct coordinates are all kept`() {
        val body =
            """
            [
              {"name":"Galway","state":"Galway","country":"Ireland","lat":53.2707,"lon":-9.0568},
              {"name":"Galway","state":"New York","country":"United States","lat":43.0186858,"lon":-74.0315162}
            ]
            """.trimIndent()

        val results = parseGeoResults(body)

        assertEquals(2, results.size)
        assertTrue(results.map { it.key }.distinct().size == results.size)
    }
}
