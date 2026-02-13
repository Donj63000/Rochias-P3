use crate::domain::Analysis;

pub fn validate_analysis(analysis: &Analysis) -> Result<(), &'static str> {
    if analysis.sample_id.trim().is_empty() {
        return Err("sample_id is required");
    }
    Ok(())
}
