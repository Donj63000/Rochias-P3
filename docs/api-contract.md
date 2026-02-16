# Contrat d'API enrichi (v1)

Base URL: `https://api.peroxyde.local/v1`

## 1) Envoi d'analyse

`POST /analyses`

### Requête
```json
{
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
}
```

### Réponse
- `202 Accepted` si reçu pour traitement.
- `200 OK` si `client_analysis_id` a déjà été reçu avec un payload strictement identique (idempotence).
- `409 Conflict` si `client_analysis_id` existe déjà avec un payload différent.
- `400 Bad Request` si format invalide.
- `422 Unprocessable Entity` si la validation métier (plage PPM, confiance, référence image) échoue.

```json
{
  "server_analysis_id": "2f58c716-9707-4fd1-9f6f-1ba0990f6378",
  "server_lifecycle_status": "recu",
  "received_at": "2026-02-13T09:45:01Z"
}
```

## 2) Accusé de réception serveur

`GET /analyses/{server_analysis_id}/ack`

### Réponse
```json
{
  "server_analysis_id": "2f58c716-9707-4fd1-9f6f-1ba0990f6378",
  "server_lifecycle_status": "valide",
  "validated_schema": true,
  "validated_business_rules": true,
  "queued_for_secondary_review": true,
  "ack_at": "2026-02-13T09:45:02Z"
}
```

> Valeurs possibles de `server_lifecycle_status` :
> `recu`, `valide`, `rejete`, `revu_secondairement`, `exporte_audit`.

## 3) Relecture / validation secondaire

`POST /analyses/{server_analysis_id}/secondary-review`

### Requête
```json
{
  "reviewer_id": "lab-tech-17",
  "decision": "confirmed",
  "comment": "Signal net, cohérent avec étalon.",
  "reviewed_at": "2026-02-13T10:02:00Z"
}
```

### Réponse
```json
{
  "server_analysis_id": "2f58c716-9707-4fd1-9f6f-1ba0990f6378",
  "server_lifecycle_status": "revu_secondairement",
  "secondary_review_status": "completed",
  "decision": "confirmed"
}
```

## 4) Consultation historique

`GET /analyses/history?sample_id=SAMPLE-2026-0001&limit=20&cursor=...`

### Réponse
```json
{
  "items": [
    {
      "server_analysis_id": "2f58c716-9707-4fd1-9f6f-1ba0990f6378",
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
      "received_at": "2026-02-13T09:45:01Z",
      "server_lifecycle_status": "exporte_audit"
    }
  ],
  "next_cursor": null
}
```

## 5) Export registre audit (CSV/PDF)

`POST /analyses/audit-export` (CSV prêt, PDF à venir)

### Requête
```json
{
  "from": "2026-02-01T00:00:00Z",
  "to": "2026-02-29T23:59:59Z",
  "format": "csv",
  "include_image_reference": true
}
```

### Réponse
```json
{
  "export_id": "2f4d7f8f-72dd-4f7c-a88a-4ab65c937d77",
  "status": "ready",
  "format": "csv",
  "download_url": "https://api.peroxyde.local/v1/exports/2f4d7f8f.csv"
}
```

### Schéma registre audit (colonnes CSV / sections PDF)
- `server_analysis_id`
- `client_analysis_id`
- `sample_id`
- `ppm_estime`
- `ppm_min`
- `ppm_max`
- `compliance_status`
- `analysis_result`
- `recommended_action`
- `confidence`
- `analysis_rules_version`
- `calibration_version`
- `captured_at`
- `received_at`
- `server_lifecycle_status`
- `image.uri`
- `image.sha256`
- `image.content_type`
- `acquisition.light_condition`
- `acquisition.ambient_lux`
- `acquisition.temperature_celsius`
- `acquisition.humidity_percent`
- `acquisition.camera_focus_score`
- `acquisition.rejection_flags.low_light`
- `acquisition.rejection_flags.blur_detected`
- `acquisition.rejection_flags.framing_issue`
- `acquisition.rejection_flags.overexposed`
- `acquisition.rejection_flags.strip_not_detected`
- `device.platform`
- `device.model`
- `device.os_version`
- `device.app_version`

## Audit trail (événements attendus)

Le backend doit tracer les événements suivants :
- `analysis_received`
- `analysis_schema_validated`
- `analysis_business_validated`
- `analysis_rejected`
- `analysis_secondary_review_completed`
- `analysis_exported_audit`
- `analysis_history_viewed`
