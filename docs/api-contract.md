# Contrat d'API initial (v0)

Base URL: `https://api.peroxyde.local/v1`

## 1) Envoi d'analyse

`POST /analyses`

### Requête
```json
{
  "client_analysis_id": "a8e68c43-8fba-4cad-bb7a-3d6b5d9af2aa",
  "sample_id": "SAMPLE-2026-0001",
  "captured_at": "2026-02-13T09:45:00Z",
  "result": "positif",
  "confidence": 0.93,
  "device": {
    "platform": "android",
    "app_version": "0.1.0"
  }
}
```

### Réponse
- `202 Accepted` si reçu pour traitement.
- `400 Bad Request` si format invalide.

```json
{
  "server_analysis_id": "2f58c716-9707-4fd1-9f6f-1ba0990f6378",
  "status": "received",
  "received_at": "2026-02-13T09:45:01Z"
}
```

## 2) Accusé de réception serveur

`GET /analyses/{server_analysis_id}/ack`

### Réponse
```json
{
  "server_analysis_id": "2f58c716-9707-4fd1-9f6f-1ba0990f6378",
  "status": "accepted",
  "validated_schema": true,
  "queued_for_secondary_review": true,
  "ack_at": "2026-02-13T09:45:02Z"
}
```

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
      "result": "positif",
      "secondary_review_status": "completed",
      "created_at": "2026-02-13T09:45:01Z"
    }
  ],
  "next_cursor": null
}
```

## Audit trail (événements attendus)

Le backend doit tracer les événements suivants :
- `analysis_received`
- `analysis_schema_validated`
- `analysis_secondary_review_completed`
- `analysis_history_viewed`
