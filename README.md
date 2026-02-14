# 🧪 Rochias Peroxyde Tester

> **Application mobile Android (Kotlin) + serveur back-end Rust**
> pour la vérification fiable du taux de peroxyde d’hydrogène (P3) via lecture de bandelettes colorimétriques.

---

## 📌 Présentation officielle du projet

**Rochias Peroxyde Tester** est un projet conçu pour la société **Rochias (Issoire)**, dans le cadre du contrôle qualité en environnement agroalimentaire.

Aujourd’hui, le contrôle du taux de P3 dans le bac de fluidité repose sur une méthode manuelle :
- trempage de bandelette,
- lecture visuelle de la couleur,
- saisie humaine du résultat.

Cette méthode peut introduire des écarts d’interprétation et des erreurs de transcription.

L’objectif de cette application est de mettre en place une solution **numérique, robuste et traçable**, répondant aux exigences d’audit strictes.

---

## 🎯 Besoin métier auquel l’application répondra

Quand elle sera pleinement développée, l’application répondra à un besoin central :

### **Fiabiliser la décision qualité en production**

Le système permettra :
- de **scanner une bandelette** dans un cadre de prise de vue contrôlé,
- d’**estimer automatiquement le taux de PPM** à partir de la couleur,
- de **déterminer la conformité** selon les seuils métier,
- de **réduire l’erreur humaine**,
- de **conserver une traçabilité horodatée complète** pour audit.

Seuils de référence visés :
- **< 100 PPM** → Alerte taux bas,
- **100 à 500 PPM** → Conforme pour la production,
- **> 500 PPM** → Alerte seuil dépassé, production non conforme.

---

## 🏗️ Architecture cible

Le projet est structuré autour de deux composants interconnectés :

### 1) Application Android (Kotlin)
- prise de photo de bandelette,
- vérification des conditions de prise (lumière, netteté, cadrage),
- analyse colorimétrique de la zone utile,
- affichage du résultat et de la recommandation opérationnelle,
- enregistrement local de l’historique des scans.

### 2) Serveur back-end (Rust)
- réception des analyses en temps réel,
- validation secondaire des résultats,
- stockage sécurisé des enregistrements (image + PPM + horodatage + statut),
- consultation et tri des données,
- production de traces exploitables en audit.

---

## 🔁 Résilience & continuité de service

Le système est pensé pour ne pas bloquer la production :
- en cas de coupure réseau, l’application conserve les enregistrements localement,
- une synchronisation différée réessaie l’envoi vers le serveur,
- les données validées ne doivent pas être perdues.

---

## ✅ Valeur attendue pour Rochias

À terme, **Rochias Peroxyde Tester** doit apporter :
- une méthode de contrôle **plus fiable et reproductible**,
- une interface claire pour les opérateurs,
- un historique consultable et structuré,
- une capacité de justification documentaire face à un audit qualité exigeant,
- un socle logiciel maintenable pour les évolutions futures.

---

## 📁 Structure du dépôt

- `android-app/` → application Android Kotlin
- `server-rust/` → back-end Rust
- `docs/` → documentation fonctionnelle et technique
- `data/` → données et ressources associées au projet

---

## 📝 Statut

Le projet est en cours de structuration et de développement.
Ce README formalise la vision produit et le besoin métier cible.

---

<div align="right">
  <sub>Document officiel de présentation — signé <strong>Val</strong></sub>
</div>
