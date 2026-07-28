import './style.css'
import gradleProps from '../../gradle.properties?raw'

const MC_VERSION = /^minecraft_version=(.+)$/m.exec(gradleProps)?.[1].trim() ?? '1.21.4'
const MOD_VERSION = /^mod_version=(.+)$/m.exec(gradleProps)?.[1].trim() ?? '0.1.0'

const MRPACK = `blind-deaf-muted-${MOD_VERSION}.mrpack`
const MRPACK_URL = `./downloads/${MRPACK}`

// Injecte par Vite au build (voir vite.config.ts). Date du dernier deploiement.
declare const __BUILD_DATE__: string
const BUILD_DATE = __BUILD_DATE__

interface RoleInfo {
  nom: string
  emoji: string
  /** Couleur d'accent (hex) appliquee en bordure + titre via style inline. */
  couleur: string
  perd: string
  desc: string
}

const roles: RoleInfo[] = [
  {
    nom: 'Aveugle',
    emoji: '🙈',
    couleur: '#ff5555',
    perd: 'la vue',
    desc: "Un flou de profondeur réglable vous empêche de voir de loin. Vous ne voyez presque rien — quelqu'un doit vous guider à la voix.",
  },
  {
    nom: 'Sourd',
    emoji: '🙉',
    couleur: '#ffaa00',
    perd: "l'ouïe",
    desc: "L'audio du jeu est très faible et les voix de vos amis sont fortement étouffées (filtre passe-bas, comme à travers un mur).",
  },
  {
    nom: 'Muet',
    emoji: '🙊',
    couleur: '#ff79ff',
    perd: 'la parole',
    desc: "Le chat écrit est bloqué et votre voix est déformée (incompréhensible). Vous devez utiliser les cartes à écrire et les animations pour communiquer !",
  },
]

// Classes reutilisables (evite de repeter de longues listes d'utilitaires).
const btnBase =
  'inline-block rounded-lg px-6 py-3.5 font-semibold no-underline transition active:translate-y-px'
const btnPrimary = `${btnBase} bg-brand text-[#06210f] hover:bg-brand-dark`
const btnGhost = `${btnBase} border border-line text-slate-200 hover:bg-ink-2`
const pre = 'bg-[#0a0c11] border border-line rounded-lg p-4 overflow-x-auto text-sm font-mono'
const icode = 'bg-[#0a0c11] border border-line px-1.5 py-0.5 rounded text-[0.85em] font-mono'
const section = 'py-12 border-b border-line'
const h2 = 'text-3xl font-bold mb-4'
const stepH = 'mt-8 mb-2 text-brand text-xl font-semibold'
const link = 'text-brand underline underline-offset-2 hover:text-brand-dark'

const a = (href: string, label: string) =>
  `<a href="${href}" target="_blank" rel="noopener" class="${link}">${label}</a>`

