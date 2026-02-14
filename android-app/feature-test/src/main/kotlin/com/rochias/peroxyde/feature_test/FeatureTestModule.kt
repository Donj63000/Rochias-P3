package com.rochias.peroxyde.feature_test

import com.rochias.peroxyde.core_analysis.CoreAnalysisModule
import com.rochias.peroxyde.core_camera.CaptureInput
import com.rochias.peroxyde.core_camera.CaptureValidationResult
import com.rochias.peroxyde.core_camera.CoreCameraModule
import com.rochias.peroxyde.core_db.AnalysisLocalStore
import com.rochias.peroxyde.core_db.AnalysisRecord
import com.rochias.peroxyde.core_db.ComplianceStatus
import com.rochias.peroxyde.core_sync.SyncEngine
import java.util.UUID

interface PpmEstimator {
    fun estimatePpm(imagePath: String): Int
}

class SimplePpmEstimator : PpmEstimator {
    override fun estimatePpm(imagePath: String): Int = 300
}

data class OperatorCaptureRequest(
    val imagePath: String,
    val captureInput: CaptureInput,
    val capturedAtEpochMs: Long,
)

sealed class TestFlowResult {
    data class Rejected(val capture: CaptureValidationResult) : TestFlowResult()
    data class Completed(val record: AnalysisRecord) : TestFlowResult()
}

class TestWorkflowService(
    private val store: AnalysisLocalStore,
    private val syncEngine: SyncEngine,
    private val ppmEstimator: PpmEstimator = SimplePpmEstimator(),
) {
    fun runOperatorFlow(request: OperatorCaptureRequest): TestFlowResult {
        val capture = CoreCameraModule.validateCapture(request.captureInput)
        if (!capture.accepted) {
            return TestFlowResult.Rejected(capture)
        }

        val ppm = ppmEstimator.estimatePpm(request.imagePath)
        val decision = CoreAnalysisModule.evaluatePpm(ppm)

        val status = when (decision.complianceStatus) {
            "CONFORME" -> ComplianceStatus.COMPLIANT
            "MAINTENANCE_QUALITE" -> ComplianceStatus.ALERT_LOW
            else -> ComplianceStatus.ALERT_HIGH
        }

        val record = AnalysisRecord(
            localId = UUID.randomUUID().toString(),
            capturedAtEpochMs = request.capturedAtEpochMs,
            ppm = ppm,
            complianceStatus = status,
            imagePath = request.imagePath,
            captureRulesVersion = capture.rulesVersion,
            analysisRulesVersion = decision.contractVersion,
        )
        store.saveValidatedAnalysis(record)
        store.enqueueForSync(record.localId, request.capturedAtEpochMs)
        syncEngine.runOnce()

        return TestFlowResult.Completed(record)
    }
}

object FeatureTestModule
