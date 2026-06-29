# Site vitrine — Blind Deaf Muted

Site **Vite + TypeScript + Tailwind CSS v4** qui présente le mod, propose le
**téléchargement du jar** et contient un **tutoriel en français** (installation joueur +
déploiement serveur + commandes).

Stack : `vite` + `typescript` + `tailwindcss` (plugin `@tailwindcss/vite`, pas de
`tailwind.config` — thème défini via `@theme` dans `src/style.css`). Markup généré dans
`src/main.ts` avec des classes utilitaires Tailwind. `npm run build` lance `tsc` (type-check)
puis `vite build`.

## Lancer / construire

```bash
cd site
npm install
npm run dev      # serveur de dev (http://localhost:5173)
npm run build    # build statique -> site/dist/
npm run preview  # prévisualiser le build
```

## Le jar téléchargeable

Le bouton de téléchargement pointe vers `public/downloads/blind-deaf-muted-<version>.jar`.
Ce dossier est **commité exprès** (exception ajoutée dans le `.gitignore` racine) pour
que le jar soit servi par le site.

Après chaque rebuild du mod + bump de version :

```bash
# depuis la racine du dépôt
./gradlew :mod:build
cp mod/build/libs/blind-deaf-muted-<version>.jar site/public/downloads/
```

Puis mettre à jour `MOD_VERSION` en haut de `src/main.js`.

## Déploiement

Build statique pur (`base: './'`) — déployable tel quel sur GitHub Pages, Netlify,
Vercel ou tout hébergeur de fichiers statiques en publiant le dossier `dist/`.
