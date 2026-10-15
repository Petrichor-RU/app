package nl.petrichor.app.data.wardrobe

import androidx.room.TypeConverter

class WardrobeConverters {
    @TypeConverter
    fun materialToString(material: Material): String = material.name

    @TypeConverter
    fun stringToMaterial(value: String): Material =
        Material.entries.firstOrNull { it.name == value } ?: Material.UNKNOWN

    @TypeConverter
    fun supertypeToString(supertype: ClothingSupertype): String = supertype.name

    @TypeConverter
    fun stringToSupertype(value: String): ClothingSupertype =
        ClothingSupertype.entries.firstOrNull { it.name == value }
            ?: error("Unknown clothing supertype: $value")
}
