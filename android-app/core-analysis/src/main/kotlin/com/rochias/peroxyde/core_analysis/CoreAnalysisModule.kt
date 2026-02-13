package com.rochias.peroxyde.core_analysis

const val ANALYSIS_RULES_VERSION: String = "analysis-rules/v1"

data class VersionedAnalysisDecision(
    val contractVersion: String,
    val analysisResult: String,
    val complianceStatus: String,
    val recommendedAction: String,
)

object CoreAnalysisModule {
    fun evaluatePpm(ppm: Int): VersionedAnalysisDecision {
        return when {
            ppm < 100 -> VersionedAnalysisDecision(
                contractVersion = ANALYSIS_RULES_VERSION,
                analysisResult = "ATTENTION TAUX BAS",
                complianceStatus = "MAINTENANCE_QUALITE",
                recommendedAction = "Appliquer les consignes maintenance/qualité.",
            )

            ppm <= 500 -> VersionedAnalysisDecision(
                contractVersion = ANALYSIS_RULES_VERSION,
                analysisResult = "CONFORME POUR LA PRODUCTION",
                complianceStatus = "CONFORME",
                recommendedAction = "Poursuivre la production normale.",
            )

            else -> VersionedAnalysisDecision(
                contractVersion = ANALYSIS_RULES_VERSION,
                analysisResult = "ALERTE seuil dépassé",
                complianceStatus = "SEUIL_DEPASSE",
                recommendedAction = "Arrêt/notification/recontrôle immédiats.",
            )
        }
    }
}
