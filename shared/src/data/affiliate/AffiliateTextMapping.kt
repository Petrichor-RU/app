package nl.petrichor.app.data.affiliate

import nl.petrichor.app.data.wardrobe.ClothingSupertype

private val typeRules: List<Pair<Regex, Pair<String, ClothingSupertype>>> =
    listOf(
        Regex("\\bhoodies?\\b|\\bhooded\\b") to ("Hoodies" to ClothingSupertype.UPPER_BODY),
        Regex("\\bsweatshirts?\\b|\\bsweaters?\\b|\\bjumpers?\\b|\\bpullovers?\\b|\\bfleece\\b") to
            ("Sweaters" to ClothingSupertype.UPPER_BODY),
        Regex("\\bjackets?\\b|\\bcoats?\\b|\\bblazers?\\b|\\bparkas?\\b|\\bwindbreakers?\\b|\\braincoats?\\b") to
            ("Jackets" to ClothingSupertype.UPPER_BODY),
        Regex("\\bt-?shirts?\\b|\\btees?\\b") to ("T-Shirts" to ClothingSupertype.UPPER_BODY),
        Regex("\\bshirts?\\b|\\bblouses?\\b") to ("Shirts" to ClothingSupertype.UPPER_BODY),
        // Shorts before jeans so "denim shorts" maps to Shorts, not Jeans.
        Regex("\\bshorts?\\b") to ("Shorts" to ClothingSupertype.LOWER_BODY),
        Regex("\\bjeans?\\b|\\bdenim\\b") to ("Jeans" to ClothingSupertype.LOWER_BODY),
        Regex("\\bpants?\\b|\\btrousers?\\b|\\bchinos?\\b|\\bleggings?\\b") to ("Pants" to ClothingSupertype.LOWER_BODY),
        Regex("\\bdresses?\\b|\\bgowns?\\b") to ("Dresses" to ClothingSupertype.FULL_BODY),
        Regex("\\bsneakers?\\b|\\bshoes?\\b|\\bboots?\\b|\\btrainers?\\b") to ("Sneakers" to ClothingSupertype.FOOTWEAR),
        Regex("\\bcaps?\\b|\\bbeanies?\\b|\\bhats?\\b") to ("Caps" to ClothingSupertype.HEADWEAR),
        Regex("\\bties?\\b|\\bscarves?\\b|\\bbelts?\\b") to ("Ties" to ClothingSupertype.ACCESSORIES),
    )

/**
 * Maps free store text (product title/description) onto a built-in clothing type
 * (name + supertype). An item that cannot be mapped returns null and MUST be dropped.
 */
internal fun mapToBuiltInType(text: String): Pair<String, ClothingSupertype>? {
    val lower = text.lowercase()
    return typeRules.firstOrNull { (regex, _) -> regex.containsMatchIn(lower) }?.second
}

/** Currency code → symbol for price display; falls back to the code itself. */
internal fun currencySymbol(currencyCode: String?): String =
    when (currencyCode?.uppercase()) {
        "INR" -> "₹"
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        else -> currencyCode?.uppercase() ?: ""
    }

/** Formats an API price amount without a trailing ".0" for whole numbers. */
internal fun Double.formatPlain(): String = if (this == kotlin.math.floor(this)) toLong().toString() else toString()
