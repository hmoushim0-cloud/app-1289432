package sa.factory.qalamcampussmarted.hardware.digitizer

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Calibrated Stroke Dynamics Result
 * Strict Invariant: Zero Unicode Emojis.
 */
data class CalibratedStrokeDynamics(
    val strokeWidthPt: Double,
    val opacity: Double,
    val velocityDampeningFactor: Double,
    val isContactDetected: Boolean
)

/**
 * High-Precision Stylus Pressure Curve & Dynamics Engine
 * Translates 12-bit ADC pressure telemetry (0..4095) into natural digital ink strokes.
 */
class StylusPressureCurveEngine(
    private val profile: StylusPressureProfile = StylusPressureProfile.SOFT_SENSITIVE,
    private val minStrokeWidthPt: Double = 0.5,
    private val maxStrokeWidthPt: Double = 12.0
) {
    /**
     * Maps raw 12-bit ADC pressure (0..4095) into calibrated stroke weight and opacity.
     */
    fun evaluateStroke(
        rawPressure: Int,
        velocityPxPerMs: Double = 1.0,
        tipDown: Boolean = true
    ): CalibratedStrokeDynamics {
        if (!tipDown || rawPressure <= 20) {
            return CalibratedStrokeDynamics(
                strokeWidthPt = 0.0,
                opacity = 0.0,
                velocityDampeningFactor = 1.0,
                isContactDetected = false
            )
        }

        val clampedRaw = max(0, min(4095, rawPressure))
        val normalized = clampedRaw / 4095.0

        val curvedPressure = when (profile) {
            StylusPressureProfile.LINEAR -> normalized
            StylusPressureProfile.SOFT_SENSITIVE -> {
                // Soft response: reaches higher stroke weight with less force
                normalized.pow(0.65)
            }
            StylusPressureProfile.FIRM_CONTROL -> {
                // Firm response: sigmoid curve requiring deliberate force for broad strokes
                1.0 / (1.0 + exp(-10.0 * (normalized - 0.45)))
            }
        }

        // Apply velocity dampening (faster movements yield subtly thinner calligraphic strokes)
        val velocityFactor = max(0.65, min(1.0, 1.0 - (velocityPxPerMs * 0.08)))
        val strokeWidth = minStrokeWidthPt + (curvedPressure * (maxStrokeWidthPt - minStrokeWidthPt) * velocityFactor)
        val opacity = max(0.2, min(1.0, 0.3 + (curvedPressure * 0.7)))

        return CalibratedStrokeDynamics(
            strokeWidthPt = Math.round(strokeWidth * 100.0) / 100.0,
            opacity = Math.round(opacity * 100.0) / 100.0,
            velocityDampeningFactor = Math.round(velocityFactor * 100.0) / 100.0,
            isContactDetected = true
        )
    }
}
