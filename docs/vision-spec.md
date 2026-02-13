# Spécification vision — Bandelette P3

## 1. Conditions de prise de vue (pré-analyse)

### 1.1 Distance, angle, fond
- **Distance capteur ↔ bandelette** : 18 à 24 cm (cible nominale : 20 cm).
- **Inclinaison de la caméra** : angle de 90° ± 7° par rapport au plan de la bandelette.
- **Rotation de la bandelette** : tolérance de ± 15° autour de l'axe horizontal.
- **Occupation du cadre** : la zone utile de la bandelette doit couvrir 20 % à 60 % de l'image.
- **Fond autorisé** : neutre mat (gris clair ou blanc), sans texture dominante ni reflet spéculaire.
- **Stabilité** : flou de mouvement interdit (variance Laplacien min = 120).

### 1.2 Luminosité et homogénéité
- **Lux minimal** : 350 lux.
- **Lux maximal** : 900 lux.
- **Température de couleur recommandée** : 4 000 K à 5 500 K.
- **Uniformité d'éclairage** : ratio min/max de luminance sur fond ≤ 1.35.
- **Saturation capteur** : pixels saturés (R=255 ou G=255 ou B=255) ≤ 2.5 %.

## 2. Règles de rejet — « Ambiance mauvaise prise refusée »

Le message **« Ambiance mauvaise prise refusée »** est affiché si **au moins une** condition suivante est vraie :

1. Luminosité hors plage (lux < 350 ou > 900).
2. Angle de prise de vue hors tolérance (écart > 7°).
3. Bandelette non détectée avec confiance suffisante (< 0.75).
4. Flou mesuré (variance Laplacien < 120).
5. Fond non conforme (teinte dominante non neutre, texture excessive, reflet).
6. Saturation excessive (> 2.5 % de pixels saturés).
7. ROI couleur partiellement hors bandelette (< 95 % de recouvrement).

## 3. Pipeline d'analyse colorimétrique

1. **Détection bandelette**
   - Détection contour + modèle léger de segmentation.
   - Sortie : polygone bandelette + score de confiance `strip_conf`.

2. **Extraction ROI**
   - Calcul de la zone réactive (ROI) via repères géométriques normalisés.
   - Vérification de couverture minimale et exclusion des bords.

3. **Correction d’illumination**
   - Correction "gray-world" + normalisation locale (CLAHE sur canal L*).
   - Rejet si correction requise dépasse un facteur de compensation de 1.8.

4. **Conversion d’espace colorimétrique**
   - Conversion RGB linéaire → CIE Lab (illuminant D65).
   - Utilisation de `(a*, b*)` comme axes principaux, `L*` comme variable de contrôle.

5. **Interpolation PPM**
   - Calcul de distance colorimétrique `ΔE00` entre ROI et points de calibration.
   - Interpolation spline monotone entre points PPM adjacents.
   - Sortie : `ppm_estime`, `ppm_min`, `ppm_max`, `confidence`.

## 4. Seuils de confiance et messages opérateur

### 4.1 Seuils
- `strip_conf` (détection bandelette) :
  - **OK** si ≥ 0.75
  - **Alerte** si [0.60, 0.75[
  - **Rejet** si < 0.60
- `color_conf` (qualité rapprochement colorimétrique) :
  - **OK** si ≥ 0.80
  - **Alerte** si [0.65, 0.80[
  - **Rejet** si < 0.65
- **Confiance globale** (`confidence`) = min(`strip_conf`, `color_conf`, `illum_conf`).

### 4.2 Messages opérateur
- **Rejet ambiance** :
  - « Ambiance mauvaise prise refusée »
  - Action : repositionner la bandelette, stabiliser le téléphone, corriger l'éclairage.
- **Analyse incertaine** (`confidence` < 0.80) :
  - « Analyse possible mais incertaine — refaire une prise pour confirmation ».
- **Analyse validée** (`confidence` ≥ 0.80) :
  - Afficher `Taux de PPM = X` puis statut métier :
    - `< 100` : « ATTENTION TAUX BAS »
    - `100–500` : « CONFORME POUR LA PRODUCTION »
    - `> 500` : « ALERTE SEUIL DÉPASSÉ — PRODUCTION NON CONFORME »

## 5. Jeu de calibration

## 5.1 Arborescence
- `data/calibration/reference-swatches.csv` : couleurs de référence associées aux PPM.
- `data/calibration/calibration-procedure.md` : procédure opératoire de recalibration.

## 5.2 Exigences de calibration
- Minimum 7 points de calibration couvrant la plage 0–600 PPM.
- Références acquises sous conditions contrôlées (section 1).
- Chaque point PPM est stocké avec médiane Lab et écart-type (`sigma_deltaE`).

## 6. Méthode de recalibration périodique

- **Fréquence nominale** : toutes les 2 semaines.
- **Recalibration exceptionnelle** :
  - changement de lot de bandelettes,
  - changement de smartphone/capteur,
  - dérive mesurée `ΔE00` médian > 2.0 sur le jeu de contrôle.
- **Processus** :
  1. Capturer 3 images par patch de référence.
  2. Calculer médiane Lab par patch.
  3. Comparer au référentiel courant.
  4. Publier une nouvelle version horodatée (`calibration_version`).
  5. Exécuter les tests de non-régression avant activation.

## 7. Tests de non-régression d’analyse

## 7.1 Jeu d’images de validation
- Dossier : `data/validation/`
- Fichier d’oracle : `data/validation/expected-results.csv`
- Le jeu doit inclure :
  - éclairage faible mais acceptable,
  - éclairage nominal,
  - éclairage fort mais acceptable,
  - cas rejetés (flou, reflet, angle hors tolérance, fond non neutre),
  - cas limites de décision métier (95, 100, 500, 505 PPM).

## 7.2 Critères de passage
- Erreur absolue moyenne (MAE) sur cas acceptés : ≤ 15 PPM.
- 95e percentile erreur absolue : ≤ 30 PPM.
- Taux de faux conformes (hors [100, 500]) : 0 % sur jeu de validation.
- Taux de rejet attendu sur images volontairement dégradées : ≥ 98 %.

## 7.3 Exécution recommandée (CI)
- Chargement du dataset + oracle.
- Rejeu du pipeline complet image par image.
- Comparaison : statut (accepté/rejeté), PPM estimé, niveau de confiance, message opérateur.
- Génération d’un rapport versionné pour audit interne.
