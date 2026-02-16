use axum::{
    extract::{Path, Query, State},
    http::{header, HeaderValue, StatusCode},
    response::IntoResponse,
    Json,
};
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use serde_json::json;
use uuid::Uuid;

use crate::{
    domain::{
        AcquisitionMetadata, Analysis, ComplianceStatus, ImageReference, ServerLifecycleStatus,
    },
    reporting,
    storage::{AnalysisStore, StorageError, UpsertResult},
    validation,
};

#[derive(Debug, Deserialize)]
pub struct CreateAnalysisPayload {
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
    pub image: ImageReference,
    pub acquisition_metadata: AcquisitionMetadata,
}

#[derive(Debug, Serialize)]
pub struct CreateAnalysisResponse {
    pub server_analysis_id: Uuid,
    pub server_lifecycle_status: ServerLifecycleStatus,
    pub received_at: DateTime<Utc>,
}

#[derive(Debug, Deserialize)]
pub struct HistoryQuery {
    pub limit: Option<usize>,
    pub cursor: Option<String>,
    pub sample_id: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct HistoryItem {
    pub server_analysis_id: Uuid,
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
    pub server_lifecycle_status: ServerLifecycleStatus,
}

#[derive(Debug, Serialize)]
pub struct HistoryResponse {
    pub items: Vec<HistoryItem>,
    pub next_cursor: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct AnalysisAckResponse {
    pub server_analysis_id: Uuid,
    pub server_lifecycle_status: ServerLifecycleStatus,
    pub validated_schema: bool,
    pub validated_business_rules: bool,
    pub queued_for_secondary_review: bool,
    pub ack_at: DateTime<Utc>,
}

#[derive(Debug, Deserialize)]
pub struct AuditExportPayload {
    pub from: Option<DateTime<Utc>>,
    pub to: Option<DateTime<Utc>>,
}

pub async fn health() -> Json<serde_json::Value> {
    Json(json!({"status":"ok","service":"peroxyde-server"}))
}

pub async fn create_analysis(
    State(store): State<AnalysisStore>,
    Json(payload): Json<CreateAnalysisPayload>,
) -> Result<(StatusCode, Json<CreateAnalysisResponse>), (StatusCode, Json<serde_json::Value>)> {
    let analysis = Analysis {
        id: Uuid::new_v4(),
        client_analysis_id: payload.client_analysis_id,
        sample_id: payload.sample_id,
        ppm_estime: payload.ppm_estime,
        ppm_min: payload.ppm_min,
        ppm_max: payload.ppm_max,
        compliance_status: payload.compliance_status,
        analysis_result: payload.analysis_result,
        recommended_action: payload.recommended_action,
        confidence: payload.confidence,
        analysis_rules_version: payload.analysis_rules_version,
        calibration_version: payload.calibration_version,
        captured_at: payload.captured_at,
        received_at: Utc::now(),
        image: payload.image,
        acquisition_metadata: payload.acquisition_metadata,
        server_lifecycle_status: ServerLifecycleStatus::Recu,
    };

    validation::validate_analysis(&analysis).map_err(|message| {
        (
            StatusCode::UNPROCESSABLE_ENTITY,
            Json(json!({"error":message})),
        )
    })?;

    match store
        .upsert_analysis(analysis)
        .await
        .map_err(storage_error)?
    {
        UpsertResult::Inserted(saved) => Ok((
            StatusCode::ACCEPTED,
            Json(CreateAnalysisResponse {
                server_analysis_id: saved.id,
                server_lifecycle_status: saved.server_lifecycle_status,
                received_at: saved.received_at,
            }),
        )),
        UpsertResult::IdempotentReplay(saved) => Ok((
            StatusCode::OK,
            Json(CreateAnalysisResponse {
                server_analysis_id: saved.id,
                server_lifecycle_status: saved.server_lifecycle_status,
                received_at: saved.received_at,
            }),
        )),
        UpsertResult::Conflict => Err((
            StatusCode::CONFLICT,
            Json(json!({"error":"client_analysis_id already exists with different payload"})),
        )),
    }
}

pub async fn analyses_history(
    State(store): State<AnalysisStore>,
    Query(query): Query<HistoryQuery>,
) -> Result<Json<HistoryResponse>, (StatusCode, Json<serde_json::Value>)> {
    let limit = query.limit.unwrap_or(20).clamp(1, 100);
    let offset = query
        .cursor
        .as_deref()
        .and_then(|cursor| cursor.parse::<usize>().ok())
        .unwrap_or(0);

    let (analyses, next_cursor) = store
        .list_paginated(limit, offset, query.sample_id.as_deref())
        .await
        .map_err(storage_error)?;

    let items = analyses
        .into_iter()
        .map(|analysis| HistoryItem {
            server_analysis_id: analysis.id,
            sample_id: analysis.sample_id,
            ppm_estime: analysis.ppm_estime,
            ppm_min: analysis.ppm_min,
            ppm_max: analysis.ppm_max,
            compliance_status: analysis.compliance_status,
            analysis_result: analysis.analysis_result,
            recommended_action: analysis.recommended_action,
            confidence: analysis.confidence,
            analysis_rules_version: analysis.analysis_rules_version,
            calibration_version: analysis.calibration_version,
            captured_at: analysis.captured_at,
            received_at: analysis.received_at,
            server_lifecycle_status: analysis.server_lifecycle_status,
        })
        .collect();

    Ok(Json(HistoryResponse { items, next_cursor }))
}

pub async fn analysis_ack(
    State(store): State<AnalysisStore>,
    Path(server_analysis_id): Path<Uuid>,
) -> impl IntoResponse {
    match store.find_by_id(server_analysis_id).await {
        Ok(Some(analysis)) => (
            StatusCode::OK,
            Json(AnalysisAckResponse {
                server_analysis_id: analysis.id,
                server_lifecycle_status: analysis.server_lifecycle_status,
                validated_schema: true,
                validated_business_rules: true,
                queued_for_secondary_review: true,
                ack_at: Utc::now(),
            }),
        )
            .into_response(),
        Ok(None) => (
            StatusCode::NOT_FOUND,
            Json(json!({"error":"analysis not found"})),
        )
            .into_response(),
        Err(err) => storage_error(err).into_response(),
    }
}

pub async fn export_audit_csv(
    State(store): State<AnalysisStore>,
    Json(payload): Json<AuditExportPayload>,
) -> Result<impl IntoResponse, (StatusCode, Json<serde_json::Value>)> {
    let analyses = store
        .list_for_audit_csv(payload.from, payload.to)
        .await
        .map_err(storage_error)?;

    let csv = reporting::audit_csv(&analyses).map_err(|err| {
        (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(json!({"error": err})),
        )
    })?;

    Ok((
        [
            (
                header::CONTENT_TYPE,
                HeaderValue::from_static("text/csv; charset=utf-8"),
            ),
            (
                header::CONTENT_DISPOSITION,
                HeaderValue::from_static("attachment; filename=registre-audit.csv"),
            ),
        ],
        csv,
    ))
}

fn storage_error(err: StorageError) -> (StatusCode, Json<serde_json::Value>) {
    (
        StatusCode::INTERNAL_SERVER_ERROR,
        Json(json!({"error":"storage_failure","details":err.to_string()})),
    )
}

#[cfg(test)]
mod tests {
    use axum::{
        body::{to_bytes, Body},
        http::{Method, Request, StatusCode},
        routing::{get, post},
        Router,
    };
    use tower::ServiceExt;

