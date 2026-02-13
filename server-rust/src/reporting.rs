use crate::domain::Analysis;

pub fn audit_line(analysis: &Analysis) -> String {
    format!("analysis={} sample={} result={}", analysis.id, analysis.sample_id, analysis.result)
}
