package nl.petrichor.app.data.wardrobe

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "clothing",
    foreignKeys = [
        ForeignKey(
            entity = ClothingType::class,
            parentColumns = ["id"],
            childColumns = ["typeId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("typeId")]
)
data class ClothingPiece(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String?,
    val typeId: Long,
    val material: Material = Material.UNKNOWN,
    val notes: String?,
    val imagePath: String,
    val thumbnailPath: String,
    val createdAt: Long = 0L
)
