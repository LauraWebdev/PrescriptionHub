package media.laura.prescriptionhub

import android.app.Application
import media.laura.prescriptionhub.data.local.PrescriptionDatabase
import media.laura.prescriptionhub.data.repository.PrescriptionRepository
import media.laura.prescriptionhub.data.repository.PrescriptionService

class PrescriptionHubApplication : Application() {

    val database: PrescriptionDatabase by lazy {
        PrescriptionDatabase.getDatabase(this)
    }

    val prescriptionService: PrescriptionService by lazy {
        PrescriptionRepository(database.prescriptionDao())
    }
}
