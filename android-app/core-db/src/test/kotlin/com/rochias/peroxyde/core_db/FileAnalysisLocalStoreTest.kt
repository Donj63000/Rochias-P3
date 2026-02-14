package com.rochias.peroxyde.core_db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FileAnalysisLocalStoreTest {

    @Test
    fun persists_local_analysis_and_queue_without_loss() {
        val tempDir = createTempDir(prefix = "p3-db")
        try {
            val store = FileAnalysisLocalStore(tempDir)
            val record = AnalysisRecord(
                localId = "scan-1",
                capturedAtEpochMs = 111L,
                ppm = 180,
                complianceStatus = ComplianceStatus.COMPLIANT,
                imagePath = "images/scan-1.jpg",
                captureRulesVersion = "capture-rules/v1",
                analysisRulesVersion = "analysis-rules/v1",
            )

            store.saveValidatedAnalysis(record)
            store.enqueueForSync(localId = "scan-1", nowEpochMs = 111L)

            val reloaded = FileAnalysisLocalStore(tempDir)
            assertNotNull(reloaded.getById("scan-1"))
            assertEquals(1, reloaded.listAnalyses().size)
            assertEquals(1, reloaded.listPendingQueue().size)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
