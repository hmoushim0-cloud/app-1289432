package sa.factory.qalamcampussmarted.domain.academy

import sa.factory.qalamcampussmarted.hardware.digitizer.BleAttendanceBeaconFrame
import sa.factory.qalamcampussmarted.hardware.digitizer.BleProximityZone
import sa.factory.qalamcampussmarted.hardware.digitizer.StylusDigitizerPoint
import java.io.Serializable

/**
 * Saudi Tech Academy Bootcamp Tracks
 */
enum class BootcampTrack(
    val titleAr: String,
    val durationWeeks: Int,
    val baseTuitionSar: Double
) {
    AI_ENGINEERING("معسكر هندسة الذكاء الاصطناعي والنماذج اللغوية", 12, 14500.0),
    CYBERSECURITY("معسكر الأمن السيبراني واختبار الاختراق المتقدم", 10, 12800.0),
    FULLSTACK_MOBILE("معسكر تطوير تطبيقات الموبايل والأنظمة الموزعة", 8, 9500.0),
    CLOUD_DEVOPS("معسكر الحوسبة السحابية وهندسة البنية التحتية", 8, 10200.0)
}

/**
 * Student Attendance Lifecycle Status
 */
enum class AttendanceRecordStatus(val titleAr: String) {
    PRESENT_ON_TIME("حاضر في الوقت المحدد"),
    LATE_ATTENDANCE("حضور متأخر"),
    ABSENT_OUT_OF_RANGE("غياب: خارج نطاق القاعة الذكية"),
    BEACON_MISMATCH("خطأ في التحقق من قاعة المحاضرة")
}

/**
 * Student Academy Profile
 */
data class StudentProfile(
    val studentId: String,
    val fullNameAr: String,
    val nationalId: String,
    val track: BootcampTrack,
    val beaconDeviceId: String,
    var totalSessionsAttended: Int = 0,
    var totalLateCount: Int = 0,
    var totalAbsences: Int = 0
) : Serializable

/**
 * Active Classroom Lecture Session
 */
data class ClassroomSession(
    val sessionId: String,
    val track: BootcampTrack,
    val titleAr: String,
    val instructorNameAr: String,
    val roomCode: String,
    val beaconMajor: Int,
    val beaconMinor: Int,
    val startTimeMillis: Long,
    val endTimeMillis: Long
) : Serializable

/**
 * Attendance Verification Log Entry
 */
data class AttendanceEntry(
    val sessionId: String,
    val studentId: String,
    val timestamp: Long,
    val status: AttendanceRecordStatus,
    val verifiedDistanceMeters: Double,
    val isVerified: Boolean
) : Serializable

/**
 * Interactive Whiteboard / Tablet Stylus Stroke Representation
 */
data class StylusAnnotationStroke(
    val strokeId: String,
    val sessionId: String,
    val authorId: String,
    val colorHex: String,
    val pointCount: Int,
    val averagePressure: Double,
    val averageThicknessPt: Double,
    val durationMs: Long
) : Serializable

/**
 * ZATCA 15% VAT Compliant Bootcamp Tuition Invoice
 */
data class BootcampTuitionInvoice(
    val invoiceId: String,
    val studentId: String,
    val track: BootcampTrack,
    val baseTuitionSar: Double,
    val scholarshipDiscountSar: Double,
    val subtotalSar: Double,
    val vatSar: Double, // 15% ZATCA VAT
    val totalSar: Double,
    val issueTimestamp: Long = System.currentTimeMillis(),
    val isPaid: Boolean = false
) : Serializable

/**
 * Smart Tech Academy Classroom & Interactive Bootcamp Engine
 */
class SmartClassroomBootcampEngine {

    companion object {
        const val ZATCA_VAT_RATE = 0.15 // 15% Standard KSA VAT
        const val LATE_GRACE_MINUTES = 15 // 15 minutes grace period
        const val MAX_ATTENDANCE_DISTANCE_METERS = 3.5 // Within classroom radius
    }

