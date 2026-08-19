package media.laura.prescriptionhub.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * Immutable snapshot of a prescription used for historically accurate checklist.
 *
 * A new snapshot is created each time a prescription is changed.
 */
@Entity(
    tableName = "prescription_snapshots",
    indices = [
        Index(value = ["prescriptionId"]),
        Index(value = ["validFrom"]),
        Index(value = ["validTo"])
    ]
)
data class PrescriptionSnapshot(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val prescriptionId: Long,
    val name: String,
    val color: Long,
    val dosis: String,
    @Embedded(prefix = "schedule_")
    val schedule: Schedule,
    val validFrom: LocalDateTime,
    val validTo: LocalDateTime? = null
) {
    companion object {
        fun fromPrescription(
            prescription: Prescription,
            validFrom: LocalDateTime
        ): PrescriptionSnapshot {
            return PrescriptionSnapshot(
                prescriptionId = prescription.id,
                name = prescription.name,
                color = prescription.color,
                dosis = prescription.dosis,
                schedule = prescription.schedule,
                validFrom = validFrom,
                validTo = null
            )
        }
    }
}
