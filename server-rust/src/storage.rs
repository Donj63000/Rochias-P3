use std::{fmt, path::Path};

use chrono::{DateTime, Utc};
use rusqlite::{params, OptionalExtension};
use tokio_rusqlite::Connection;
use uuid::Uuid;

use crate::domain::{
    AcquisitionMetadata, Analysis, ComplianceStatus, ImageReference, ServerLifecycleStatus,
};

#[derive(Clone)]
pub struct AnalysisStore {
    conn: Connection,
}

#[derive(Debug)]
pub enum UpsertResult {
    Inserted(Analysis),
    IdempotentReplay(Analysis),
    Conflict,
}

#[derive(Debug)]
pub enum StorageError {
    Sqlite(String),
    Serde(String),
}

impl fmt::Display for StorageError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Sqlite(err) => write!(f, "sqlite error: {err}"),
            Self::Serde(err) => write!(f, "serde error: {err}"),
        }
    }
}

impl std::error::Error for StorageError {}

impl AnalysisStore {
    pub async fn new<P: AsRef<Path>>(path: P) -> Result<Self, StorageError> {
        let conn = Connection::open(path)
            .await
            .map_err(|err| StorageError::Sqlite(err.to_string()))?;
        let store = Self { conn };
        store.init_schema().await?;
        Ok(store)
    }

    pub async fn new_in_memory() -> Result<Self, StorageError> {
        let conn = Connection::open_in_memory()
            .await
            .map_err(|err| StorageError::Sqlite(err.to_string()))?;
        let store = Self { conn };
        store.init_schema().await?;
        Ok(store)
    }

    async fn init_schema(&self) -> Result<(), StorageError> {
        self.conn
            .call(|conn| {
                conn.execute_batch(
                    "
                    CREATE TABLE IF NOT EXISTS analyses (
                        id TEXT PRIMARY KEY,
                        client_analysis_id TEXT NOT NULL UNIQUE,
                        sample_id TEXT NOT NULL,
                        ppm_estime REAL NOT NULL,
                        ppm_min REAL NOT NULL,
                        ppm_max REAL NOT NULL,
                        compliance_status TEXT NOT NULL,
                        analysis_result TEXT NOT NULL,
                        recommended_action TEXT NOT NULL,
                        confidence REAL NOT NULL,
                        analysis_rules_version TEXT NOT NULL,
                        calibration_version TEXT NOT NULL,
                        captured_at TEXT NOT NULL,
                        received_at TEXT NOT NULL,
                        image_uri TEXT NOT NULL,
                        image_sha256 TEXT NOT NULL,
                        image_content_type TEXT NOT NULL,
                        acquisition_metadata_json TEXT NOT NULL,
                        server_lifecycle_status TEXT NOT NULL
                    );

                    CREATE INDEX IF NOT EXISTS idx_analyses_captured_at ON analyses (captured_at);
                    CREATE INDEX IF NOT EXISTS idx_analyses_received_at ON analyses (received_at);
                    CREATE INDEX IF NOT EXISTS idx_analyses_sample_id ON analyses (sample_id);
                    CREATE INDEX IF NOT EXISTS idx_analyses_server_lifecycle_status ON analyses (server_lifecycle_status);
                    ",
                )?;
                Ok(())
            })
            .await
            .map_err(|err| StorageError::Sqlite(err.to_string()))
    }

