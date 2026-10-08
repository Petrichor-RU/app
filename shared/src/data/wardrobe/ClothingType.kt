package nl.petrichor.app.data.wardrobe

/**
 * Clothing types following KISS principle - limited set of common categories
 */
enum class ClothingType(val displayName: String) {
    T_SHIRT("T-Shirt"),
    JEANS("Jeans"),
    TROUSERS("Trousers"),
    SHIRT("Shirt"),
    JACKET("Jacket"),
    DRESS("Dress"),
    SHORTS("Shorts"),
    SWEATER("Sweater");
}