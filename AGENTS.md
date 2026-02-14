# AGENTS.md — Rochias Peroxyde Tester

Ce document définit le cadre de travail pour les agents IA et les développeurs qui contribuent à ce dépôt.
Objectif : produire une application **fiable, traçable et audit-ready** pour le contrôle du P3 (peroxyde d’hydrogène) chez Rochias.

---

## 1) Contexte métier

- Société : **Rochias** (agroalimentaire, Issoire).
- Process concerné : contrôle du taux de P3 dans le bac de fluidité.
- Problème actuel : mesure manuelle via bandelette colorimétrique + saisie humaine (risque d’erreur).
- Besoin : automatiser la lecture de bandelette, réduire l’erreur humaine, garantir la traçabilité pour audit strict.

Seuils métier de référence :
- **< 100 PPM** : non conforme (taux bas, action corrective).
- **100–500 PPM** : conforme production.
- **> 500 PPM** : non conforme (seuil dépassé, action immédiate).

---

## 2) Vision produit

Le produit "**Rochias Peroxyde Tester**" est composé de 2 briques :

1. **Application Android (Kotlin)**
   - Prise de photo de bandelette.
   - Contrôle des conditions de prise de vue (luminosité/cadre/qualité).
   - Analyse colorimétrique d’une zone sélectionnée.
   - Calcul estimé du taux de PPM.
   - Affichage de la décision (conforme / alerte bas / alerte haut).
   - Historique horodaté consultable.

2. **Serveur / logiciel back-end (Rust)**
   - Réception en temps réel des analyses envoyées par l’app.
   - Stockage robuste et sécurisé des enregistrements.
   - Consultation et tri des données (date/heure/statut).
   - Validation secondaire des analyses (contrôle serveur).
   - Export de traçabilité (ex. PDF/registre d’audit).

Les 2 systèmes doivent rester interconnectés ; en cas d’indisponibilité réseau, l’app doit fonctionner en mode dégradé avec synchronisation différée.

---

## 3) Parcours utilisateur attendu

### Application mobile
- Écran d’accueil :
  - "Effectuer un test"
  - "Historique"
  - "Aide"
- Écran test :
  - Capture photo
  - Sélection de zone
  - Boutons : "Photo conforme ?", "Analyser", "Recommencer"
- Résultat :
  - "Taux de PPM = X"
  - Message contextualisé :
    - <100 : "ATTENTION TAUX BAS" + consigne maintenance/qualité
    - 100–500 : "CONFORME POUR LA PRODUCTION"
    - >500 : "ALERTE SEUIL DÉPASSÉ — PRODUCTION NON CONFORME" + arrêt/alerte/recontrôle

### Logiciel serveur
- Menu principal :
  - "Réglages serveur"
  - "Données application"
  - "Aide"
- Données application :
  - Tableau des scans
  - Tri/filtre date-heure
  - Détail d’un scan (image, PPM, horodatage, statut)
  - Mécanisme de vérification serveur de l’analyse mobile

---

## 4) Exigences non fonctionnelles (obligatoires)

1. **Fiabilité de mesure**
   - Refuser l’analyse si les conditions photo sont insuffisantes (ambiance lumineuse, netteté, cadrage, etc.).
   - Même logique de validation entre mobile et serveur pour limiter les divergences.

2. **Traçabilité & audit**
   - Chaque analyse doit être historisée avec : image, timestamp, PPM, statut de conformité, version algo.
   - Les données doivent être faciles à extraire pour audit.

3. **Robustesse réseau**
   - Si serveur indisponible : stockage local durable + file de retry automatique.
   - Ne jamais perdre une mesure validée localement.

4. **Sécurité & intégrité**
   - Journalisation des erreurs.
   - Protection des données et cohérence des enregistrements.

5. **UX/UI pro (qualité industrielle)**
   - Thème vert/blanc, lisible, sobre.
   - Parcours rapide, sans ambiguïté.
   - Messages actionnables pour opérateurs.

---

## 5) Règles d’ingénierie pour ce dépôt

- Respecter l’architecture existante :
  - `android-app/` : app Kotlin Android.
  - `server-rust/` : backend Rust.
  - `docs/` : documentation fonctionnelle/technique.
- Éviter les changements larges non demandés.
- Préférer des commits atomiques et explicites.
- Ajouter/mettre à jour tests et documentation pour toute fonctionnalité significative.
- Toujours privilégier des noms clairs et orientés domaine métier (scan, ppm, conformité, historique, synchronisation, audit).
- Toute décision de seuil métier doit être centralisée dans des constantes/configs versionnées.

---

## 6) Critères de “Definition of Done”

Une évolution est considérée terminée si :
- le besoin métier est couvert,
- les cas limites sont gérés (photo invalide, réseau KO, seuils dépassés),
- la persistance et la traçabilité sont garanties,
- les tests critiques passent,
- la documentation est à jour,
- l’impact audit/qualité est explicitement mentionné.

---

## 7) Priorités produit

1. Précision et répétabilité de l’analyse.
2. Tolérance aux pannes réseau sans perte de données.
3. Traçabilité exploitable en audit.
4. Simplicité d’usage pour opérateurs terrain.
5. Maintenabilité long terme (code propre, testable, documenté).

---

## 8) Langage de communication

- Interfaces utilisateur : français clair orienté opérateur.
- Messages techniques/logs : explicites, horodatés, exploitables côté support qualité/maintenance.
- Documentation développeur : français ou anglais technique cohérent dans un même fichier.