    pub async fn upsert_analysis(&self, analysis: Analysis) -> Result<UpsertResult, StorageError> {
        let client_analysis_id = analysis.client_analysis_id;
        let existing = self.find_by_client_analysis_id(client_analysis_id).await?;

        if let Some(saved) = existing {
            if saved.is_same_submission(&analysis) {
                return Ok(UpsertResult::IdempotentReplay(saved));
            }
            return Ok(UpsertResult::Conflict);
        }

        let acquisition_json = serde_json::to_string(&analysis.acquisition_metadata)
            .map_err(|err| StorageError::Serde(err.to_string()))?;

        let analysis_for_insert = analysis.clone();
        self.conn
            .call(move |conn| {
                conn.execute(
                    "INSERT INTO analyses (
                        id, client_analysis_id, sample_id, ppm_estime, ppm_min, ppm_max,
                        compliance_status, analysis_result, recommended_action, confidence,
                        analysis_rules_version, calibration_version, captured_at, received_at,
                        image_uri, image_sha256, image_content_type, acquisition_metadata_json,
                        server_lifecycle_status
                    ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16, ?17, ?18, ?19)",
                    params![
                        analysis_for_insert.id.to_string(),
                        analysis_for_insert.client_analysis_id.to_string(),
                        analysis_for_insert.sample_id,
                        analysis_for_insert.ppm_estime,
                        analysis_for_insert.ppm_min,
                        analysis_for_insert.ppm_max,
                        serde_json::to_string(&analysis_for_insert.compliance_status).unwrap(),
                        analysis_for_insert.analysis_result,
                        analysis_for_insert.recommended_action,
                        analysis_for_insert.confidence,
                        analysis_for_insert.analysis_rules_version,
                        analysis_for_insert.calibration_version,
                        analysis_for_insert.captured_at.to_rfc3339(),
                        analysis_for_insert.received_at.to_rfc3339(),
                        analysis_for_insert.image.uri,
                        analysis_for_insert.image.sha256,
                        analysis_for_insert.image.content_type,
                        acquisition_json,
                        serde_json::to_string(&analysis_for_insert.server_lifecycle_status).unwrap(),
                    ],
                )?;
                Ok(())
            })
            .await
            .map_err(|err| StorageError::Sqlite(err.to_string()))?;

        Ok(UpsertResult::Inserted(analysis))
    }

    pub async fn find_by_id(&self, id: Uuid) -> Result<Option<Analysis>, StorageError> {
        self.find_one("SELECT * FROM analyses WHERE id = ?1", vec![id.to_string()])
            .await
    }

    async fn find_by_client_analysis_id(
        &self,
        client_analysis_id: Uuid,
    ) -> Result<Option<Analysis>, StorageError> {
        self.find_one(
            "SELECT * FROM analyses WHERE client_analysis_id = ?1",
            vec![client_analysis_id.to_string()],
        )
        .await
    }

    async fn find_one(
        &self,
        query: &str,
        params_vec: Vec<String>,
    ) -> Result<Option<Analysis>, StorageError> {
        let query_owned = query.to_string();
        self.conn
            .call(move |conn| {
                let mut stmt = conn.prepare(&query_owned)?;
                let found = stmt
                    .query_row([params_vec[0].as_str()], parse_analysis_row)
                    .optional()?;
                Ok(found)
            })
            .await
            .map_err(|err| StorageError::Sqlite(err.to_string()))
    }

    pub async fn list_paginated(
        &self,
        limit: usize,
        offset: usize,
        sample_id: Option<&str>,
    ) -> Result<(Vec<Analysis>, Option<String>), StorageError> {
        let sample_filter = sample_id.map(str::to_owned);
        let limit_i64 = limit as i64;
        let offset_i64 = offset as i64;

        let (items, total): (Vec<Analysis>, i64) = self
            .conn
            .call(move |conn| {
                let total: i64 = if let Some(ref sample) = sample_filter {
                    conn.query_row(
                        "SELECT COUNT(*) FROM analyses WHERE sample_id = ?1",
                        [sample],
                        |row| row.get(0),
                    )?
                } else {
                    conn.query_row("SELECT COUNT(*) FROM analyses", [], |row| row.get(0))?
                };

                let mut stmt = if sample_filter.is_some() {
                    conn.prepare(
                        "SELECT * FROM analyses WHERE sample_id = ?1 ORDER BY received_at DESC LIMIT ?2 OFFSET ?3",
                    )?
                } else {
                    conn.prepare("SELECT * FROM analyses ORDER BY received_at DESC LIMIT ?1 OFFSET ?2")?
                };

                let analyses = if let Some(ref sample) = sample_filter {
                    stmt.query_map(params![sample, limit_i64, offset_i64], parse_analysis_row)?
                        .collect::<Result<Vec<_>, _>>()?
                } else {
                    stmt.query_map(params![limit_i64, offset_i64], parse_analysis_row)?
                        .collect::<Result<Vec<_>, _>>()?
                };

                Ok((analyses, total))
            })
            .await
            .map_err(|err| StorageError::Sqlite(err.to_string()))?;

        let next_offset = offset + items.len();
        let next_cursor = (next_offset < total as usize).then(|| next_offset.to_string());

        Ok((items, next_cursor))
    }

    pub async fn list_for_audit_csv(
        &self,
        from: Option<DateTime<Utc>>,
        to: Option<DateTime<Utc>>,
    ) -> Result<Vec<Analysis>, StorageError> {
        let from_s = from.map(|dt| dt.to_rfc3339());
        let to_s = to.map(|dt| dt.to_rfc3339());

        self.conn
            .call(move |conn| {
                let mut stmt = conn.prepare(
                    "SELECT * FROM analyses
                     WHERE (?1 IS NULL OR captured_at >= ?1)
                       AND (?2 IS NULL OR captured_at <= ?2)
                     ORDER BY captured_at ASC",
                )?;

                let analyses = stmt
                    .query_map(params![from_s, to_s], parse_analysis_row)?
                    .collect::<Result<Vec<_>, _>>()?;
                Ok(analyses)
            })
            .await
            .map_err(|err| StorageError::Sqlite(err.to_string()))
    }
}

