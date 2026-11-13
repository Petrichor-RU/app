package nl.petrichor.app.data.database

import androidx.room3.ColumnTypeConverter
import kotlinx.datetime.LocalDate
import nl.petrichor.app.data.outfit.WarmthVerdict
import nl.petrichor.app.data.wardrobe.ClothingSupertype
import nl.petrichor.app.data.wardrobe.Material

class PetrichorConverters {
    @ColumnTypeConverter
    fun materialToString(material: Material): String = material.name

    @ColumnTypeConverter
    fun stringToMaterial(value: String): Material = Material.entries.firstOrNull { it.name == value } ?: Material.UNKNOWN

    @ColumnTypeConverter
    fun supertypeToString(supertype: ClothingSupertype): String = supertype.name

    @ColumnTypeConverter
    fun stringToSupertype(value: String): ClothingSupertype =
        ClothingSupertype.entries.firstOrNull { it.name == value }
            ?: error("Unknown clothing supertype: $value")

    @ColumnTypeConverter
    fun warmthVerdictToString(verdict: WarmthVerdict): String = verdict.name

    @ColumnTypeConverter
    fun stringToWarmthVerdict(value: String): WarmthVerdict =
        WarmthVerdict.entries.firstOrNull { it.name == value }
            ?: error("Unknown warmth verdict: $value")

    // LocalDate is stored as ISO "yyyy-MM-dd", which sorts lexicographically, so
    // ORDER BY wornOnDate DESC and date-equality queries keep working unchanged.
    @ColumnTypeConverter
    fun localDateToString(date: LocalDate): String = date.toString()

    @ColumnTypeConverter
    fun stringToLocalDate(value: String): LocalDate = LocalDate.parse(value)
}
