# JOEY V1

Première base iPhone native SwiftUI pour l'analyseur d'eau Joey (Irripool / Blueriiot).

## Contenu

- Tableau de bord Spa
- Température, pH et Redox
- État global de l'eau
- Scan Bluetooth Low Energy
- Connexion à un périphérique nommé Joey/Blue
- Écran Appareils
- Écrans Analyses et Réglages
- Structure prête pour le décodage GATT réel

## État BLE

La couche CoreBluetooth est fonctionnelle pour scanner et se connecter. Les UUID GATT et le décodage des trames de mesure restent volontairement non figés tant qu'ils ne sont pas confirmés sur l'appareil Joey réel.

## Génération du projet Xcode

Le dépôt utilise XcodeGen :

1. Installer XcodeGen sur le Mac.
2. Dans le dossier du projet, exécuter `xcodegen generate`.
3. Ouvrir `JOEY.xcodeproj` dans Xcode.
4. Sélectionner l'équipe Apple Developer et lancer sur un iPhone réel (le BLE n'est pas représentatif dans le simulateur).

## Sécurité

Ne jamais committer la Key, le numéro de série ou un token de compte dans le dépôt.