fn parse_analysis_row(row: &rusqlite::Row<'_>) -> rusqlite::Result<Analysis> {
    let compliance_status_json: String = row.get("compliance_status")?;
    let lifecycle_status_json: String = row.get("server_lifecycle_status")?;
    let acquisition_json: String = row.get("acquisition_metadata_json")?;

    let compliance_status = serde_json::from_str::<ComplianceStatus>(&compliance_status_json)
        .map_err(|e| {
            rusqlite::Error::FromSqlConversionFailure(0, rusqlite::types::Type::Text, Box::new(e))
        })?;
    let lifecycle_status = serde_json::from_str::<ServerLifecycleStatus>(&lifecycle_status_json)
        .map_err(|e| {
            rusqlite::Error::FromSqlConversionFailure(0, rusqlite::types::Type::Text, Box::new(e))
        })?;
    let acquisition_metadata = serde_json::from_str::<AcquisitionMetadata>(&acquisition_json)
        .map_err(|e| {
            rusqlite::Error::FromSqlConversionFailure(0, rusqlite::types::Type::Text, Box::new(e))
        })?;

    let parse_date = |value: String| -> rusqlite::Result<DateTime<Utc>> {
        DateTime::parse_from_rfc3339(&value)
            .map(|dt| dt.with_timezone(&Utc))
            .map_err(|e| {
                rusqlite::Error::FromSqlConversionFailure(
                    0,
                    rusqlite::types::Type::Text,
                    Box::new(e),
                )
            })
    };

    Ok(Analysis {
        id: Uuid::parse_str(&row.get::<_, String>("id")?).map_err(|e| {
            rusqlite::Error::FromSqlConversionFailure(0, rusqlite::types::Type::Text, Box::new(e))
        })?,
        client_analysis_id: Uuid::parse_str(&row.get::<_, String>("client_analysis_id")?).map_err(
            |e| {
                rusqlite::Error::FromSqlConversionFailure(
                    0,
                    rusqlite::types::Type::Text,
                    Box::new(e),
                )
            },
        )?,
        sample_id: row.get("sample_id")?,
        ppm_estime: row.get("ppm_estime")?,
        ppm_min: row.get("ppm_min")?,
        ppm_max: row.get("ppm_max")?,
        compliance_status,
        analysis_result: row.get("analysis_result")?,
        recommended_action: row.get("recommended_action")?,
        confidence: row.get("confidence")?,
        analysis_rules_version: row.get("analysis_rules_version")?,
        calibration_version: row.get("calibration_version")?,
        captured_at: parse_date(row.get("captured_at")?)?,
        received_at: parse_date(row.get("received_at")?)?,
        image: ImageReference {
            uri: row.get("image_uri")?,
            sha256: row.get("image_sha256")?,
            content_type: row.get("image_content_type")?,
        },
        acquisition_metadata,
        server_lifecycle_status: lifecycle_status,
    })
}
