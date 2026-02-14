# Procédure de recalibration périodique

## Préparation
1. Nettoyer la zone de prise de vue, installer le fond neutre et fixer la distance caméra/patch.
2. Verrouiller le protocole photo contrôlé `studio-550lux-d65-v1` (éclairage D65, 550 ± 30 lux, balance des blancs fixe, angle 90°).
3. Charger la version de calibration active et identifier le lot de bandelettes physiques 0–500 ppm.

## Acquisition (minimum 3 acquisitions par patch)
1. Capturer au moins 3 images conformes pour chaque patch 0, 50, 100, 200, 300, 400 et 500 ppm.
2. Rejeter les images floues, saturées, hors cadre ou avec ombrage.
3. Enregistrer chaque mesure Lab dans `reference-swatches-acquisitions.csv` avec `calibration_version` horodatée (ex: `calib-2026-02-14T09:30:00Z`).

## Génération des médianes et sigma
1. Lancer `python3 data/calibration/generate_reference_swatches.py`.
2. Le script recalcule, patch par patch :
   - la médiane `L*`, `a*`, `b*`,
   - `sigma_deltaE` (médiane des `ΔE76` des acquisitions autour de la médiane Lab),
   - la version de calibration et le protocole de capture.
3. Publier le résultat dans `reference-swatches.csv`.

## Validation métier obligatoire
1. Vérifier la cohérence patchs physiques 0–500 ppm vs Lab générés (`sigma_deltaE` faible et stable).
2. Vérifier le sens de variation colorimétrique attendu :
   - 0–50 ppm : jaune net (`b*` élevé),
   - 100–500 ppm : couleur plus terne/gris/brun (`b*` décroissant, luminance décroissante).
3. Exécuter les tests de non-régression (dont détection d’inversion de tendance colorimétrique).

## Publication
1. Committer ensemble : acquisitions brutes, CSV agrégé et documentation.
2. Archiver la calibration précédente et notifier les opérateurs (date, motif, version).
3. Reporter explicitement l’impact traçabilité/audit (version horodatée + protocole figé).
