use crate::domain::Analysis;

pub fn audit_line(analysis: &Analysis) -> String {
    format!(
        "analysis={} sample={} ppm_estime={} compliance_status={:?} lifecycle_status={:?}",
        analysis.id,
        analysis.sample_id,
        analysis.ppm_estime,
        analysis.compliance_status,
        analysis.server_lifecycle_status
    )
}
