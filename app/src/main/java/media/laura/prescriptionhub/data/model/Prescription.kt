package media.laura.prescriptionhub.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a user prescription.
 *
 * @param id Unique identifier (auto-generated primary key).
 * @param name The name of the medication or prescription.
 * @param color Color representation (32-bit ARGB Long value such as 0xFF4CAF50).
 * @param dosis Dosage description (e.g., "500mg", "1 pill", "10ml").
 * @param schedule Frequency and time settings for taking this prescription.
 */
@Entity(tableName = "prescriptions")
data class Prescription(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val color: Long,
    val dosis: String,
    @Embedded
    val schedule: Schedule = Schedule()
)
