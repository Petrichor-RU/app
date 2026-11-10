package nl.petrichor.app.data.outfitLog

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "outfit_logs",
    indices = [Index("loggedAt")]
)
data class OutfitLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val photoPath: String,

    val comfortRating: Int,

    val loggedAt: Long
)