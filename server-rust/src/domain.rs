use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "snake_case")]
pub enum ComplianceStatus {
    TauxBas,
    ConformeProduction,
    SeuilDepasse,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "snake_case")]
pub enum ServerLifecycleStatus {
    Recu,
    Valide,
    Rejete,
    RevuSecondairement,
    ExporteAudit,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "snake_case")]
pub enum LightCondition {
    Insuffisante,
    Limite,
    Conforme,
    Excellente,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct RejectionFlags {
    pub low_light: bool,
    pub blur_detected: bool,
    pub framing_issue: bool,
    pub overexposed: bool,
    pub strip_not_detected: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct DeviceMetadata {
    pub platform: String,
    pub model: Option<String>,
    pub os_version: Option<String>,
    pub app_version: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct AcquisitionMetadata {
    pub light_condition: LightCondition,
    pub ambient_lux: Option<f32>,
    pub temperature_celsius: Option<f32>,
    pub humidity_percent: Option<f32>,
    pub camera_focus_score: Option<f32>,
    pub rejection_flags: RejectionFlags,
    pub device: DeviceMetadata,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct ImageReference {
    pub uri: String,
    pub sha256: String,
    pub content_type: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct Analysis {
    pub id: Uuid,
    pub client_analysis_id: Uuid,
    pub sample_id: String,
    pub ppm_estime: f32,
    pub ppm_min: f32,
    pub ppm_max: f32,
    pub compliance_status: ComplianceStatus,
    pub analysis_result: String,
    pub recommended_action: String,
    pub confidence: f32,
    pub analysis_rules_version: String,
    pub calibration_version: String,
    pub captured_at: DateTime<Utc>,
    pub received_at: DateTime<Utc>,
    pub image: ImageReference,
    pub acquisition_metadata: AcquisitionMetadata,
    pub server_lifecycle_status: ServerLifecycleStatus,
}

impl Analysis {
    pub fn is_same_submission(&self, other: &Self) -> bool {
        self.client_analysis_id == other.client_analysis_id
            && self.sample_id == other.sample_id
            && self.ppm_estime == other.ppm_estime
            && self.ppm_min == other.ppm_min
            && self.ppm_max == other.ppm_max
            && self.compliance_status == other.compliance_status
            && self.analysis_result == other.analysis_result
            && self.recommended_action == other.recommended_action
            && self.confidence == other.confidence
            && self.analysis_rules_version == other.analysis_rules_version
            && self.calibration_version == other.calibration_version
            && self.captured_at == other.captured_at
            && self.image == other.image
            && self.acquisition_metadata == other.acquisition_metadata
    }
}
