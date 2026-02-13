use serde::{Deserialize, Serialize};

pub const ANALYSIS_RULES_VERSION: &str = "analysis-rules/v1";

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct VersionedAnalysisDecision {
    pub contract_version: String,
    pub analysis_result: String,
    pub compliance_status: String,
    pub recommended_action: String,
}

pub fn evaluate_ppm(ppm: u32) -> VersionedAnalysisDecision {
    if ppm < 100 {
        return VersionedAnalysisDecision {
            contract_version: ANALYSIS_RULES_VERSION.to_string(),
            analysis_result: "ATTENTION TAUX BAS".to_string(),
            compliance_status: "MAINTENANCE_QUALITE".to_string(),
            recommended_action: "Appliquer les consignes maintenance/qualité.".to_string(),
        };
    }

    if ppm <= 500 {
        return VersionedAnalysisDecision {
            contract_version: ANALYSIS_RULES_VERSION.to_string(),
            analysis_result: "CONFORME POUR LA PRODUCTION".to_string(),
            compliance_status: "CONFORME".to_string(),
            recommended_action: "Poursuivre la production normale.".to_string(),
        };
    }

    VersionedAnalysisDecision {
        contract_version: ANALYSIS_RULES_VERSION.to_string(),
        analysis_result: "ALERTE seuil dépassé".to_string(),
        compliance_status: "SEUIL_DEPASSE".to_string(),
        recommended_action: "Arrêt/notification/recontrôle immédiats.".to_string(),
    }
}

#[cfg(test)]
mod tests {
    use super::{evaluate_ppm, ANALYSIS_RULES_VERSION};
    use serde::Deserialize;

    #[derive(Debug, Deserialize)]
    struct ContractCase {
        ppm: u32,
        analysis_result: String,
        compliance_status: String,
        recommended_action: String,
    }

    #[derive(Debug, Deserialize)]
    struct Contract {
        contract_version: String,
        cases: Vec<ContractCase>,
    }

    #[test]
    fn evaluates_frontier_values() {
        let at_99 = evaluate_ppm(99);
        assert_eq!(at_99.analysis_result, "ATTENTION TAUX BAS");

        let at_100 = evaluate_ppm(100);
        assert_eq!(at_100.analysis_result, "CONFORME POUR LA PRODUCTION");

        let at_500 = evaluate_ppm(500);
        assert_eq!(at_500.analysis_result, "CONFORME POUR LA PRODUCTION");

        let at_501 = evaluate_ppm(501);
        assert_eq!(at_501.analysis_result, "ALERTE seuil dépassé");
    }

    #[test]
    fn matches_json_contract_cases() {
        let raw_contract =
            std::fs::read_to_string("../data/validation/analysis_rules_contract_v1.json")
                .expect("read contract json");
        let contract: Contract = serde_json::from_str(&raw_contract).expect("parse contract json");

        assert_eq!(contract.contract_version, ANALYSIS_RULES_VERSION);

        for case in contract.cases {
            let decision = evaluate_ppm(case.ppm);
            assert_eq!(decision.contract_version, contract.contract_version);
            assert_eq!(decision.analysis_result, case.analysis_result);
            assert_eq!(decision.compliance_status, case.compliance_status);
            assert_eq!(decision.recommended_action, case.recommended_action);
        }
    }
}
