import { defineConfig } from 'vite'
import tailwindcss from '@tailwindcss/vite'

// Site vitrine statique. `base: './'` => liens relatifs, donc le build `dist/`
// marche servi a la racine d'un domaine comme dans un sous-dossier (GitHub Pages).
// Le jar telechargeable vit dans `public/downloads/` et est copie tel quel dans
// `dist/downloads/` au build.
export default defineConfig({
  base: './',
  plugins: [tailwindcss()],
})
