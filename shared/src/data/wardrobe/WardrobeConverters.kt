package nl.petrichor.app.data.wardrobe

import androidx.room3.ColumnTypeConverter

class WardrobeConverters {
    @ColumnTypeConverter
    fun materialToString(material: Material): String = material.name

    @ColumnTypeConverter
    fun stringToMaterial(value: String): Material =
        Material.entries.firstOrNull { it.name == value } ?: Material.UNKNOWN

    @ColumnTypeConverter
    fun supertypeToString(supertype: ClothingSupertype): String = supertype.name

    @ColumnTypeConverter
    fun stringToSupertype(value: String): ClothingSupertype =
        ClothingSupertype.entries.firstOrNull { it.name == value }
            ?: error("Unknown clothing supertype: $value")
}
