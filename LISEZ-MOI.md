# Fonte & Macros — obtenir l'IPA iPhone

Ce dossier contient l'application complète : la page web, le projet Android et le
projet iOS. L'APK est déjà compilé. Pour l'IPA, il faut un Mac — mais tu n'as pas
besoin d'en posséder un : GitHub en prête un, gratuitement.

---

## Pourquoi ce détour

Un `.ipa` ne peut être compilé que par Xcode, qui n'existe que sur macOS. Et un
`.ipa` non signé ne s'installe sur aucun iPhone : Apple exige une signature.

La solution tient en deux temps :

1. **GitHub Actions** compile l'app sur un vrai Mac et te rend un IPA non signé.
2. **Sideloadly**, sur ton PC Windows, le signe avec ton identifiant Apple gratuit
   et l'installe sur ton iPhone.

Aucun compte payant n'est nécessaire. Une limite : l'app expire au bout de 7 jours
et doit être réinstallée. C'est une règle d'Apple pour les comptes gratuits, pas
une limite de l'application.

---

## Étape 1 — Mettre le projet sur GitHub

1. Crée un compte sur [github.com](https://github.com) si tu n'en as pas.
2. Crée un dépôt **public** — appelons-le `fonte-macros`.
   Public, c'est important : les runners macOS sont gratuits sur un dépôt public,
   et facturés sur un dépôt privé.
3. Sur la page du dépôt vide, clique **uploading an existing file**, puis glisse
   **tout le contenu de ce dossier** (pas le dossier lui-même).

   N'envoie pas `node_modules` ni `android/build` s'ils apparaissent : ils sont
   régénérés automatiquement et pèsent lourd pour rien.

---

## Étape 2 — Lancer la compilation

1. Onglet **Actions** du dépôt.
2. Dans la colonne de gauche, **IPA iOS**.
3. Bouton **Run workflow**, puis **Run workflow** à nouveau pour confirmer.
4. Attends une dizaine de minutes. Un rond vert signale la fin.
5. Clique sur la ligne terminée, puis télécharge **FonteEtMacros-ipa** en bas de page.
   C'est un `.zip` : dézippe-le pour obtenir le `.ipa`.

Si la compilation échoue, ouvre l'étape en rouge et copie-moi le message : je corrige.

---

## Étape 3 — Installer sur l'iPhone

1. Installe [Sideloadly](https://sideloadly.io) sur ton PC, ainsi qu'**iTunes**
   depuis le site d'Apple — pas depuis le Microsoft Store, la version du Store ne
   convient pas.
2. Branche l'iPhone en USB, déverrouille-le, accepte **Faire confiance**.
3. Ouvre Sideloadly, glisse le `.ipa` dedans, saisis ton identifiant Apple, **Start**.
4. Sur l'iPhone : **Réglages → Général → VPN et gestion de l'appareil**, touche ton
   identifiant, puis **Faire confiance**.

L'app apparaît sur l'écran d'accueil. Au bout de 7 jours, refais l'étape 3 — ou
installe **AltStore**, qui renouvelle la signature automatiquement tant que ton PC
est sur le même réseau Wi-Fi.

---

## Ce qui marchera, et ce qui risque de ne pas marcher

Tout fonctionne : les calories et macros, le suivi du poids, le journal, les repas
composés, la semaine, les 204 aliments avec leurs photos, les mouvements animés,
les programmes de musculation et de cardio, le français et l'arabe.

**L'onglet Montre & santé est l'inconnue.** Lire Apple Santé demande une
autorisation spéciale, le *HealthKit entitlement*, qu'Apple n'accorde de façon
fiable qu'aux comptes développeur payants. Avec un identifiant gratuit, la
resignature peut la perdre : l'app s'ouvre normalement mais l'onglet reste vide, et
l'application n'apparaît pas dans les sources de l'app Santé.

Si ça arrive, il y a deux issues :

- **Un compte Apple Developer** à 99 € par an. Tu crées alors un App ID explicite
  avec la capacité HealthKit, et tout fonctionne — y compris les données de ta
  montre Huawei, qui écrit directement dans Apple Santé sur iPhone.
- **S'en passer** : saisir ton poids à la main, ce que l'app permet déjà.

Je préfère te le dire avant que tu passes une heure dessus plutôt qu'après.

---

## Si tu as un Mac

Tout ce détour devient inutile :

```
npm install
npx cap sync ios
open ios/App/App.xcworkspace
```

Dans Xcode : onglet **Signing & Capabilities**, choisis ton équipe, ajoute la
capacité **HealthKit**, branche l'iPhone et appuie sur ▶.
