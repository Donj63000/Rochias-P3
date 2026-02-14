package com.rochias.peroxyde.core_sync

import com.rochias.peroxyde.core_db.AnalysisRecord
import com.rochias.peroxyde.core_db.ComplianceStatus
import com.rochias.peroxyde.core_db.FileAnalysisLocalStore
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncEngineTest {

    @Test
    fun retries_when_server_is_down_and_acks_after_reconnection() {
        val tempDir = createTempDir(prefix = "p3-sync")
        try {
            val store = FileAnalysisLocalStore(tempDir)
            val record = AnalysisRecord(
                localId = "scan-1",
                capturedAtEpochMs = 1000L,
                ppm = 420,
                complianceStatus = ComplianceStatus.COMPLIANT,
                imagePath = "images/scan-1.jpg",
                captureRulesVersion = "capture-rules/v1",
                analysisRulesVersion = "analysis-rules/v1",
            )
            store.saveValidatedAnalysis(record)
            store.enqueueForSync("scan-1", 1000L)

            val api = FlakyApi(failuresBeforeAck = 1)
            val engine = SyncEngine(store, api)

            val first = engine.runOnce()
            assertEquals(1, first.failed)
            assertEquals(1, store.listPendingQueue().size)
            assertEquals(1, store.listPendingQueue().first().attemptCount)

            val second = engine.runOnce()
            assertEquals(1, second.acked)
            assertEquals(0, store.listPendingQueue().size)
            assertEquals(2, api.receivedIdempotencyKeys.size)
            assertEquals("scan-1", api.receivedIdempotencyKeys[0])
            assertEquals("scan-1", api.receivedIdempotencyKeys[1])
        } finally {
            tempDir.deleteRecursively()
        }
    }
}

private class FlakyApi(private val failuresBeforeAck: Int) : SyncApi {
    var callCount = 0
    val receivedIdempotencyKeys = mutableListOf<String>()

    override fun pushAnalysis(payload: SyncPayload): SyncResult {
        callCount += 1
        receivedIdempotencyKeys += payload.idempotencyKey
        return if (callCount <= failuresBeforeAck) {
            SyncResult.RetryableFailure("serveur KO")
        } else {
            SyncResult.Ack(remoteReceiptId = "ack-${payload.localId}")
        }
    }
}
