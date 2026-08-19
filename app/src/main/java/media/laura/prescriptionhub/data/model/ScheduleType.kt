package media.laura.prescriptionhub.data.model

/**
 * Defines the frequency/recurrence pattern for when a prescription is scheduled.
 */
enum class ScheduleType {
    DAILY,
    SPECIFIC_DAYS_OF_WEEK,
    EVERY_X_DAYS
}
