package nl.petrichor.app.data.wardrobe

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a clothing item in the wardrobe
 */
@Entity(tableName = "clothing")
data class ClothingPiece(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String?,
    val type: ClothingType,
    val material: String?,
    val notes: String?,
    val imagePath: String,
    val thumbnailPath: String,
    val createdAt: Long = 0L
)