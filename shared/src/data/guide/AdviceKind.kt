package nl.petrichor.app.data.guide

import dev.catbit.material_symbols.MaterialSymbols

/**
 * One kind of dress advice the location guide can emit. Each kind has exactly one English
 * (British) template and one Material Symbols icon, plus a fixed [severity] rank used to order
 * advice rows coldest-weather first. No i18n for now.
 */
enum class AdviceKind(
    val template: String,
    val icon: String,
    val severity: Int,
) {
    THERMAL_BASE_LAYER("A thermal base layer is worth packing.", MaterialSymbols.SEVERE_COLD, 0),
    THICK_JACKET("Take a thick jacket with you.", MaterialSymbols.CHECKROOM, 1),
    WARM_ACCESSORIES("Bring a hat, scarf and gloves.", MaterialSymbols.SNOWING, 2),
    LIGHT_JACKET("A light jacket is usually enough.", MaterialSymbols.APPAREL, 3),
    LAYERED_CLOTHING("Wear layered clothing so you can adjust through the day.", MaterialSymbols.LAYERS, 5),
    WATERPROOF_FOOTWEAR("Waterproof footwear pays off this time of year.", MaterialSymbols.SNOWSHOEING, 6),
    RAIN_GEAR("Keep a rain jacket or compact umbrella at hand.", MaterialSymbols.UMBRELLA, 7),
    BREATHABLE_FABRICS("Stick to breathable fabrics such as linen or cotton.", MaterialSymbols.AIR, 8),
    SUN_PROTECTION("Sunscreen and a cap are recommended.", MaterialSymbols.SUNNY, 9),
    COLD_SNAP_RISK("Be prepared for occasional cold snaps.", MaterialSymbols.SNOWFLAKE, 10),
    HEAT_SPIKE_RISK("Be prepared for occasional heat spikes.", MaterialSymbols.HEAT, 11),
}
