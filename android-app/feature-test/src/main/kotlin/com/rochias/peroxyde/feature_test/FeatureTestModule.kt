package com.rochias.peroxyde.feature_test

import android.graphics.BitmapFactory
import android.graphics.Color
import com.rochias.peroxyde.core_analysis.ANALYSIS_RULES_VERSION
import com.rochias.peroxyde.core_analysis.AnalysisPatch
import com.rochias.peroxyde.core_analysis.CoreAnalysisModule
import com.rochias.peroxyde.core_analysis.ImageFrame
import com.rochias.peroxyde.core_analysis.LabColor
import com.rochias.peroxyde.core_analysis.QualityStatus
import com.rochias.peroxyde.core_analysis.RgbPixel
import com.rochias.peroxyde.core_analysis.Roi
import com.rochias.peroxyde.core_camera.CaptureInput
import com.rochias.peroxyde.core_camera.CaptureValidationResult
import com.rochias.peroxyde.core_camera.CoreCameraModule
import com.rochias.peroxyde.core_db.AnalysisLocalStore
import com.rochias.peroxyde.core_db.AnalysisRecord
import com.rochias.peroxyde.core_db.ComplianceStatus
import com.rochias.peroxyde.core_sync.SyncEngine
import java.io.File
import kotlin.math.roundToInt
import java.util.UUID

fun interface CaptureFrameDecoder {
    fun decode(imagePath: String): ImageFrame
}

class AndroidBitmapCaptureFrameDecoder : CaptureFrameDecoder {
    override fun decode(imagePath: String): ImageFrame {
        val bitmap = BitmapFactory.decodeFile(imagePath)
            ?: error("Impossible de décoder l'image de capture : $imagePath")
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return ImageFrame(
            width = bitmap.width,
            height = bitmap.height,
            pixels = pixels.map {
                RgbPixel(
                    r = Color.red(it),
                    g = Color.green(it),
                    b = Color.blue(it),
                )
            },
        )
    }
}

fun interface OperatorRoiProvider {
    fun provide(frame: ImageFrame): Roi
}

class ManualOperatorRoiProvider : OperatorRoiProvider {
    override fun provide(frame: ImageFrame): Roi {
        val roiWidth = (frame.width / 2).coerceAtLeast(1)
        val roiHeight = (frame.height / 2).coerceAtLeast(1)
        val roiX = ((frame.width - roiWidth) / 2).coerceAtLeast(0)
        val roiY = ((frame.height - roiHeight) / 2).coerceAtLeast(0)
        return Roi(x = roiX, y = roiY, width = roiWidth, height = roiHeight)
    }
}

fun interface ReferenceScaleProvider {
    fun load(): List<AnalysisPatch>
}

class CsvReferenceScaleProvider(
    private val candidatePaths: List<String> = listOf(
        "data/calibration/reference-swatches.csv",
        "../data/calibration/reference-swatches.csv",
    ),
) : ReferenceScaleProvider {
    override fun load(): List<AnalysisPatch> {
        val csvFile = candidatePaths.asSequence().map(::File).firstOrNull { it.exists() }
            ?: error("Fichier de calibration introuvable (priorité data/calibration/reference-swatches.csv).")

        return csvFile.readLines()
            .drop(1)
            .asSequence()
            .filter { it.isNotBlank() }
            .map { line ->
                val cols = line.split(',', limit = 8)
                AnalysisPatch(
                    ppm = cols[2].toDouble(),
                    lab = LabColor(
                        l = cols[3].toDouble(),
                        a = cols[4].toDouble(),
                        b = cols[5].toDouble(),
                    ),
                )
            }
            .toList()
            .also { patches ->
                require(patches.size >= 2) { "La calibration doit contenir au moins deux points de référence." }
            }
    }
}

data class OperatorCaptureRequest(
    val imagePath: String,
    val captureInput: CaptureInput,
    val capturedAtEpochMs: Long,
)

sealed class TestFlowResult {
    data class Rejected(val capture: CaptureValidationResult) : TestFlowResult()
    data class AnalysisRejected(val operatorMessage: String, val reasons: List<String>) : TestFlowResult()
    data class Completed(val record: AnalysisRecord) : TestFlowResult()
}

class TestWorkflowService(
    private val store: AnalysisLocalStore,
    private val syncEngine: SyncEngine,
    private val frameDecoder: CaptureFrameDecoder = AndroidBitmapCaptureFrameDecoder(),
    private val roiProvider: OperatorRoiProvider = ManualOperatorRoiProvider(),
    private val referenceScaleProvider: ReferenceScaleProvider = CsvReferenceScaleProvider(),
) {
    fun runOperatorFlow(request: OperatorCaptureRequest): TestFlowResult {
        val capture = CoreCameraModule.validateCapture(request.captureInput)
        if (!capture.accepted) {
            return TestFlowResult.Rejected(capture)
        }

        val frame = frameDecoder.decode(request.imagePath)
        val roi = roiProvider.provide(frame)
        val referenceScale = referenceScaleProvider.load()
        val outcome = CoreAnalysisModule.analyzeStrip(frame, roi, referenceScale)

        if (outcome.quality.status == QualityStatus.REJECTED) {
            val reasons = outcome.quality.reasons.ifEmpty {
                listOf("Qualité de capture insuffisante pour une analyse fiable.")
            }
            return TestFlowResult.AnalysisRejected(
                operatorMessage = "Analyse refusée : qualité de capture rejetée. Reprendre la photo (luminosité, netteté, cadrage).",
                reasons = reasons,
            )
        }

        val ppm = outcome.ppmEstimate?.ppmEstimate?.roundToInt()
            ?: error("Analyse invalide : estimation PPM absente malgré une capture acceptée.")
        val decision = outcome.decision ?: CoreAnalysisModule.evaluatePpm(ppm)

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
            analysisRulesVersion = outcome.decision?.contractVersion ?: ANALYSIS_RULES_VERSION,
        )
        store.saveValidatedAnalysis(record)
        store.enqueueForSync(record.localId, request.capturedAtEpochMs)
        syncEngine.runOnce()

        return TestFlowResult.Completed(record)
    }
}

object FeatureTestModule
