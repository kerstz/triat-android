# Handoff : UI Lecteur Triat (Android)

## Vue d'ensemble
Refonte de l'interface du lecteur de musique **Triat** pour Android : une UI simple, monochrome, thème sombre, inspirée de Cider (coverflow 3D) mais épurée. Deux sources audio mélangées (YouTube + SoundCloud) avec badge de source. Cinq écrans : Accueil, Lecteur plein écran, Recherche, Bibliothèque, Réglages de source.

Cible réelle : app Android **Koda** rebrandée Triat — Kotlin + Jetpack Compose + Material 3 (Expressive). Paquet `com.ivor.ivormusic`, appId `org.triat.mobile`.

## À propos des fichiers de design
Le fichier `Lecteur Triat.dc.html` de ce dossier est une **référence de design en HTML** — un prototype qui montre l'apparence et le comportement visés, **pas du code à copier tel quel**. La tâche est de **recréer fidèlement ces écrans dans l'app Compose existante** en réutilisant ses patterns (thème Material 3, `MaterialTheme.colorScheme`, composables maison, NavHost existant). Le HTML code en dur un thème sombre neutre pour la lisibilité de la maquette ; **en production, câble les couleurs sur le thème Material You dynamique** (défaut du projet) — les hex ci-dessous sont les valeurs de repli / l'intention visuelle quand le thème dynamique n'est pas dispo.

