use crate::domain::Analysis;

pub fn audit_line(analysis: &Analysis) -> String {
    format!(
        "analysis={} sample={} ppm_estime={} compliance_status={:?} analysis_label={} decision_version={} decision_message={} recommended_action={} lifecycle_status={:?}",
        analysis.id,
        analysis.sample_id,
        analysis.ppm_estime,
        analysis.compliance_status,
        format!("{} — {}", analysis.analysis_rules_version, analysis.analysis_result),
        analysis.analysis_rules_version,
        analysis.analysis_result,
        analysis.recommended_action,
        analysis.server_lifecycle_status
    )
}