    /**
     * Verifies student attendance using BLE beacon proximity and lecture session time window
     */
    fun verifyStudentAttendance(
        student: StudentProfile,
        session: ClassroomSession,
        beaconFrame: BleAttendanceBeaconFrame,
        checkinTimeMillis: Long
    ): AttendanceEntry {
        // Verify room beacon major matches session room
        if (beaconFrame.major != session.beaconMajor) {
            return AttendanceEntry(
                sessionId = session.sessionId,
                studentId = student.studentId,
                timestamp = checkinTimeMillis,
                status = AttendanceRecordStatus.BEACON_MISMATCH,
                verifiedDistanceMeters = beaconFrame.calculatedDistanceMeters,
                isVerified = false
            )
        }

        // Verify proximity zone is within classroom
        val isInClassroom = beaconFrame.calculatedDistanceMeters <= MAX_ATTENDANCE_DISTANCE_METERS &&
                (beaconFrame.proximityZone == BleProximityZone.IMMEDIATE || beaconFrame.proximityZone == BleProximityZone.NEAR)

        if (!isInClassroom) {
            student.totalAbsences++
            return AttendanceEntry(
                sessionId = session.sessionId,
                studentId = student.studentId,
                timestamp = checkinTimeMillis,
                status = AttendanceRecordStatus.ABSENT_OUT_OF_RANGE,
                verifiedDistanceMeters = beaconFrame.calculatedDistanceMeters,
                isVerified = false
            )
        }

        // Check if student arrived on time or late
        val lateThreshold = session.startTimeMillis + (LATE_GRACE_MINUTES * 60 * 1000L)
        val isLate = checkinTimeMillis > lateThreshold

        val status = if (isLate) {
            student.totalLateCount++
            AttendanceRecordStatus.LATE_ATTENDANCE
        } else {
            student.totalSessionsAttended++
            AttendanceRecordStatus.PRESENT_ON_TIME
        }

        return AttendanceEntry(
            sessionId = session.sessionId,
            studentId = student.studentId,
            timestamp = checkinTimeMillis,
            status = status,
            verifiedDistanceMeters = beaconFrame.calculatedDistanceMeters,
            isVerified = true
        )
    }

    /**
     * Compiles an interactive digital whiteboard stylus stroke from digitizer point stream
     */
    fun processStylusStroke(
        strokeId: String,
        sessionId: String,
        authorId: String,
        colorHex: String,
        points: List<StylusDigitizerPoint>
    ): StylusAnnotationStroke {
        require(points.isNotEmpty()) { "Stroke must contain at least 1 digitizer point" }

        val avgPressure = points.map { it.normalizedPressure }.average()
        val avgThickness = points.map { it.strokeThicknessPt }.average()
        val duration = if (points.size > 1) {
            points.last().timestamp - points.first().timestamp
        } else {
            10L
        }

        return StylusAnnotationStroke(
            strokeId = strokeId,
            sessionId = sessionId,
            authorId = authorId,
            colorHex = colorHex,
            pointCount = points.size,
            averagePressure = Math.round(avgPressure * 1000.0) / 1000.0,
            averageThicknessPt = Math.round(avgThickness * 10.0) / 10.0,
            durationMs = Math.max(1L, duration)
        )
    }

    /**
     * Generates a ZATCA-compliant tuition invoice for bootcamp enrollment
     */
    fun generateTuitionInvoice(
        invoiceId: String,
        studentId: String,
        track: BootcampTrack,
        scholarshipDiscountSar: Double = 0.0
    ): BootcampTuitionInvoice {
        val base = track.baseTuitionSar
        val clampedDiscount = Math.max(0.0, Math.min(base, scholarshipDiscountSar))
        val subtotal = Math.round((base - clampedDiscount) * 100.0) / 100.0
        val vat = Math.round(subtotal * ZATCA_VAT_RATE * 100.0) / 100.0
        val total = Math.round((subtotal + vat) * 100.0) / 100.0

        return BootcampTuitionInvoice(
            invoiceId = invoiceId,
            studentId = studentId,
            track = track,
            baseTuitionSar = base,
            scholarshipDiscountSar = clampedDiscount,
            subtotalSar = subtotal,
            vatSar = vat,
            totalSar = total,
            issueTimestamp = System.currentTimeMillis()
        )
    }

    /**
     * Calculates overall attendance percentage for a student across curriculum
     */
    fun calculateAttendanceRate(student: StudentProfile, totalPlannedSessions: Int): Double {
        if (totalPlannedSessions <= 0) return 100.0
        val attendedUnits = student.totalSessionsAttended + (student.totalLateCount * 0.75)
        val rate = (attendedUnits / totalPlannedSessions.toDouble()) * 100.0
        return Math.round(Math.min(100.0, Math.max(0.0, rate)) * 10.0) / 10.0
    }
}
