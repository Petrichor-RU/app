package nl.petrichor.app.data.outfit

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.Junction
import androidx.room3.PrimaryKey
import androidx.room3.Relation
import kotlinx.datetime.LocalDate
import nl.petrichor.app.data.wardrobe.ClothingPiece

enum class WarmthVerdict {
    TOO_COLD,
    PERFECT,
    TOO_WARM,
}

@Entity(tableName = "outfit_entries")
data class OutfitEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wornOnDate: LocalDate, // stored as ISO "yyyy-MM-dd" via PetrichorConverters
    val photoPath: String?, // optional selfie, stored via MediaStorage
    val notes: String?,
    // weather snapshot at logging time (nullable: older manual entries may have no data)
    val temperatureC: Double?,
    val feelsLikeC: Double?,
    val minTempC: Double?,
    val maxTempC: Double?,
    val weatherDescription: String?,
    val weatherIcon: String?,
    val isRainy: Boolean,
    // survey (both null until surveyed; both set together)
    val surveyFeelingScore: Int?, // 1..5
    val surveyWarmthVerdict: WarmthVerdict?,
    val surveyedAtEpochMs: Long?,
    // laundry (epoch ms until which this outfit is blocked; 0 = clean)
    val laundryUntilEpochMs: Long = 0,
    val createdAtEpochMs: Long = 0,
)

@Entity(
    tableName = "outfit_entry_pieces",
    primaryKeys = ["entryId", "pieceId"],
    foreignKeys = [
        ForeignKey(
            entity = OutfitEntry::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ClothingPiece::class,
            parentColumns = ["id"],
            childColumns = ["pieceId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("entryId"), Index("pieceId")],
)
data class OutfitEntryPiece(
    val entryId: Long,
    val pieceId: Long,
    val orderIndex: Int = 0,
)

data class OutfitEntryWithPieces(
    @Embedded val entry: OutfitEntry,
    @Relation(
        parentColumns = ["id"],
        entityColumns = ["id"],
        associateBy =
            Junction(
                value = OutfitEntryPiece::class,
                parentColumns = ["entryId"],
                entityColumns = ["pieceId"],
            ),
    )
    val pieces: List<ClothingPiece>,
)

@Entity(tableName = "model_state")
data class ModelState(
    @PrimaryKey val id: Long = 1, // singleton row
    val coefficientsJson: String, // kotlinx-serialization List<Double>
    val trainedAtEpochMs: Long,
    val sampleCount: Int,
)
