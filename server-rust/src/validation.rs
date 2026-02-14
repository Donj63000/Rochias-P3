use crate::domain::Analysis;

pub fn validate_analysis(analysis: &Analysis) -> Result<(), &'static str> {
    if analysis.sample_id.trim().is_empty() {
        return Err("sample_id is required");
    }

    if !(0.0..=1.0).contains(&analysis.confidence) {
        return Err("confidence must be between 0 and 1");
    }

    if analysis.ppm_min > analysis.ppm_estime || analysis.ppm_estime > analysis.ppm_max {
        return Err("ppm_estime must be within [ppm_min, ppm_max]");
    }

    if analysis.analysis_rules_version.trim().is_empty() {
        return Err("analysis_rules_version is required");
    }

    if analysis.calibration_version.trim().is_empty() {
        return Err("calibration_version is required");
    }

    if analysis.image.uri.trim().is_empty() || analysis.image.sha256.trim().is_empty() {
        return Err("image reference is required");
    }

    Ok(())
}
