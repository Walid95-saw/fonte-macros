/* Regenere altstore.json a partir de l'IPA reellement compile.
 *
 * AltStore refuse une installation si la version annoncee par la source differe
 * de celle inscrite dans l'app. Ecrire le catalogue a la main expose a cet ecart :
 * on lit donc les valeurs dans l'Info.plist du binaire, jamais ailleurs.
 *
 * Usage : node outils/catalogue.js <version> <build> <minOS> <taille> <etiquette>
 */
const fs = require('fs');

const [version, build, minOS, taille, etiquette] = process.argv.slice(2);
if (!version || !taille || !etiquette) {
  console.error('arguments manquants'); process.exit(1);
}

const DEPOT = 'https://github.com/Walid95-saw/fonte-macros';
const BRUT = 'https://raw.githubusercontent.com/Walid95-saw/fonte-macros/main';
const ICONE = BRUT + '/ios/App/App/Assets.xcassets/AppIcon.appiconset/AppIcon-512%402x.png';
const url = `${DEPOT}/releases/download/${etiquette}/FonteEtMacros-non-signe.ipa`;

/* on garde l'historique des versions deja publiees */
let anciennes = [];
try {
  const precedent = JSON.parse(fs.readFileSync('altstore.json', 'utf8'));
  anciennes = (precedent.apps[0].versions || []).filter(v => v.version !== version);
} catch (e) { /* premier passage */ }

const nouvelle = {
  version,
  buildVersion: build,
  date: new Date().toISOString(),
  localizedDescription: 'Compilation automatique depuis la branche principale.',
  downloadURL: url,
  size: Number(taille),
  minOSVersion: minOS || '13.0'
};

const catalogue = {
  name: 'Fonte & Macros',
  identifier: 'com.fontemacros.source',
  subtitle: 'Nutrition et musculation',
  description: 'Catalogue personnel. A ajouter comme source dans AltStore.',
  iconURL: ICONE,
  headerURL: ICONE,
  website: DEPOT,
  tintColor: '9E4426',
  apps: [{
    name: 'Fonte & Macros',
    bundleIdentifier: 'com.fontemacros.app',
    developerName: 'Walid',
    subtitle: 'Calories, macros, poids et entrainement',
    localizedDescription: "Calcul des besoins caloriques et des macros, suivi du poids, journal alimentaire sur 204 aliments photographies, repas composes, plan de la semaine, programmes de musculation avec mouvements animes et programmes de cardio. En francais et en arabe. Les donnees restent sur l'appareil.",
    iconURL: ICONE,
    tintColor: '9E4426',
    category: 'lifestyle',
    screenshotURLs: [],
    /* champs attendus par les anciennes versions d'AltStore */
    version,
    versionDate: nouvelle.date,
    versionDescription: nouvelle.localizedDescription,
    downloadURL: url,
    size: Number(taille),
    permissions: [],
    /* et le tableau attendu par les recentes */
    versions: [nouvelle, ...anciennes]
  }],
  news: []
};

fs.writeFileSync('altstore.json', JSON.stringify(catalogue, null, 2) + '\n');
console.log(`catalogue : version ${version} (build ${build}), ${taille} octets, ${etiquette}`);
