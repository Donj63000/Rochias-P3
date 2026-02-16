mod api;
mod calibration;
mod domain;
mod reporting;
mod rules;
mod storage;
mod validation;

use axum::{
    routing::{get, post},
    Router,
};

use crate::storage::AnalysisStore;

#[tokio::main]
async fn main() {
    let database_url =
        std::env::var("DATABASE_URL").unwrap_or_else(|_| "data/analyses.sqlite".to_string());
    let store = AnalysisStore::new(&database_url)
        .await
        .expect("sqlite store initialization");

    let app = Router::new()
        .route("/health", get(api::health))
        .route("/v1/analyses", post(api::create_analysis))
        .route("/v1/analyses/history", get(api::analyses_history))
        .route("/v1/analyses/:id/ack", get(api::analysis_ack))
        .route("/v1/analyses/audit-export", post(api::export_audit_csv))
        .with_state(store);

    let listener = tokio::net::TcpListener::bind("0.0.0.0:8080")
        .await
        .expect("bind 0.0.0.0:8080");

    println!("server-rust listening on http://0.0.0.0:8080");
    axum::serve(listener, app).await.expect("server start");
}