## Fidélité
**Haute fidélité (hifi).** Couleurs, typo, espacements et rayons sont définitifs. Recréer au pixel près avec les composants Material 3 du projet. Seules les pochettes sont des placeholders en dégradé (remplacées par les vraies vignettes YouTube/SoundCloud à l'exécution).

---

## Système visuel global

### Couleurs (repli thème sombre — sinon `colorScheme`)
| Rôle | Hex | Compose (sombre) |
|---|---|---|
| Fond principal | `#0F1013` | `background` / `surface` |
| Fond écran lecteur (haut) | `#201B26` → `#0E0C12` (dégradé vertical) | gradient sur `surface` |
| Surface carte / ligne | `#17181C` | `surfaceContainer` |
| Surface élevée (mini-lecteur) | `#242027` | `surfaceContainerHigh` |
| Surface onglet actif (pilule nav) | `#302C38` | `secondaryContainer` |
| Bordure / séparateur | `#1E1E22`, `#26262B` | `outlineVariant` |
| Texte principal | `#E4E2E6` / `#E9E6EF` | `onSurface` |
| Texte secondaire | `#8B8A92` / `#9A99A1` | `onSurfaceVariant` |
| Accent / éléments actifs (bouton play, toggles, scrubber) | `#E9E6EF` / `#D8D4E2` | `primary` |
| Texte sur accent (icône dans bouton play) | `#15131A` | `onPrimary` |

Pas de couleur de marque vive : **monochrome violet-gris**. Les dégradés de pochette ne sont que des placeholders.

### Typographie
- Police : **Hanken Grotesk** (dans la maquette). En prod, garde la typo sobre du thème Triat — mappe sur les rôles M3 ci-dessous. Pas de Montserrat étiré.
- Titre de page (« Accueil », « Recherche »…) : **22px / 700**, letter-spacing ~`0.04em` → `headlineSmall`.
- Titre de section (« Pour vous », « Tes playlists ») : **17px / 600** → `titleMedium`.
- Titre de piste / playlist : **14.5px / 600** → `titleSmall` / `bodyLarge`.
- Sous-texte (artiste, nb titres) : **12.5px / 400**, couleur `onSurfaceVariant` → `bodyMedium`.
- Titre du lecteur plein écran : **23px / 700** → `headlineSmall`.
- Labels de nav : **11px**, actif 600 → `labelMedium`.
- Micro-label (« EN LECTURE ») : **11px**, letter-spacing `0.14em`, majuscules → `labelSmall`.

### Rayons (coins, Material 3 standard — pas d'exagéré)
- Cartes de pochette carrousel : **16px**
- Pochette centrale lecteur : **20px**
- Lignes de liste / cartes playlist : **14px**
- Vignette dans une ligne : **9–10px**
- Pilule onglet nav actif : **15px** (hauteur 30px → full pill)
- Champ de recherche : **26px** (full pill)
- Chips de filtre : **20px**
- Badge source : **5–9px**
- Toggle (switch) : piste 15px (full pill), pastille 24px

### Icônes
Style **outline, trait 2px** (Material Symbols Outlined). Exceptions remplies (fill) : bouton play/pause central, pochettes placeholder, onglet nav actif.

### Logo Triat
3 points formant un triangle « lecture » : deux cercles à gauche (haut `cx=7 cy=6`, bas `cx=7 cy=18`), un à droite centré (`cx=17.5 cy=12`), rayon 2.5 sur un viewBox 24×24, couleur `#E9E6EF`. À fournir en vecteur Compose (`ImageVector`) ou drawable.

### Cadre d'appareil (maquette uniquement)
Téléphone 344×744, rayon 46px, encoche « pill » centrée. **À ignorer en prod** — c'est juste le bezel de présentation.

---

## Navigation
Barre de nav basse à **5 onglets** (Material 3 `NavigationBar`) : **Accueil · Recherche · Biblio · Favoris · Réglages**.
- Onglet actif : pilule `#302C38` (48–52×30) derrière l'icône + label 600 en `onSurface`.
- Onglets inactifs : icône + label en `onSurfaceVariant` (`#8B8A92`), pas de pilule.
- Fond barre `#141417`, bordure haute 1px `#1E1E22`.
- Icônes : Accueil = maison (filled si actif), Recherche = loupe, Biblio = barres de livres `M6 4v16M10 4v16M15 5l4 15`, Favoris = cœur, Réglages = roue crantée.

Le **mini-lecteur** est ancré au-dessus de la nav sur Accueil (et devrait persister sur tous les onglets dans l'app réelle).

---

## Écran 1 — Accueil
**But** : point d'entrée, carrousels de recommandations + playlists, mini-lecteur.

**Layout** (de haut en bas) : status bar → top app bar → contenu scrollable → mini-lecteur ancré → barre de progression fine → nav bar.

- **Top app bar** : logo Triat (22px) + « Accueil » (22px/700, ls 0.06em) à gauche ; avatar rond 36px (dégradé `#3A3747`→`#22212A`, initiale) à droite. Padding `8px 20px 14px`.
- **Section « Pour vous »** : en-tête (titre `titleMedium` + « Tout voir » 13px `onSurfaceVariant`), padding `4px 20px 12px`. Carrousel horizontal, gap 14px, padding latéral 20px. Cartes **150×150**, rayon 16px, dégradé placeholder, logo Triat gris centré. Badge en haut-droite : carré 24px, fond `rgba(10,10,12,.72)` + blur, icône source (play-rect YouTube / nuage SoundCloud) 15px. Sous la carte : titre 13.5px/600 + sous-titre 12px `onSurfaceVariant` (ex. « Mix sur mesure », « SoundCloud »).
  - Cartes : « Découverte / Mix sur mesure » (YouTube), « Chill du soir / SoundCloud » (nuage), « Nouveautés / Mix sur mesure ».
- **Section « Tes playlists »** : en-tête idem, padding top 22px. Liste verticale, gap 10px. Chaque ligne : carte `#17181C` rayon 14px, padding 10px ; vignette 52×52 rayon 10px à gauche ; titre 14.5px/600 + méta 12.5px `onSurfaceVariant` (« 42 titres · YouTube + SoundCloud ») ; overflow (3 points verticaux) à droite.
  - Playlists : « En mode BU$HI » (42 titres · YouTube + SoundCloud), « Luther : tous ses sons » (88 · YouTube), « amourette » (17 · SoundCloud).
- **Mini-lecteur** (ancré) : marge `0 12px 6px`, fond `#242027`, rayon 16px, padding 9px. Vignette 44×44 rayon 9px ; titre 13.5px/600 + artiste 12px `onSurfaceVariant` (ellipsis) ; bouton pause (barres 26px) + bouton next (24px), couleur `onSurface`. Titre exemple « Immaterial » / « SOPHIE ».
- **Barre de progression** : hauteur 2px, marge `0 20px 10px`, piste `#2C2C32`, remplissage 38% en `#D6D3DE`.

---

## Écran 2 — Lecteur plein écran (coverflow 3D façon Cider)
**But** : lecture en cours, navigation entre pistes au swipe (coverflow).

**Fond** : dégradé vertical `#201B26` → `#0E0C12` (55%).

**Layout** : status bar → header → coverflow → titre → (spacer) → scrubber → transport → rangée d'icônes basse.

- **Header** (padding `10px 20px 4px`) : chevron retour gauche (`M15 6l-6 6 6 6`, 24px) ; centre = « EN LECTURE » (labelSmall, ls 0.14em, `#9A99A1`) + « File d'attente · 24 » (13px/600) ; overflow 3-points à droite.
- **Coverflow** (hauteur 280px, `perspective: 1100px`, overflow hidden) :
  - Pochette **centrale** : 216×216, rayon 20px, dégradé `#4A3F52`→`#201925`, ombre `0 34px 60px -18px rgba(0,0,0,.85)`, centrée (`left:50% translateX(-50%)`, top 8px). Badge source en haut-droite : pilule `rgba(10,10,12,.72)` + blur, icône 15px + « YouTube » 11px/600.
  - Pochette **gauche** : 150×150, `top:40px; left:-34px`, `transform: rotateY(42deg) scale(.82)`, ombre `0 20px 40px -14px rgba(0,0,0,.7)`.
  - Pochette **droite** : 150×150, `top:40px; right:-34px`, `rotateY(-42deg) scale(.82)`, même ombre.
  - **En Compose** : `HorizontalPager` avec `graphicsLayer` par page — appliquer `rotationY` (±42°) et `scaleX/Y` (0.82) interpolés sur `pagerState.offsetForPage`, `cameraDistance` élevé pour la perspective. Page centrale à l'échelle 1, rotationY 0.
- **Bloc titre** (centré, padding `14px 24px 0`) : titre 23px/700 (« Immaterial ») + « SOPHIE · Oil of Every Pearl's Un-Insides » 15px `#9A99A1`.
- **Scrubber** (padding latéral 26px) : piste 4px `#2C2C32` rayon 3px ; remplissage 46% `#E4E2E6` ; poignée ronde 13px blanche centrée sur la position. Sous : temps écoulé « 1:18 » / restant « -2:35 » 11.5px `onSurfaceVariant`.
- **Transport** (padding `16px 30px 6px`, space-between) : shuffle (outline 22px, `onSurfaceVariant`) · précédent (filled 30px) · **play/pause central** = cercle 68px `#E9E6EF`, ombre `0 10px 26px -8px rgba(233,230,239,.5)`, icône pause `#15131A` 30px · suivant (filled 30px) · repeat (outline 22px).
- **Rangée d'icônes basse** (padding `12px 34px 26px`, `onSurfaceVariant`) : cœur (favori) · ajouter à playlist · paroles/file · partager.

**Interactions** : swipe horizontal = piste précédente/suivante (anime le coverflow) ; tap pochette latérale = saute à cette piste ; tap play central = toggle ; drag scrubber = seek ; chevron/swipe-down = fermer vers le mini-lecteur.

---

## Écran 3 — Recherche
**But** : chercher titres/artistes sur les deux sources avec filtre.

- **Titre** « Recherche » 22px/700 (padding `10px 20px 4px`).
- **Champ de recherche** : marge `10px 20px 0`, fond `#1C1D22`, rayon 26px (pill), padding `13px 18px`. Loupe outline 20px `onSurfaceVariant` + texte saisi 15px `onSurface` (« sophie ») + curseur (barre 1.5×17px `#C9C4D6`).
- **Chips de filtre source** (padding `14px 20px 6px`, gap 9px) :
  - « Toutes » = **actif** : fond `#302C38`, bordure `#403A4A`, 13px/600, coche à gauche.
  - « YouTube » / « SoundCloud » = inactifs : fond `#17181C`, bordure `#26262B`, 13px `#B7B6BD`, icône source à gauche.
- **Résultats** (padding `12px 20px 0`, gap 14px) :
  - Label « Titres » 13px/600 `onSurfaceVariant`.
  - Lignes : vignette 48×48 rayon 9px ; titre 14.5px/600 + ligne artiste 12.5px `onSurfaceVariant` suivie d'un **mini-badge source** (chip `#1F2024` rayon 5px, icône 12px). Overflow 3-points à droite.
    - « Immaterial » / SOPHIE (YouTube), « It's Okay To Cry » / SOPHIE (SoundCloud), « Faceshopping » / SOPHIE (YouTube).
  - Label « Artistes » puis ligne : avatar **rond** 48px + « SOPHIE » 14.5px/600 + « Artiste » 12.5px.

---

## Écran 4 — Bibliothèque
**But** : playlists, albums, artistes sauvegardés.

- **Top bar** : « Bibliothèque » 22px/700 + bouton « + » (plus, 24px) à droite.
- **Chips d'onglet** (gap 9px) : « Playlists » actif (`#302C38`, 12.5px/600), « Albums » / « Artistes » inactifs (`#17181C`, `#B7B6BD`). Rayon 18px.
- **Grille 2 colonnes** (padding `14px 20px 0`, gap 16px) : chaque tuile = pochette carrée (`aspect-ratio:1`) rayon 14px dégradé placeholder ; titre 14px/600 + nb titres 12px `onSurfaceVariant` dessous.
  - « En mode BU$HI » 42 · « amourette » 17 · « Luther » 88 · « Nepal » 31. (La 1ʳᵉ tuile montre un petit indicateur multi-source en haut-droite : 2 carrés 7px.)

---

## Écran 5 — Réglages de source
**But** : choisir où chercher la musique et comment l'afficher.

- **Top bar** : chevron retour + « Sources » 22px/700.
- **Groupe radio « OÙ CHERCHER LA MUSIQUE »** (label 13px/600 `onSurfaceVariant`, gap 11px) : cartes `#17181C` bordure `#26262B` rayon 16px padding 16px. Chaque carte : icône 38×38 rayon 10px (fond `#242027`) + titre 15px/600 + sous-titre 12.5px `onSurfaceVariant` + **radio** à droite (cercle 22px, bordure 2px `#45464A` ; sélectionné = bordure `#D8D4E2` + point 11px `#D8D4E2`).
  - « YouTube seul » / « Le plus grand catalogue »
  - « SoundCloud seul » / « Indés, remixes, sets »
  - « **Les deux** » / « Recommandé » = **sélectionné** : carte `#201C27`, bordure 1.5px `#4A4357`.
- **Section « AFFICHAGE »** (gap, séparateurs 1px `#1E1E22`) : lignes avec titre 15px/600 + description 12.5px `onSurfaceVariant` + **switch** à droite (piste 50×30 `#D8D4E2`, pastille 24px `#15131A` à droite = activé).
  - « Mélanger sur l'accueil » / « Entremêle YouTube et SoundCloud dans les carrousels » — **activé**.
  - « Badge de source » / « Petite icône sur chaque pochette » — **activé**.

---

## Interactions & comportement (résumé)
- Nav bas : change d'onglet, conserve l'état de scroll de chaque onglet, mini-lecteur persistant.
- Tap mini-lecteur → ouvre le lecteur plein écran (transition partagée de la pochette recommandée).
- Lecteur : swipe horizontal (coverflow), seek au drag, toggle play.
- Chips/radios/switches : feedback immédiat Material (ripple, animation d'état).
- Badge de source : visible si le switch « Badge de source » est ON.
- « Mélanger sur l'accueil » ON → carrousels mixent les deux sources ; OFF → sections séparées par source.

## État / données
- `selectedTab` (enum 5 onglets).
- `sourceMode` : `YOUTUBE | SOUNDCLOUD | BOTH` (écran Réglages, défaut `BOTH`).
- `mixOnHome: Boolean` (défaut true), `showSourceBadge: Boolean` (défaut true).
- `nowPlaying` : piste courante, file d'attente, position, état play/pause, source.
- `searchQuery` + `searchFilter` (All/YouTube/SoundCloud).
- Données réelles via les sources existantes du projet (YouTube + NewPipe/SoundCloud déjà intégrés).

## Design tokens (récap)
- **Couleurs** : voir tableau plus haut.
- **Spacing** : 4 / 6 / 8 / 9 / 10 / 12 / 14 / 16 / 20 / 22 / 26px.
- **Rayons** : 5, 9, 10, 14, 15, 16, 18, 20, 26px.
- **Tailles typo** : 11, 12, 12.5, 13, 13.5, 14, 14.5, 15, 17, 22, 23px.
- **Ombres** : carte lecteur `0 34px 60px -18px rgba(0,0,0,.85)` ; pochettes latérales `0 20px 40px -14px rgba(0,0,0,.7)` ; bouton play `0 10px 26px -8px rgba(233,230,239,.5)`.

## Assets
- Logo Triat (3 points triangle lecture) — à vectoriser.
- Icônes : Material Symbols Outlined (home, search, library, favorite, settings, skip, shuffle, repeat, share, playlist-add, more-vert, chevron) — déjà dispo via l'icon pack du projet.
- Icône source YouTube (rectangle arrondi + triangle play) et SoundCloud (nuage avec barres de forme d'onde) — monochromes, à vectoriser.
- Pochettes : vignettes réelles YouTube/SoundCloud à l'exécution (les dégradés sont des placeholders).

## Fichiers
- `Lecteur Triat.dc.html` — prototype HTML de référence (les 5 écrans côte à côte, thème sombre). Ouvrir dans un navigateur pour inspecter mesures/couleurs exactes.
