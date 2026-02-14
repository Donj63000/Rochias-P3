package com.rochias.peroxyde.core_camera

private const val DEFAULT_MIN_DISTANCE_CM = 12.0
private const val DEFAULT_MAX_DISTANCE_CM = 25.0
private const val DEFAULT_MAX_ANGLE_DEGREES = 15.0
private const val DEFAULT_MIN_LUMINANCE = 35.0
private const val DEFAULT_MAX_LUMINANCE = 235.0
private const val DEFAULT_MAX_BLUR = 0.35
private const val DEFAULT_MAX_SATURATION = 0.30

/**
 * Versionnée pour audit : règles de validation capture côté mobile.
 */
const val CAPTURE_RULES_VERSION: String = "capture-rules/v1"

enum class CaptureRejectionReason(val message: String) {
    DISTANCE_TOO_CLOSE("Distance insuffisante : éloigner la caméra."),
    DISTANCE_TOO_FAR("Distance excessive : rapprocher la caméra."),
    ANGLE_TOO_HIGH("Angle de prise de vue non conforme : réaligner la bandelette."),
    LUMINANCE_TOO_LOW("Luminosité trop faible : augmenter l'éclairage."),
    LUMINANCE_TOO_HIGH("Luminosité trop forte : réduire les reflets."),
    BLUR_TOO_HIGH("Flou détecté : stabiliser l'appareil avant capture."),
    SATURATION_TOO_HIGH("Saturation excessive : corriger l'exposition."),
}

data class CaptureConstraints(
    val minDistanceCm: Double = DEFAULT_MIN_DISTANCE_CM,
    val maxDistanceCm: Double = DEFAULT_MAX_DISTANCE_CM,
    val maxAngleDegrees: Double = DEFAULT_MAX_ANGLE_DEGREES,
    val minLuminance: Double = DEFAULT_MIN_LUMINANCE,
    val maxLuminance: Double = DEFAULT_MAX_LUMINANCE,
    val maxBlur: Double = DEFAULT_MAX_BLUR,
    val maxSaturationRatio: Double = DEFAULT_MAX_SATURATION,
)

data class CaptureInput(
    val distanceCm: Double,
    val angleDegrees: Double,
    val luminance: Double,
    val blurScore: Double,
    val saturationRatio: Double,
)

data class CaptureValidationResult(
    val accepted: Boolean,
    val rulesVersion: String,
    val rejectionReasons: List<CaptureRejectionReason>,
) {
    val operatorMessages: List<String> = rejectionReasons.map { it.message }
}

object CoreCameraModule {

    fun validateCapture(
        input: CaptureInput,
        constraints: CaptureConstraints = CaptureConstraints(),
    ): CaptureValidationResult {
        val reasons = mutableListOf<CaptureRejectionReason>()

        if (input.distanceCm < constraints.minDistanceCm) reasons += CaptureRejectionReason.DISTANCE_TOO_CLOSE
        if (input.distanceCm > constraints.maxDistanceCm) reasons += CaptureRejectionReason.DISTANCE_TOO_FAR
        if (kotlin.math.abs(input.angleDegrees) > constraints.maxAngleDegrees) reasons += CaptureRejectionReason.ANGLE_TOO_HIGH
        if (input.luminance < constraints.minLuminance) reasons += CaptureRejectionReason.LUMINANCE_TOO_LOW
        if (input.luminance > constraints.maxLuminance) reasons += CaptureRejectionReason.LUMINANCE_TOO_HIGH
        if (input.blurScore > constraints.maxBlur) reasons += CaptureRejectionReason.BLUR_TOO_HIGH
        if (input.saturationRatio > constraints.maxSaturationRatio) reasons += CaptureRejectionReason.SATURATION_TOO_HIGH

        return CaptureValidationResult(
            accepted = reasons.isEmpty(),
            rulesVersion = CAPTURE_RULES_VERSION,
            rejectionReasons = reasons,
        )
    }
}
