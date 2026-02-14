package com.rochias.peroxyde.core_camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreCameraModuleTest {

    @Test
    fun rejects_capture_with_explicit_reasons() {
        val result = CoreCameraModule.validateCapture(
            CaptureInput(
                distanceCm = 8.0,
                angleDegrees = 20.0,
                luminance = 10.0,
                blurScore = 0.6,
                saturationRatio = 0.5,
            ),
        )

        assertFalse(result.accepted)
        assertTrue(result.rejectionReasons.contains(CaptureRejectionReason.DISTANCE_TOO_CLOSE))
        assertTrue(result.rejectionReasons.contains(CaptureRejectionReason.ANGLE_TOO_HIGH))
        assertTrue(result.rejectionReasons.contains(CaptureRejectionReason.LUMINANCE_TOO_LOW))
        assertTrue(result.rejectionReasons.contains(CaptureRejectionReason.BLUR_TOO_HIGH))
        assertTrue(result.rejectionReasons.contains(CaptureRejectionReason.SATURATION_TOO_HIGH))
    }
}
