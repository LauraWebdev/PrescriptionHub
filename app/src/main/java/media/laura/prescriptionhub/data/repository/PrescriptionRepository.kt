package media.laura.prescriptionhub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import media.laura.prescriptionhub.data.local.PrescriptionDao
import media.laura.prescriptionhub.data.model.Prescription
import media.laura.prescriptionhub.data.model.ScheduledDose
import java.time.LocalDate

/**
 * Concrete implementation of [PrescriptionService] backed by Room [PrescriptionDao].
 */
class PrescriptionRepository(
    private val prescriptionDao: PrescriptionDao
) : PrescriptionService {

    override fun getAllPrescriptions(): Flow<List<Prescription>> {
        return prescriptionDao.getAllPrescriptions()
    }

    override fun getPrescriptionById(id: Long): Flow<Prescription?> {
        return prescriptionDao.getPrescriptionById(id)
    }

    override suspend fun getPrescription(id: Long): Prescription? {
        return prescriptionDao.getPrescriptionByIdOnce(id)
    }

    override suspend fun addPrescription(prescription: Prescription): Long {
        return prescriptionDao.insertPrescription(prescription)
    }

    override suspend fun updatePrescription(prescription: Prescription) {
        prescriptionDao.updatePrescription(prescription)
    }

    override suspend fun deletePrescription(prescription: Prescription) {
        prescriptionDao.deletePrescription(prescription)
    }

    override suspend fun deletePrescriptionById(id: Long) {
        prescriptionDao.deletePrescriptionById(id)
    }

    override suspend fun deleteAllPrescriptions() {
        prescriptionDao.deleteAllPrescriptions()
    }

    override fun getPrescriptionsForDate(date: LocalDate): Flow<List<Prescription>> {
        return prescriptionDao.getAllPrescriptions().map { list ->
            list.filter { prescription ->
                prescription.schedule.isScheduledOn(date)
            }
        }
    }

    override fun getScheduledDosesForDate(date: LocalDate): Flow<List<ScheduledDose>> {
        return prescriptionDao.getAllPrescriptions().map { list ->
            list.filter { prescription ->
                prescription.schedule.isScheduledOn(date)
            }.flatMap { prescription ->
                prescription.schedule.timesOfDay.map { time ->
                    ScheduledDose(prescription = prescription, time = time)
                }
            }.sorted()
        }
    }
}
