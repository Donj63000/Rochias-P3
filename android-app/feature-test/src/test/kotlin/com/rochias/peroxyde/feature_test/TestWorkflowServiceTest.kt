package com.rochias.peroxyde.feature_test

import com.rochias.peroxyde.core_analysis.AnalysisPatch
import com.rochias.peroxyde.core_analysis.ImageFrame
import com.rochias.peroxyde.core_analysis.LabColor
import com.rochias.peroxyde.core_analysis.RgbPixel
import com.rochias.peroxyde.core_analysis.Roi
import com.rochias.peroxyde.core_camera.CaptureInput
import com.rochias.peroxyde.core_db.FileAnalysisLocalStore
import com.rochias.peroxyde.core_sync.SyncApi
import com.rochias.peroxyde.core_sync.SyncEngine
import com.rochias.peroxyde.core_sync.SyncPayload
import com.rochias.peroxyde.core_sync.SyncResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestWorkflowServiceTest {

    @Test
    fun keeps_local_measurement_when_server_down_then_syncs_after_reconnect() {
        val tempDir = createTempDir(prefix = "p3-workflow")
        try {
            val store = FileAnalysisLocalStore(tempDir)
            val api = ReconnectableApi()
            val syncEngine = SyncEngine(store, api)
            val service = TestWorkflowService(
                store = store,
                syncEngine = syncEngine,
                frameDecoder = solidFrameDecoder(color = RgbPixel(200, 140, 80)),
                roiProvider = OperatorRoiProvider { Roi(2, 2, 4, 4) },
                referenceScaleProvider = ReferenceScaleProvider {
                    listOf(
                        AnalysisPatch(100.0, LabColor(72.0, 5.0, 30.0)),
                        AnalysisPatch(500.0, LabColor(61.0, 18.0, 40.0)),
                    )
                },
            )

            api.available = false
            val result = service.runOperatorFlow(
                OperatorCaptureRequest(
                    imagePath = "images/scan-77.jpg",
                    captureInput = CaptureInput(
                        distanceCm = 16.0,
                        angleDegrees = 2.0,
                        luminance = 120.0,
                        blurScore = 0.1,
                        saturationRatio = 0.12,
                    ),
                    capturedAtEpochMs = 42L,
                ),
            )

            assertTrue(result is TestFlowResult.Completed)
            val record = (result as TestFlowResult.Completed).record
            assertTrue(record.ppm in 100..500)
            assertEquals(record.ppm, store.listAnalyses().single().ppm)
            assertEquals(1, store.listPendingQueue().size)

            api.available = true
            val stats = syncEngine.runOnce()
            assertEquals(1, stats.acked)
            assertEquals(0, store.listPendingQueue().size)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun rejects_flow_with_operator_message_when_analysis_quality_is_rejected() {
        val tempDir = createTempDir(prefix = "p3-workflow-reject")
        try {
            val store = FileAnalysisLocalStore(tempDir)
            val service = TestWorkflowService(
                store = store,
                syncEngine = SyncEngine(store, ReconnectableApi()),
                frameDecoder = solidFrameDecoder(color = RgbPixel(0, 0, 0)),
                roiProvider = OperatorRoiProvider { Roi(0, 0, 4, 4) },
                referenceScaleProvider = ReferenceScaleProvider {
                    listOf(
                        AnalysisPatch(100.0, LabColor(55.0, 8.0, 24.0)),
                        AnalysisPatch(500.0, LabColor(42.0, 18.0, 34.0)),
                    )
                },
            )

            val result = service.runOperatorFlow(
                OperatorCaptureRequest(
                    imagePath = "images/scan-dark.jpg",
                    captureInput = CaptureInput(
                        distanceCm = 16.0,
                        angleDegrees = 2.0,
                        luminance = 120.0,
                        blurScore = 0.1,
                        saturationRatio = 0.12,
                    ),
                    capturedAtEpochMs = 43L,
                ),
            )

            assertTrue(result is TestFlowResult.AnalysisRejected)
            val rejection = result as TestFlowResult.AnalysisRejected
            assertTrue(rejection.operatorMessage.contains("Analyse refusée"))
            assertEquals(0, store.listAnalyses().size)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}

private class ReconnectableApi : SyncApi {
    var available: Boolean = false

    override fun pushAnalysis(payload: SyncPayload): SyncResult {
        return if (available) SyncResult.Ack("ack-${payload.localId}")
        else SyncResult.RetryableFailure("serveur KO")
    }
}

private fun solidFrameDecoder(color: RgbPixel): CaptureFrameDecoder = CaptureFrameDecoder {
    ImageFrame(
        width = 8,
        height = 8,
        pixels = List(64) { color },
    )
}
