package com.rochias.peroxyde.core_analysis

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreAnalysisModuleTest {

    @Test
    fun evaluates_frontier_values() {
        assertEquals("ATTENTION TAUX BAS", CoreAnalysisModule.evaluatePpm(95).analysisResult)
        assertEquals("CONFORME POUR LA PRODUCTION", CoreAnalysisModule.evaluatePpm(100).analysisResult)
        assertEquals("CONFORME POUR LA PRODUCTION", CoreAnalysisModule.evaluatePpm(500).analysisResult)
        assertEquals("ALERTE seuil dépassé", CoreAnalysisModule.evaluatePpm(505).analysisResult)
    }

    @Test
    fun full_pipeline_estimates_ppm_with_interval_and_decision() {
        val frame = uniformFrame(width = 8, height = 8, color = RgbPixel(210, 158, 96))
        val roi = Roi(2, 2, 4, 4)
        val scale = listOf(
            AnalysisPatch(100.0, CoreAnalysisModule.srgbToLab(RgbPixel(220, 180, 120))),
            AnalysisPatch(500.0, CoreAnalysisModule.srgbToLab(RgbPixel(200, 140, 80))),
            AnalysisPatch(800.0, CoreAnalysisModule.srgbToLab(RgbPixel(180, 110, 60))),
        )

        val outcome = CoreAnalysisModule.analyzeStrip(frame, roi, scale)

        assertEquals(QualityStatus.ACCEPTED, outcome.quality.status)
        assertNotNull(outcome.ppmEstimate)
        assertTrue(outcome.ppmEstimate!!.ppmEstimate in outcome.ppmEstimate.ppmMin..outcome.ppmEstimate.ppmMax)
        assertNotNull(outcome.decision)
    }

    @Test
    fun marks_ambiguous_color_with_lower_confidence() {
        val ambiguous = CoreAnalysisModule.srgbToLab(RgbPixel(210, 150, 95))
        val estimate = CoreAnalysisModule.estimatePpm(
            sampleLab = ambiguous,
            referenceScale = listOf(
                AnalysisPatch(100.0, CoreAnalysisModule.srgbToLab(RgbPixel(220, 165, 105))),
                AnalysisPatch(500.0, CoreAnalysisModule.srgbToLab(RgbPixel(200, 135, 85))),
            ),
        )

        assertTrue(estimate.ppmEstimate in 100.0..500.0)
        assertTrue(estimate.deltaE00ToLower > 0.0)
        assertTrue(estimate.deltaE00ToUpper > 0.0)
    }

    @Test
    fun rejects_bad_capture_conditions_before_ppm() {
        val darkPixels = List(36) { RgbPixel(2, 2, 2) }
        val frame = ImageFrame(width = 6, height = 6, pixels = darkPixels)
        val roi = Roi(0, 0, 4, 4)

        val outcome = CoreAnalysisModule.analyzeStrip(
            frame = frame,
            roi = roi,
            referenceScale = listOf(
                AnalysisPatch(100.0, LabColor(55.0, 8.0, 24.0)),
                AnalysisPatch(500.0, LabColor(42.0, 18.0, 34.0)),
            ),
        )

        assertEquals(QualityStatus.REJECTED, outcome.quality.status)
        assertEquals(null, outcome.ppmEstimate)
        assertTrue(outcome.quality.reasons.isNotEmpty())
    }

    @Test
    fun matches_json_contract_cases() {
        val contract = JSONObject(readContractJson())
        assertEquals(ANALYSIS_RULES_VERSION, contract.getString("contract_version"))

        val cases = contract.getJSONArray("cases")
        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val ppm = case.getInt("ppm")
            val decision = CoreAnalysisModule.evaluatePpm(ppm)

            assertEquals(contract.getString("contract_version"), decision.contractVersion)
            assertEquals(case.getString("analysis_result"), decision.analysisResult)
            assertEquals(case.getString("compliance_status"), decision.complianceStatus)
            assertEquals(case.getString("recommended_action"), decision.recommendedAction)
        }
    }

    private fun uniformFrame(width: Int, height: Int, color: RgbPixel): ImageFrame {
        return ImageFrame(width = width, height = height, pixels = List(width * height) { color })
    }

    private fun readContractJson(): String {
        val candidates = listOf(
            File("../data/validation/analysis_rules_contract_v1.json"),
            File("../../data/validation/analysis_rules_contract_v1.json"),
        )

        val contractFile = candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Cannot locate analysis_rules_contract_v1.json")

        return contractFile.readText()
    }
}
