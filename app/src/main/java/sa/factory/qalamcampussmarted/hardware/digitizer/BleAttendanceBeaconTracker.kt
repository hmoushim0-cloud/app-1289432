package sa.factory.qalamcampussmarted.hardware.digitizer

import kotlin.math.pow

/**
 * Attendance Verification Status
 * Strict Invariant: Zero Unicode Emojis.
 */
enum class AttendanceStatus {
    VERIFIED_PRESENT,
    PROXIMITY_MARGINAL,
    OUT_OF_CLASSROOM_BOUNDS,
    NO_BEACON_SIGNAL
}

/**
 * BLE Beacon Verification Report
 */
data class BeaconAttendanceResult(
    val studentId: String,
    val classroomBeaconId: String,
    val smoothedRssi: Double,
    val estimatedDistanceMeters: Double,
    val status: AttendanceStatus,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Autonomous BLE Classroom Attendance Proximity Tracker
 * Applies Log-Distance Path Loss (LDPL) modeling to evaluate student physical presence.
 */
class BleAttendanceBeaconTracker(
    private val measuredPowerAt1m: Int = -59,
    private val pathLossExponent: Double = 2.2,
    private val maxClassroomRadiusMeters: Double = 4.5
) {
    /**
     * Estimates physical distance in meters from received signal strength indicator (RSSI).
     */
    fun estimateDistanceMeters(rssiDbm: Int): Double {
        if (rssiDbm >= 0) return 0.0
        val ratio = (measuredPowerAt1m - rssiDbm) / (10.0 * pathLossExponent)
        val distance = 10.0.pow(ratio)
        return Math.round(distance * 100.0) / 100.0
    }

    /**
     * Evaluates attendance state based on student BLE badge and classroom beacon telemetry.
     */
    fun evaluateAttendance(
        studentId: String,
        classroomBeaconId: String,
        rssiDbm: Int
    ): BeaconAttendanceResult {
        if (rssiDbm <= -100) {
            return BeaconAttendanceResult(
                studentId = studentId,
                classroomBeaconId = classroomBeaconId,
                smoothedRssi = rssiDbm.toDouble(),
                estimatedDistanceMeters = 999.0,
                status = AttendanceStatus.NO_BEACON_SIGNAL
            )
        }

        val distance = estimateDistanceMeters(rssiDbm)
        val status = when {
            distance <= maxClassroomRadiusMeters -> AttendanceStatus.VERIFIED_PRESENT
            distance <= maxClassroomRadiusMeters * 1.5 -> AttendanceStatus.PROXIMITY_MARGINAL
            else -> AttendanceStatus.OUT_OF_CLASSROOM_BOUNDS
        }

        return BeaconAttendanceResult(
            studentId = studentId,
            classroomBeaconId = classroomBeaconId,
            smoothedRssi = rssiDbm.toDouble(),
            estimatedDistanceMeters = distance,
            status = status
        )
    }
}