    use crate::storage::AnalysisStore;

    async fn test_app() -> Router {
        let store = AnalysisStore::new_in_memory().await.unwrap();
        Router::new()
            .route("/v1/analyses", post(super::create_analysis))
            .route("/v1/analyses/history", get(super::analyses_history))
            .with_state(store)
    }

    fn valid_payload() -> String {
        serde_json::json!({
            "client_analysis_id": "a8e68c43-8fba-4cad-bb7a-3d6b5d9af2aa",
            "sample_id": "SAMPLE-2026-0001",
            "ppm_estime": 276.4,
            "ppm_min": 255.0,
            "ppm_max": 298.0,
            "compliance_status": "conforme_production",
            "analysis_result": "CONFORME POUR LA PRODUCTION",
            "recommended_action": "Poursuivre la production normale.",
            "confidence": 0.93,
            "analysis_rules_version": "analysis-rules/v1",
            "calibration_version": "calib-2026-02",
            "captured_at": "2026-02-13T09:45:00Z",
            "image": {
                "uri": "s3://rochias-analyses/2026/02/13/a8e68c43.jpg",
                "sha256": "9f0868d4d1f8ca0f4b9f2b3f575b8f71c8e7df4a6932f53f9fdd5d6a016d89cf",
                "content_type": "image/jpeg"
            },
            "acquisition_metadata": {
                "light_condition": "conforme",
                "ambient_lux": 620.5,
                "temperature_celsius": 21.3,
                "humidity_percent": 44.0,
                "camera_focus_score": 0.88,
                "rejection_flags": {
                    "low_light": false,
                    "blur_detected": false,
                    "framing_issue": false,
                    "overexposed": false,
                    "strip_not_detected": false
                },
                "device": {
                    "platform": "android",
                    "model": "SM-X910",
                    "os_version": "14",
                    "app_version": "0.3.0"
                }
            }
        })
        .to_string()
    }

