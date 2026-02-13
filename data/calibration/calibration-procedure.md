# Procédure de recalibration périodique

## Préparation
1. Nettoyer la zone de prise de vue et installer le fond neutre.
2. Vérifier l'éclairage stable dans la plage 350–900 lux.
3. Charger la version de calibration active et identifier le lot de bandelettes.

## Acquisition
1. Pour chaque patch de référence, capturer 3 images conformes.
2. Rejeter les images floues, saturées ou avec angle hors tolérance.
3. Calculer la médiane Lab pour chaque patch PPM.

## Validation
1. Mesurer la dérive `ΔE00` par rapport à la calibration active.
2. Si `ΔE00` médian > 2.0, créer une nouvelle version de calibration.
3. Exécuter le jeu de non-régression `data/validation/expected-results.csv`.

## Publication
1. Générer un identifiant de version (ex: `calib-2026-02-13-a`).
2. Archiver la version précédente et publier la nouvelle.
3. Notifier les opérateurs de la date et du motif de recalibration.
