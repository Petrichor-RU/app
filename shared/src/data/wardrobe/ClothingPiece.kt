package nl.petrichor.app.data.wardrobe

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "clothing",
    foreignKeys = [
        ForeignKey(
            entity = ClothingType::class,
            parentColumns = ["id"],
            childColumns = ["typeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("typeId")],
)
data class ClothingPiece(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String?,
    val typeId: Long,
    val material: Material = Material.UNKNOWN,
    val notes: String?,
    val imagePath: String,
    val thumbnailPath: String,
    val createdAt: Long = 0L,
    // Archived pieces stay linked to journal entries but disappear from the wardrobe,
    // pickers and recommendations.
    val archived: Boolean = false,
    // Laundry: epoch ms until which this piece is blocked; 0 = clean.
    val laundryUntilEpochMs: Long = 0,
)