    #[tokio::test]
    async fn create_analysis_nominal_returns_202() {
        let app = test_app().await;

        let response = app
            .oneshot(
                Request::builder()
                    .method(Method::POST)
                    .uri("/v1/analyses")
                    .header("content-type", "application/json")
                    .body(Body::from(valid_payload()))
                    .unwrap(),
            )
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::ACCEPTED);
        let body = to_bytes(response.into_body(), usize::MAX).await.unwrap();
        let parsed: serde_json::Value = serde_json::from_slice(&body).unwrap();
        assert!(parsed.get("server_analysis_id").is_some());
        assert_eq!(parsed["server_lifecycle_status"], "recu");
        assert!(parsed.get("received_at").is_some());
    }

    #[tokio::test]
    async fn create_analysis_idempotent_returns_200() {
        let app = test_app().await;

        let first = app
            .clone()
            .oneshot(
                Request::builder()
                    .method(Method::POST)
                    .uri("/v1/analyses")
                    .header("content-type", "application/json")
                    .body(Body::from(valid_payload()))
                    .unwrap(),
            )
            .await
            .unwrap();
        assert_eq!(first.status(), StatusCode::ACCEPTED);

        let second = app
            .oneshot(
                Request::builder()
                    .method(Method::POST)
                    .uri("/v1/analyses")
                    .header("content-type", "application/json")
                    .body(Body::from(valid_payload()))
                    .unwrap(),
            )
            .await
            .unwrap();
        assert_eq!(second.status(), StatusCode::OK);
    }

    #[tokio::test]
    async fn create_analysis_invalid_json_returns_400() {
        let app = test_app().await;

        let response = app
            .oneshot(
                Request::builder()
                    .method(Method::POST)
                    .uri("/v1/analyses")
                    .header("content-type", "application/json")
                    .body(Body::from("{invalid-json"))
                    .unwrap(),
            )
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::BAD_REQUEST);
    }

    #[tokio::test]
    async fn create_analysis_business_validation_fails_returns_422() {
        let app = test_app().await;

        let mut payload: serde_json::Value = serde_json::from_str(&valid_payload()).unwrap();
        payload["confidence"] = serde_json::json!(1.7);

        let response = app
            .oneshot(
                Request::builder()
                    .method(Method::POST)
                    .uri("/v1/analyses")
                    .header("content-type", "application/json")
                    .body(Body::from(payload.to_string()))
                    .unwrap(),
            )
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::UNPROCESSABLE_ENTITY);
    }
}