document.querySelector<HTMLDivElement>('#app')!.innerHTML = `
  <header class="border-b border-line px-6 pt-20 pb-16 text-center bg-[radial-gradient(1200px_500px_at_50%_-10%,#1d2740_0%,transparent_60%)]">
    <div class="mx-auto max-w-3xl">
      <p class="uppercase tracking-[0.15em] text-xs text-slate-400 mb-2">Modpack Minecraft Fabric · ${MC_VERSION}</p>
      <h1 class="text-5xl sm:text-7xl font-extrabold leading-none mb-4 bg-gradient-to-r from-[#ff5555] via-[#ffaa00] to-[#ff79ff] bg-clip-text text-transparent">
        Blind&nbsp;Deaf&nbsp;Muted
      </h1>
      <p class="text-lg text-slate-300 max-w-xl mx-auto mb-8">
        Un défi coopératif. Chaque joueur reçoit un handicap — <b>aveugle</b>,
        <b>sourd</b> ou <b>muet</b> — et l'équipe doit communiquer autour de ses
        limites pour <b>vaincre l'Ender Dragon</b>. En normal ou en hardcore.
      </p>
      <div class="flex flex-wrap justify-center gap-3">
        <a class="${btnPrimary} track-download" href="${MRPACK_URL}" download>⬇ Télécharger le Modpack (.mrpack)</a>
        <a class="${btnGhost} track-modrinth" href="https://modrinth.com/mod/blind-deaf-mute" target="_blank">Page Modrinth</a>
        <a class="${btnGhost}" href="#tuto">Lire le tutoriel</a>
      </div>
      <p class="mt-4 text-sm text-slate-400"><a href="https://ko-fi.com/toho183994" target="_blank" rel="noopener" class="hover:text-slate-300 underline underline-offset-2 transition track-kofi">Soutenir ToHo sur Ko-fi ☕</a></p>
      <p class="mt-4 text-sm text-slate-400"><a href="https://buymeacoffee.com/rselwa" target="_blank" rel="noopener" class="hover:text-slate-300 underline underline-offset-2 transition track-kofi">Soutenir LuckyFisher sur Buy Me a Coffee ☕</a></p>
      <p class="mt-2 text-xs text-slate-500">Dernière mise à jour : ${BUILD_DATE}</p>
      <p class="mt-6 text-sm text-slate-400">Le Modpack inclut le mod principal, Simple Voice Chat, Emotecraft, et Simple Revive pour l'expérience ultime.</p>
    </div>
  </header>

  <main class="mx-auto max-w-3xl px-6">
    <section class="${section}">
      <h2 class="${h2}">Le principe (Le Triangle)</h2>
      <p class="text-lg text-slate-300 mb-4">
        Le gameplay repose sur une asymétrie totale. Chaque rôle dispose de deux capacités sur trois : voir, entendre, parler.
        Vous êtes forcés de relayer l'information !
      </p>
      <div class="grid gap-4 sm:grid-cols-3">
        ${roles
    .map(
      (r) => `
          <article class="bg-card border border-line rounded-xl p-6 border-t-[3px]" style="border-top-color:${r.couleur}">
            <div class="text-4xl">${r.emoji}</div>
            <h3 class="mt-2 font-bold text-lg" style="color:${r.couleur}">${r.nom}</h3>
            <p class="italic text-slate-400 mb-2">perd ${r.perd}</p>
            <p class="text-sm">${r.desc}</p>
          </article>`,
    )
    .join('')}
      </div>
    </section>

    <section class="${section}">
      <h2 class="${h2}">Les Objets Spéciaux</h2>
      <p class="mb-4 text-slate-300">Pour vous aider (ou pimenter la partie), plusieurs objets personnalisés ont été ajoutés :</p>
      <ul class="space-y-4">
        <li class="bg-ink-2 p-4 rounded-lg border border-line">
          <b>📢 Le Mégaphone</b> : Permet aux Sourds ou Muets de passer temporairement outre leur handicap (rafale de 5s).
        </li>
        <li class="bg-ink-2 p-4 rounded-lg border border-line">
          <b>📝 La Carte à écrire</b> : Indispensable pour le Muet. <i>Sneak + Clic Droit</i> pour écrire (jusqu'à 6 lignes ou des messages rapides), puis <i>Clic Droit</i> pour la brandir en 3D face à ses alliés.
        </li>
        <li class="bg-ink-2 p-4 rounded-lg border border-line">
          <b>🎲 La Bouteille de Randomiseur</b> : Jetable, elle relance aléatoirement les rôles de tout le monde (façon roulette). À trouver dans les coffres ou via troc Piglin.
        </li>
        <li class="bg-ink-2 p-4 rounded-lg border border-line">
          <b>🧪 La Potion de Soulagement</b> : Réduit temporairement l'intensité des handicaps de 75% dans la zone.
        </li>
        <li class="bg-ink-2 p-4 rounded-lg border border-line">
          <b>🧭 Le Traceur d'équipiers</b> : Une boussole sur l'HUD indiquant la direction de vos alliés.
        </li>
      </ul>
    </section>

    <section class="${section}">
      <h2 class="${h2}">Ce qu'il vous faut</h2>
      <div class="grid gap-6 sm:grid-cols-2">
        <div class="bg-ink-2 border border-line rounded-xl p-6">
          <h3 class="font-bold mb-2">👥 Chaque joueur (Modpack)</h3>
          <p class="text-sm mb-3">Le modpack inclut tout ce dont vous avez besoin :</p>
          <ul class="list-disc pl-5 space-y-1 text-sm">
            <li><b>Blind Deaf Muted</b></li>
            <li><b>Simple Voice Chat</b> (Indispensable pour la voix de proximité)</li>
            <li><b>Essential</b> (Pour inviter vos amis sans serveur !)</li>
            <li><b>Emotecraft</b> & <b>Simple Revive</b></li>
            <li><b>Fabric API</b> et utilitaires</li>
          </ul>
        </div>
        <div class="bg-ink-2 border border-line rounded-xl p-6">
          <h3 class="font-bold mb-2">🖥️ L'hôte (Deux options)</h3>
          <ul class="list-disc pl-5 space-y-1 text-sm">
            <li><b>Option A (Facile) :</b> Lancez une partie Solo et invitez vos amis grâce au mod <b>Essential</b> inclus ! Tout marche directement.</li>
            <li><b>Option B (Serveur Dédié) :</b> Installez le Server Pack sur un serveur distant. <b>CRUCIAL :</b> Il faut impérativement ouvrir le port <b>24454 UDP</b> pour Voice Chat !</li>
          </ul>
        </div>
      </div>
      <p class="mt-6 bg-[#2a1d0e] border border-[#6b4a17] rounded-lg px-5 py-3 text-[#f0d9b0]">
        🎙️ <b>Intégration Native Simple Voice Chat</b> : Le mod coupe le micro des Muets et applique un filtre étouffé réaliste (passe-bas) aux Sourds !
      </p>
    </section>

    <section class="${section}" id="tuto">
      <h2 class="${h2}">Tutoriel — installer et jouer</h2>

      <h3 class="${stepH}">Étape 1 — Télécharger le Modpack</h3>
      <p class="mb-2">
        Utilisez le ${a('https://modrinth.com/app', 'Modrinth App')} ou ${a('https://prismlauncher.org/', 'Prism Launcher')} pour installer facilement le format <code class="${icode}">.mrpack</code>.
      </p>
      <a href="${MRPACK_URL}" class="${btnGhost} text-sm py-2 px-4 mt-2" target="_blank">Page du Modpack sur Modrinth</a>

      <h3 class="${stepH}">Étape 2 — L'installer</h3>
      <ul class="list-disc pl-5 space-y-2 mt-2">
        <li><b>Modrinth App</b> : Cliquez sur le bouton <b>+</b> (Ajouter une instance) &rarr; <i>From File</i>, et choisissez le <code class="${icode}">.mrpack</code>.</li>
        <li><b>Prism Launcher</b> : <i>Add Instance</i> &rarr; <i>Modrinth</i>, cherchez "Blind Deaf Muted", ou importez le fichier <code class="${icode}">.mrpack</code>.</li>
      </ul>

      <h3 class="${stepH}">Étape 3 — Se connecter (Multijoueur)</h3>
      <div class="space-y-4 mb-2">
        <p class="bg-ink-2 p-4 rounded-lg border border-line text-sm">
          <b>✨ La méthode Essential (Recommandée) :</b><br>
          L'hôte lance une partie en mode Solo. Depuis le menu Échap, il clique sur <b>"Inviter"</b> via le menu Essential. Ses amis reçoivent l'invitation et rejoignent. Pas besoin de configurer de ports, la voix marchera toute seule !
        </p>
        <p class="text-sm px-4">
          <b>⚙️ La méthode Serveur Dédié :</b><br>
          L'hôte démarre son serveur (n'oubliez pas d'ouvrir l'UDP 24454). Les joueurs rejoignent l'adresse IP (ex: <code class="${icode}">&lt;IP&gt;:25565</code>).
        </p>
      </div>

      <h3 class="${stepH}">Étape 4 — Distribuer les rôles et Configurer</h3>
      <p class="mb-2">
        Appuyez sur la touche <code class="${icode}">O</code> en jeu pour ouvrir le <b>menu de configuration en direct</b> (modifier l'intensité du brouillard, le cooldown du mégaphone, etc.).<br><br>
        Les administrateurs (OP) peuvent utiliser les commandes :
      </p>
      <pre class="${pre}"><code>/bdm set &lt;joueur&gt; &lt;blind|deaf|muted|none&gt;
/bdm random
/bdm randomizer
/bdm skin &lt;on|off&gt;</code></pre>
    </section>

    <section class="${section} text-center">
      <h2 class="${h2}">Prêt ?</h2>
      <a class="${btnPrimary} track-download" href="${MRPACK_URL}" download>⬇ Télécharger le Modpack (.mrpack)</a>
      <p class="mt-3 text-sm text-slate-400">Pour Minecraft ${MC_VERSION} · Format .mrpack</p>
    </section>
  </main>

  <footer class="text-center py-10 px-6 text-sm text-slate-400">
    <p>Blind Deaf Muted — mod Minecraft coopératif. Aveugle, sourd, muet : communiquez ou périssez.</p>
    <p class="mt-2"><a href="https://ko-fi.com/toho183994" target="_blank" rel="noopener" class="hover:text-slate-300 underline underline-offset-2 transition track-kofi">Soutenir ToHo sur Ko-fi ☕</a></p>
    <p class="mt-2"><a href="https://buymeacoffee.com/rselwa" target="_blank" rel="noopener" class="hover:text-slate-300 underline underline-offset-2 transition track-kofi">Soutenir LuckyFisher sur Buy Me a Coffee ☕</a></p>
  </footer>
`

// --- API Tracking ---
const API_URL = '/api';

// Track page view
fetch(`${API_URL}/track/view`, { method: 'POST' }).catch(() => { });

// Track clicks
document.addEventListener('click', (e) => {
  const target = (e.target as HTMLElement).closest('a');
  if (!target) return;

  let type = '';
  if (target.classList.contains('track-download')) type = 'download';
  else if (target.classList.contains('track-modrinth')) type = 'modrinth';
  else if (target.classList.contains('track-kofi')) type = 'kofi';

  if (type) {
    // Fire and forget click tracking
    fetch(`${API_URL}/track/click/${type}`, { method: 'POST' }).catch(() => { });
  }
});
