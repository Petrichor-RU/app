package nl.petrichor.app.data.recommendation

import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingSupertype
import nl.petrichor.app.data.wardrobe.ClothingType
import nl.petrichor.app.data.wardrobe.Material

enum class CoverageZone {
    HEAD,
    UPPER,
    LOWER,
    FEET,

    // Display-only group zones (plan section 6.5): a full-body piece forms its own group and
    // accessories are shown separately. Neither carries warmth on its own.
    FULL_BODY,
    ACCESSORIES,
}

/** Display group a piece belongs to, derived from its type's supertype. */
fun groupZoneFor(supertype: ClothingSupertype): CoverageZone =
    when (supertype) {
        ClothingSupertype.HEADWEAR -> CoverageZone.HEAD
        ClothingSupertype.UPPER_BODY -> CoverageZone.UPPER
        ClothingSupertype.LOWER_BODY -> CoverageZone.LOWER
        ClothingSupertype.FULL_BODY -> CoverageZone.FULL_BODY
        ClothingSupertype.FOOTWEAR -> CoverageZone.FEET
        ClothingSupertype.ACCESSORIES -> CoverageZone.ACCESSORIES
    }

/** Fixed display order and labels for outfit groups (plan section 6.5). */
val groupDisplayOrder: List<CoverageZone> =
    listOf(
        CoverageZone.HEAD,
        CoverageZone.UPPER,
        CoverageZone.LOWER,
        CoverageZone.FULL_BODY,
        CoverageZone.FEET,
        CoverageZone.ACCESSORIES,
    )

fun groupLabel(zone: CoverageZone): String =
    when (zone) {
        CoverageZone.HEAD -> "Headwear"
        CoverageZone.UPPER -> "Upper body"
        CoverageZone.LOWER -> "Lower body"
        CoverageZone.FULL_BODY -> "Full body"
        CoverageZone.FEET -> "Footwear"
        CoverageZone.ACCESSORIES -> "Accessories"
    }

/**
 * Warmth encoding for pieces, zones and outfit feature vectors (plan section 6.2).
 *
 * All warmth weights below are tunable constants; they live in this one file on purpose.
 */
object WarmthEncoding {
    // Material warmth W_M.
    val materialWarmth: Map<Material, Double> =
        mapOf(
            Material.WOOL to 1.00,
            Material.LEATHER to 0.80,
            Material.COTTON to 0.60,
            Material.POLYESTER to 0.50,
            Material.MIX to 0.50,
            Material.UNKNOWN to 0.50,
            Material.SILK to 0.40,
            Material.LINEN to 0.20,
        )

    // Type warmth W_T for the built-in types, keyed by type name.
    val typeWarmthByName: Map<String, Double> =
        mapOf(
            "Jackets" to 1.00,
            "Hoodies" to 0.85,
            "Sweaters" to 0.75,
            "Jeans" to 0.60,
            "Pants" to 0.55,
            "Shirts" to 0.40,
            "T-Shirts" to 0.30,
            "Dresses" to 0.90,
            "Sneakers" to 0.30,
            "Caps" to 0.30,
        )

    // Fallback W_T for custom types, per supertype.
    val supertypeFallbackWarmth: Map<ClothingSupertype, Double> =
        mapOf(
            ClothingSupertype.UPPER_BODY to 0.50,
            ClothingSupertype.LOWER_BODY to 0.50,
            ClothingSupertype.FULL_BODY to 0.80,
            ClothingSupertype.FOOTWEAR to 0.30,
            ClothingSupertype.HEADWEAR to 0.30,
            ClothingSupertype.ACCESSORIES to 0.0,
        )

    /** Zones a piece covers, derived from its type's supertype. A FULL_BODY piece covers UPPER and LOWER as one unit. */
    fun zonesFor(supertype: ClothingSupertype): Set<CoverageZone> =
        when (supertype) {
            ClothingSupertype.HEADWEAR -> setOf(CoverageZone.HEAD)
            ClothingSupertype.UPPER_BODY -> setOf(CoverageZone.UPPER)
            ClothingSupertype.LOWER_BODY -> setOf(CoverageZone.LOWER)
            ClothingSupertype.FULL_BODY -> setOf(CoverageZone.UPPER, CoverageZone.LOWER)
            ClothingSupertype.FOOTWEAR -> setOf(CoverageZone.FEET)
            ClothingSupertype.ACCESSORIES -> emptySet()
        }

    fun typeWarmth(type: ClothingType?): Double {
        if (type == null) return 0.0
        return typeWarmthByName[type.name] ?: supertypeFallbackWarmth[type.supertype] ?: 0.0
    }

    /** Per-piece warmth: W_M(material) * W_T(type name). */
    fun warmth(
        piece: ClothingPiece,
        type: ClothingType?,
    ): Double = (materialWarmth[piece.material] ?: 0.5) * typeWarmth(type)

    /** Sum of warmth over the pieces covering [zone]. */
    fun zoneWarmth(
        pieces: List<ClothingPiece>,
        typeById: Map<Long, ClothingType>,
        zone: CoverageZone,
    ): Double =
        pieces.sumOf { piece ->
            val type = typeById[piece.typeId]
            if (type != null && zone in zonesFor(type.supertype)) warmth(piece, type) else 0.0
        }

    /** Number of pieces covering [zone] (captures stacks like two t-shirts and a sweater). */
    fun layerCount(
        pieces: List<ClothingPiece>,
        typeById: Map<Long, ClothingType>,
        zone: CoverageZone,
    ): Int =
        pieces.count { piece ->
            val type = typeById[piece.typeId]
            type != null && zone in zonesFor(type.supertype)
        }

    /**
     * Outfit feature vector used for training, prediction and the heuristic:
     * x = [1.0, tempC, warmthUpper, warmthLower, warmthHead, warmthFeet, layerCountUpper, isRainy]
     */
    fun featureVector(
        tempC: Double,
        isRainy: Boolean,
        pieces: List<ClothingPiece>,
        typeById: Map<Long, ClothingType>,
    ): DoubleArray =
        doubleArrayOf(
            1.0,
            tempC,
            zoneWarmth(pieces, typeById, CoverageZone.UPPER),
            zoneWarmth(pieces, typeById, CoverageZone.LOWER),
            zoneWarmth(pieces, typeById, CoverageZone.HEAD),
            zoneWarmth(pieces, typeById, CoverageZone.FEET),
            layerCount(pieces, typeById, CoverageZone.UPPER).toDouble(),
            if (isRainy) 1.0 else 0.0,
        )
}
