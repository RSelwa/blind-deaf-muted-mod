import { defineConfig } from 'vite'
import tailwindcss from '@tailwindcss/vite'

// Date du build, injectee au moment du `vite build`. Comme le build tourne sur
// le VPS a chaque deploiement (auto-deploy.yml), cette date = date du dernier
// deploiement. Rien n'est commite => pas de boucle CI pour bumper une version.
const BUILD_DATE = new Date().toLocaleDateString('fr-FR', {
  day: '2-digit',
  month: 'long',
  year: 'numeric',
})

// Site vitrine statique. `base: './'` => liens relatifs, donc le build `dist/`
// marche servi a la racine d'un domaine comme dans un sous-dossier (GitHub Pages).
// Le jar telechargeable vit dans `public/downloads/` et est copie tel quel dans
// `dist/downloads/` au build.
export default defineConfig({
  base: './',
  plugins: [tailwindcss()],
  define: {
    __BUILD_DATE__: JSON.stringify(BUILD_DATE),
  },
})
