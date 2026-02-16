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

pub fn audit_csv(analyses: &[Analysis]) -> Result<String, String> {
    let mut writer = csv::Writer::from_writer(vec![]);
    writer
        .write_record([
            "server_analysis_id",
            "client_analysis_id",
            "sample_id",
            "ppm_estime",
            "ppm_min",
            "ppm_max",
            "compliance_status",
            "analysis_result",
            "recommended_action",
            "confidence",
            "analysis_rules_version",
            "calibration_version",
            "captured_at",
            "received_at",
            "server_lifecycle_status",
            "image.uri",
            "image.sha256",
            "image.content_type",
            "acquisition.light_condition",
            "acquisition.ambient_lux",
            "acquisition.temperature_celsius",
            "acquisition.humidity_percent",
            "acquisition.camera_focus_score",
            "acquisition.rejection_flags.low_light",
            "acquisition.rejection_flags.blur_detected",
            "acquisition.rejection_flags.framing_issue",
            "acquisition.rejection_flags.overexposed",
            "acquisition.rejection_flags.strip_not_detected",
            "device.platform",
            "device.model",
            "device.os_version",
            "device.app_version",
        ])
        .map_err(|err| err.to_string())?;

    for analysis in analyses {
        writer
            .write_record([
                analysis.id.to_string(),
                analysis.client_analysis_id.to_string(),
                analysis.sample_id.clone(),
                analysis.ppm_estime.to_string(),
                analysis.ppm_min.to_string(),
                analysis.ppm_max.to_string(),
                serde_json::to_string(&analysis.compliance_status)
                    .map_err(|err| err.to_string())?,
                analysis.analysis_result.clone(),
                analysis.recommended_action.clone(),
                analysis.confidence.to_string(),
                analysis.analysis_rules_version.clone(),
                analysis.calibration_version.clone(),
                analysis.captured_at.to_rfc3339(),
                analysis.received_at.to_rfc3339(),
                serde_json::to_string(&analysis.server_lifecycle_status)
                    .map_err(|err| err.to_string())?,
                analysis.image.uri.clone(),
                analysis.image.sha256.clone(),
                analysis.image.content_type.clone(),
                serde_json::to_string(&analysis.acquisition_metadata.light_condition)
                    .map_err(|err| err.to_string())?,
                analysis
                    .acquisition_metadata
                    .ambient_lux
                    .map(|v| v.to_string())
                    .unwrap_or_default(),
                analysis
                    .acquisition_metadata
                    .temperature_celsius
                    .map(|v| v.to_string())
                    .unwrap_or_default(),
                analysis
                    .acquisition_metadata
                    .humidity_percent
                    .map(|v| v.to_string())
                    .unwrap_or_default(),
                analysis
                    .acquisition_metadata
                    .camera_focus_score
                    .map(|v| v.to_string())
                    .unwrap_or_default(),
                analysis
                    .acquisition_metadata
                    .rejection_flags
                    .low_light
                    .to_string(),
                analysis
                    .acquisition_metadata
                    .rejection_flags
                    .blur_detected
                    .to_string(),
                analysis
                    .acquisition_metadata
                    .rejection_flags
                    .framing_issue
                    .to_string(),
                analysis
                    .acquisition_metadata
                    .rejection_flags
                    .overexposed
                    .to_string(),
                analysis
                    .acquisition_metadata
                    .rejection_flags
                    .strip_not_detected
                    .to_string(),
                analysis.acquisition_metadata.device.platform.clone(),
                analysis
                    .acquisition_metadata
                    .device
                    .model
                    .clone()
                    .unwrap_or_default(),
                analysis
                    .acquisition_metadata
                    .device
                    .os_version
                    .clone()
                    .unwrap_or_default(),
                analysis.acquisition_metadata.device.app_version.clone(),
            ])
            .map_err(|err| err.to_string())?;
    }

    writer.flush().map_err(|err| err.to_string())?;
    let bytes = writer.into_inner().map_err(|err| err.to_string())?;
    String::from_utf8(bytes).map_err(|err| err.to_string())
}
