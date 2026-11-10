package nl.petrichor.app.data.outfitLog

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "outfit_log_items",
    foreignKeys = [
        ForeignKey(
            entity = OutfitLog::class,
            parentColumns = ["id"],
            childColumns = ["outfitLogId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("outfitLogId"),
        Index(
            value = ["outfitLogId", "wardrobeClothingId"],
            unique = true
        )
    ]
)
data class OutfitLogItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val outfitLogId: Long,

    // Original ID from the wardrobe database.
    // Intentionally not a foreign key.
    val wardrobeClothingId: Long,

    // Historical snapshots
    val clothingNameSnapshot: String?,
    val clothingTypeSnapshot: String,
    val materialSnapshot: String,
    val notesSnapshot: String?
)