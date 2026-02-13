use crate::domain::Analysis;

#[derive(Default)]
pub struct AnalysisStore {
    pub records: Vec<Analysis>,
}
