package nl.petrichor.app.data.recommendation

import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingSupertype
import nl.petrichor.app.data.wardrobe.ClothingType
import nl.petrichor.app.data.wardrobe.Material
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WarmthEncodingTest {
    private val teeType = ClothingType(id = 10, name = "T-Shirts", supertype = ClothingSupertype.UPPER_BODY)
    private val jacketType = ClothingType(id = 11, name = "Jackets", supertype = ClothingSupertype.UPPER_BODY)
    private val jeansType = ClothingType(id = 12, name = "Jeans", supertype = ClothingSupertype.LOWER_BODY)
    private val dressType = ClothingType(id = 13, name = "Dresses", supertype = ClothingSupertype.FULL_BODY)
    private val capType = ClothingType(id = 14, name = "Caps", supertype = ClothingSupertype.HEADWEAR)
    private val sneakerType = ClothingType(id = 15, name = "Sneakers", supertype = ClothingSupertype.FOOTWEAR)
    private val scarfType = ClothingType(id = 16, name = "Scarfs", supertype = ClothingSupertype.ACCESSORIES)

    private val typeById = listOf(teeType, jacketType, jeansType, dressType, capType, sneakerType, scarfType).associateBy { it.id }

    private fun piece(
        id: Long,
        typeId: Long,
        material: Material = Material.UNKNOWN,
    ) = ClothingPiece(id = id, name = "p$id", typeId = typeId, material = material, notes = null, imagePath = "", thumbnailPath = "")

    @Test
    fun `piece warmth is material warmth times type warmth`() {
        val cottonTee = piece(1, teeType.id, Material.COTTON)
        // COTTON 0.60 * T-Shirts 0.30
        assertEquals(0.18, WarmthEncoding.warmth(cottonTee, teeType), 1e-9)
        val woolJacket = piece(2, jacketType.id, Material.WOOL)
        // WOOL 1.00 * Jackets 1.00
        assertEquals(1.0, WarmthEncoding.warmth(woolJacket, jacketType), 1e-9)
    }

    @Test
    fun `unknown type contributes zero warmth`() {
        assertEquals(0.0, WarmthEncoding.warmth(piece(3, 999), null), 1e-9)
    }

    @Test
    fun `zones for supertypes map as documented`() {
        assertEquals(setOf(CoverageZone.HEAD), WarmthEncoding.zonesFor(ClothingSupertype.HEADWEAR))
        assertEquals(setOf(CoverageZone.UPPER), WarmthEncoding.zonesFor(ClothingSupertype.UPPER_BODY))
        assertEquals(setOf(CoverageZone.LOWER), WarmthEncoding.zonesFor(ClothingSupertype.LOWER_BODY))
        assertEquals(setOf(CoverageZone.UPPER, CoverageZone.LOWER), WarmthEncoding.zonesFor(ClothingSupertype.FULL_BODY))
        assertEquals(setOf(CoverageZone.FEET), WarmthEncoding.zonesFor(ClothingSupertype.FOOTWEAR))
        assertTrue(WarmthEncoding.zonesFor(ClothingSupertype.ACCESSORIES).isEmpty())
    }

    @Test
    fun `zone warmth sums the pieces covering the zone and full body covers both`() {
        val tee = piece(1, teeType.id, Material.COTTON) // 0.18
        val jacket = piece(2, jacketType.id, Material.WOOL) // 1.0
        val jeans = piece(3, jeansType.id, Material.COTTON) // 0.6 * 0.6 = 0.36
        val dress = piece(4, dressType.id, Material.SILK) // 0.4 * 0.9 = 0.36, covers UPPER and LOWER

        assertEquals(1.18, WarmthEncoding.zoneWarmth(listOf(tee, jacket, jeans), typeById, CoverageZone.UPPER), 1e-9)
        assertEquals(0.36, WarmthEncoding.zoneWarmth(listOf(tee, jacket, jeans), typeById, CoverageZone.LOWER), 1e-9)
        assertEquals(
            0.72,
            WarmthEncoding.zoneWarmth(listOf(dress), typeById, CoverageZone.UPPER) +
                WarmthEncoding.zoneWarmth(listOf(dress), typeById, CoverageZone.LOWER),
            1e-9,
        )
        assertEquals(2, WarmthEncoding.layerCount(listOf(tee, jacket), typeById, CoverageZone.UPPER))
    }

    @Test
    fun `feature vector has bias first and eight features`() {
        val pieces = listOf(piece(1, teeType.id, Material.COTTON), piece(3, jeansType.id, Material.COTTON))
        val features = WarmthEncoding.featureVector(15.0, true, pieces, typeById)
        assertEquals(8, features.size)
        assertEquals(1.0, features[0], 1e-9)
        assertEquals(15.0, features[1], 1e-9)
        assertEquals(0.18, features[2], 1e-9) // upper warmth
        assertEquals(0.36, features[3], 1e-9) // lower warmth
        assertEquals(1.0, features[7], 1e-9) // rainy
    }

    @Test
    fun `group zones and labels follow the fixed display order`() {
        assertEquals(CoverageZone.FULL_BODY, groupZoneFor(ClothingSupertype.FULL_BODY))
        assertEquals(CoverageZone.ACCESSORIES, groupZoneFor(ClothingSupertype.ACCESSORIES))
        assertEquals("Full body", groupLabel(CoverageZone.FULL_BODY))
        assertEquals(
            listOf(
                CoverageZone.HEAD,
                CoverageZone.UPPER,
                CoverageZone.LOWER,
                CoverageZone.FULL_BODY,
                CoverageZone.FEET,
                CoverageZone.ACCESSORIES,
            ),
            groupDisplayOrder,
        )
    }
}
