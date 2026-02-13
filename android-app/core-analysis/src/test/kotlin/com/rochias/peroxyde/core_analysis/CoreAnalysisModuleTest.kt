package com.rochias.peroxyde.core_analysis

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class CoreAnalysisModuleTest {

    @Test
    fun evaluates_frontier_values() {
        assertEquals("ATTENTION TAUX BAS", CoreAnalysisModule.evaluatePpm(99).analysisResult)
        assertEquals("CONFORME POUR LA PRODUCTION", CoreAnalysisModule.evaluatePpm(100).analysisResult)
        assertEquals("CONFORME POUR LA PRODUCTION", CoreAnalysisModule.evaluatePpm(500).analysisResult)
        assertEquals("ALERTE seuil dépassé", CoreAnalysisModule.evaluatePpm(501).analysisResult)
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
