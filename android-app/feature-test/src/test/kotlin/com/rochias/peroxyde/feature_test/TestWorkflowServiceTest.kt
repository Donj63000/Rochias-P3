package com.rochias.peroxyde.feature_test

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
                ppmEstimator = object : PpmEstimator {
                    override fun estimatePpm(imagePath: String): Int = 520
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
            assertEquals(1, store.listAnalyses().size)
            assertEquals(1, store.listPendingQueue().size)

            api.available = true
            val stats = syncEngine.runOnce()
            assertEquals(1, stats.acked)
            assertEquals(0, store.listPendingQueue().size)
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
