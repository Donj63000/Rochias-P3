mod api;
mod domain;
mod reporting;
mod rules;
mod storage;
mod validation;

use axum::{routing::get, Router};

#[tokio::main]
async fn main() {
    let app = Router::new().route("/health", get(api::health));
    let listener = tokio::net::TcpListener::bind("0.0.0.0:8080")
        .await
        .expect("bind 0.0.0.0:8080");

    println!("server-rust listening on http://0.0.0.0:8080");
    axum::serve(listener, app).await.expect("server start");
}
