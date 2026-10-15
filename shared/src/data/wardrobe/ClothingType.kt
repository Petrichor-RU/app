package nl.petrichor.app.data.wardrobe

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ClothingSupertype() {
    UPPER_BODY,
    LOWER_BODY,
    FULL_BODY,
    FOOTWEAR,
    HEADWEAR,
    ACCESSORIES
}

@Entity(
    tableName = "clothing_types",
    indices = [Index(value = ["supertype", "name"], unique = true)]
)
data class ClothingType(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val supertype: ClothingSupertype
)

object BuiltInClothingTypes {
    val all = listOf(
        ClothingType(name = "T-Shirts", supertype = ClothingSupertype.UPPER_BODY),
        ClothingType(name = "Hoodies", supertype = ClothingSupertype.UPPER_BODY),
        ClothingType(name = "Sweaters", supertype = ClothingSupertype.UPPER_BODY),
        ClothingType(name = "Shirts", supertype = ClothingSupertype.UPPER_BODY),
        ClothingType(name = "Jackets", supertype = ClothingSupertype.UPPER_BODY),
        ClothingType(name = "Jeans", supertype = ClothingSupertype.LOWER_BODY),
        ClothingType(name = "Pants", supertype = ClothingSupertype.LOWER_BODY),
        ClothingType(name = "Shorts", supertype = ClothingSupertype.LOWER_BODY),
        ClothingType(name = "Dresses", supertype = ClothingSupertype.FULL_BODY),
        ClothingType(name = "Sneakers", supertype = ClothingSupertype.FOOTWEAR),
        ClothingType(name = "Caps", supertype = ClothingSupertype.HEADWEAR),
        ClothingType(name = "Ties", supertype = ClothingSupertype.ACCESSORIES)
    )
}
