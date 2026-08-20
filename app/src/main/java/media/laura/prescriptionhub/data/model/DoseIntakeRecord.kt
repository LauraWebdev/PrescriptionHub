package media.laura.prescriptionhub.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Stores whether a specific scheduled dose was taken and when it was actually taken.
 */
@Entity(
    tableName = "dose_intake_records",
    foreignKeys = [
        ForeignKey(
            entity = PrescriptionSnapshot::class,
            parentColumns = ["id"],
            childColumns = ["snapshotId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["snapshotId"]),
        Index(value = ["snapshotId", "scheduledDate", "scheduledTime"], unique = true),
        Index(value = ["scheduledDate"])
    ]
)
data class DoseIntakeRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val snapshotId: Long,
    val scheduledDate: LocalDate,
    val scheduledTime: LocalTime,
    val taken: Boolean = false,
    val takenAt: LocalDateTime? = null
)
