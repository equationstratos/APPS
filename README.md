# Boîte à outils Survie

34 petites applications Android hors ligne, sans publicité, sans pistage, sans compte. Une app par outil, interface sombre commune, textes en français.

## Installer

1. Ouvrir le dossier [`apk/`](apk/) et télécharger l'APK voulu (0,6 à 1,5 Mo).
2. Autoriser l'installation depuis cette source si Android le demande.
3. Les mises à jour s'installent par-dessus la version précédente : toutes les apps sont signées avec la même clé (`keystore/survie.keystore`).

Si une version installée avant octobre 2026 refuse la mise à jour (« conflit de package »), désinstallez-la une fois : elle avait été signée avec une clé temporaire.

Les APK sont recompilés automatiquement par GitHub Actions à chaque modification (`.github/workflows/build-all.yml`).

## Les outils

| App | Rôle |
|-----|------|
| Boussole | Cap magnétique lissé, 16 points cardinaux, cap cible avec écart |
| Niveau | Niveau à bulle 2D à plat, niveau tubulaire sur la tranche, calibrage |
| Rapporteur | Inclinomètre avec fil à plomb, maintien de la mesure, zéro relatif |
| Règle | Règle cm et pouces plein écran, calibrage par carte bancaire, curseur de mesure |
| Convertisseur | Longueur, masse, température, volume, vitesse, surface, pression |
| Calculatrice | Calculatrice standard, affichage français, gestion des erreurs |
| IMC | Indice de masse corporelle, catégories OMS, poids de forme |
| Compteur | Compteur manuel, boutons de volume, mémorisé |
| Chronomètre | Chronomètre avec tours et minuteur avec alarme |
| Métronome | Tempo exact, mesure, tap tempo |
| Fuseaux | Heure locale et horloge mondiale |
| Lampe | Lampe torche, SOS morse, stroboscope, écran blanc |
| Loupe | Caméra arrière zoomée, mise au point, lampe, image figée |
| Miroir | Caméra frontale, éclairage d'appoint, image figée |
| Sonomètre | Niveau sonore approximatif, statistiques, historique |
| Luxmètre | Éclairement en lux, min/max, échelle de référence |
| Accéléromètre | Axes X/Y/Z avec ou sans gravité, graphique |
| Vibromètre | Intensité des vibrations, pic, graphique |
| Thermomètre | Température ambiante si capteur, sinon température batterie (indicative) |
| Détecteur | Détecteur de métaux ferreux et d'aimants par magnétomètre |
| Podomètre | Pas du jour, distance, objectif |
| Vitesse GPS | Vitesse, trajet, altitude, coordonnées à copier pour les secours |
| Batterie | Niveau, état, santé, température, courant |
| Réseau | Type de connexion et test de débit (seule app qui utilise Internet) |
| Pouls | Fréquence cardiaque par le doigt sur la caméra, mode manuel, historique (indicatif) |
| Soleil et Lune | Lever, coucher, crépuscules, durée du jour, position du soleil, phases et lever de lune |
| Altimètre | Altitude barométrique étalonnable, dénivelé, pression et tendance météo |
| Morse | Émission par lampe, son, vibration ou écran, décodage manuel ou au micro, apprentissage |
| Sifflet | Sifflet, SOS sonore, signal de détresse en montagne, sirène, fonctionne écran éteint |
| Signaux | Numéros d'urgence, alphabet radio, signaux sol-air et corporels, procédures radio, alertes |
| Accordeur | Accordeur chromatique et par instrument, diapason |
| Stocks | Inventaire des réserves, dates de péremption, jours d'autonomie du foyer |
| Carnet | Notes hors ligne avec horodatage et position GPS |
| Check-lists | Listes cochables : kit d'urgence, sac d'évacuation, coupure de courant, voiture… |

Chaque app ne demande que les permissions dont elle a besoin. Seule « Réseau » accède à Internet, uniquement pendant un test lancé par l'utilisateur.

## Structure

Chaque dossier (`boussole/`, `niveau/`…) est un projet Android indépendant : Kotlin, Jetpack Compose, minSdk 26. Le dossier `ruview/` est un projet séparé (détection de présence par Wi-Fi) qui n'est pas compilé par le CI.
