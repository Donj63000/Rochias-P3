use std::sync::Arc;

use tokio::sync::RwLock;
use uuid::Uuid;

use crate::domain::Analysis;

#[derive(Clone, Default)]
pub struct AnalysisStore {
    records: Arc<RwLock<Vec<Analysis>>>,
}

impl AnalysisStore {
    pub async fn insert(&self, analysis: Analysis) {
        self.records.write().await.push(analysis);
    }

    pub async fn find_by_id(&self, id: Uuid) -> Option<Analysis> {
        self.records
            .read()
            .await
            .iter()
            .find(|analysis| analysis.id == id)
            .cloned()
    }

    pub async fn list_paginated(
        &self,
        limit: usize,
        offset: usize,
        sample_id: Option<&str>,
    ) -> (Vec<Analysis>, Option<String>) {
        let records = self.records.read().await;
        let mut filtered: Vec<Analysis> = records
            .iter()
            .filter(|analysis| {
                sample_id
                    .map(|expected| analysis.sample_id == expected)
                    .unwrap_or(true)
            })
            .cloned()
            .collect();

        filtered.sort_by(|a, b| b.received_at.cmp(&a.received_at));

        let total = filtered.len();
        let items = filtered
            .into_iter()
            .skip(offset)
            .take(limit)
            .collect::<Vec<_>>();
        let next_offset = offset + items.len();
        let next_cursor = (next_offset < total).then(|| next_offset.to_string());

        (items, next_cursor)
    }
}
