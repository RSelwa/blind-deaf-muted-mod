import './style.css'
// Contenu reel des fichiers Docker importe tel quel au build (Vite `?raw`).
// Source unique de verite : ces blocs sur le site = les fichiers du depot, aucun
// risque de divergence. On les rend copiables + telechargeables cote client.
import composeUserRaw from '../../docker/docker-compose.user.yml?raw'
import composeBuild from '../../docker/docker-compose.yml?raw'
import dockerfile from '../../docker/Dockerfile?raw'
// Version lue directement dans gradle.properties (source UNIQUE de verite). Plus
// besoin de re-bumper la version ici ni le tag d'image du compose : tout en decoule.
import gradleProps from '../../gradle.properties?raw'

const MC_VERSION = /^minecraft_version=(.+)$/m.exec(gradleProps)?.[1].trim() ?? '1.21.4'
const MOD_VERSION = /^mod_version=(.+)$/m.exec(gradleProps)?.[1].trim() ?? '0.0.0'
const JAR = `blind-deaf-muted-${MOD_VERSION}.jar`
const JAR_URL = `./downloads/${JAR}`

// Re-epingle le tag d'image de l'image GHCR sur MOD_VERSION, quelle que soit la
// valeur ecrite en dur dans docker-compose.user.yml. Le fichier copie/telecharge
// depuis le site est donc TOUJOURS aligne sur gradle.properties.
const composeUser = composeUserRaw.replace(
  /(blind-deaf-muted-server:)\S+/,
  `$1${MOD_VERSION}`,
)

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
    desc: "Écran noir ou brouillard de 2 blocs. Vous ne voyez rien — quelqu'un doit vous guider à la voix.",
  },
  {
    nom: 'Sourd',
    emoji: '🙉',
    couleur: '#ffaa00',
    perd: "l'ouïe",
    desc: "Tout l'audio du jeu et la voix entrante sont coupés. Vous devez lire et observer pour comprendre.",
  },
  {
    nom: 'Muet',
    emoji: '🙊',
    couleur: '#ff79ff',
    perd: 'la parole',
    desc: 'Chat bloqué et micro coupé. Vous voyez et entendez tout, mais ne pouvez rien transmettre directement.',
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

// Echappe le texte brut d'un fichier pour l'injecter sans casser le HTML.
const esc = (s: string) =>
  s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')

// Registre id -> fichier, alimente par codeFile(). Les boutons copier/telecharger
// referencent l'id via data-* (voir le gestionnaire de clic en bas de fichier).
const files: Record<string, { name: string; text: string }> = {}
let fileSeq = 0
const btnMini =
  'inline-flex items-center gap-1 rounded-md border border-line px-2.5 py-1 text-xs font-medium text-slate-200 hover:bg-ink-2 transition cursor-pointer'

// Bloc de code avec en-tete (nom du fichier + boutons Copier / Telecharger).
const codeFile = (name: string, text: string) => {
  const id = `f${fileSeq++}`
  files[id] = { name, text }
  return `
    <div class="mt-3">
      <div class="flex items-center justify-between gap-3 bg-[#0a0c11] border border-line rounded-t-lg px-4 py-2">
        <span class="font-mono text-sm text-slate-300 truncate">${name}</span>
        <div class="flex gap-2 shrink-0">
          <button type="button" data-copy="${id}" class="${btnMini}">📋 Copier</button>
          <button type="button" data-download="${id}" class="${btnMini}">⬇ Télécharger</button>
        </div>
      </div>
      <pre class="${pre} rounded-t-none border-t-0"><code>${esc(text)}</code></pre>
    </div>`
}

document.querySelector<HTMLDivElement>('#app')!.innerHTML = `
  <header class="border-b border-line px-6 pt-20 pb-16 text-center bg-[radial-gradient(1200px_500px_at_50%_-10%,#1d2740_0%,transparent_60%)]">
    <div class="mx-auto max-w-3xl">
      <p class="uppercase tracking-[0.15em] text-xs text-slate-400 mb-2">Mod Minecraft Fabric · ${MC_VERSION}</p>
      <h1 class="text-5xl sm:text-7xl font-extrabold leading-none mb-4 bg-gradient-to-r from-[#ff5555] via-[#ffaa00] to-[#ff79ff] bg-clip-text text-transparent">
        Blind&nbsp;Deaf&nbsp;Muted
      </h1>
      <p class="text-lg text-slate-300 max-w-xl mx-auto mb-8">
        Un défi coopératif. Chaque joueur reçoit un handicap — <b>aveugle</b>,
        <b>sourd</b> ou <b>muet</b> — et l'équipe doit communiquer autour de ses
        limites pour <b>vaincre l'Ender Dragon</b>. En normal ou en hardcore.
      </p>
      <div class="flex flex-wrap justify-center gap-3">
        <a class="${btnPrimary}" href="${JAR_URL}" download>⬇ Télécharger le mod (${JAR})</a>
        <a class="${btnGhost}" href="#tuto">Lire le tutoriel</a>
      </div>
      <p class="mt-3 text-xs text-slate-500">Dernière mise à jour : ${BUILD_DATE}</p>
      <p class="mt-6 text-sm text-slate-400">Le même jar s'installe côté joueur ET tourne côté serveur. Aucun téléchargement séparé.</p>
    </div>
  </header>

  <main class="mx-auto max-w-3xl px-6">
    <section class="${section}">
      <h2 class="${h2}">Le principe</h2>
      <p class="text-lg text-slate-300">
        Chaque rôle perd <b>exactement un</b> des trois sens : voir, entendre, parler.
        Personne ne peut percevoir, signaler et agir tout seul — vous êtes forcés de
        vous relayer l'information. C'est là que naît le fun (et le chaos).
      </p>
      <div class="grid gap-4 mt-6 sm:grid-cols-3">
        ${roles
          .map(
            (r) => `
          <article class="bg-card border border-line rounded-xl p-6 border-t-[3px]" style="border-top-color:${r.couleur}">
            <div class="text-4xl">${r.emoji}</div>
            <h3 class="mt-2 font-bold text-lg" style="color:${r.couleur}">${r.nom}</h3>
            <p class="italic text-slate-400 mb-2">perd ${r.perd}</p>
            <p>${r.desc}</p>
          </article>`,
          )
          .join('')}
      </div>
    </section>

    <section class="${section}">
      <h2 class="${h2}">Ce qu'il vous faut</h2>
      <div class="grid gap-6 sm:grid-cols-2">
        <div class="bg-ink-2 border border-line rounded-xl p-6">
          <h3 class="font-bold mb-2">👥 Chaque joueur</h3>
          <ul class="list-disc pl-5 space-y-1">
            <li><b>Fabric Loader</b> pour Minecraft ${MC_VERSION}</li>
            <li><b>Fabric API</b></li>
            <li><b>Simple Voice Chat</b> (voix en jeu)</li>
            <li>le jar <b>${JAR}</b> ci-dessus</li>
          </ul>
        </div>
        <div class="bg-ink-2 border border-line rounded-xl p-6">
          <h3 class="font-bold mb-2">🖥️ L'hôte (une personne)</h3>
          <ul class="list-disc pl-5 space-y-1">
            <li>fait tourner le serveur via <b>Docker</b> (une commande)</li>
            <li>pas besoin d'installer Java ni Minecraft sur le serveur</li>
            <li>le serveur télécharge Fabric API + Simple Voice Chat tout seul</li>
          </ul>
        </div>
      </div>
      <p class="mt-6 bg-[#2a1d0e] border border-[#6b4a17] rounded-lg px-5 py-3 text-[#f0d9b0]">
        ⚠️ La version du mod côté joueur <b>doit être identique</b> à celle du serveur.
        Une version différente affiche un avertissement « mettez à jour », pas un crash.
      </p>
    </section>

    <section class="${section}" id="tuto">
      <h2 class="${h2}">Tutoriel — installer et jouer</h2>

      <h3 class="${stepH}">Étape 1 — Installer Fabric (chaque joueur)</h3>
      <ol class="list-decimal pl-5 space-y-2">
        <li>
          Installez le <b>Fabric Loader</b> pour Minecraft ${MC_VERSION} avec
          l'${a('https://fabricmc.net/use/installer/', 'installateur officiel')},
          ou un launcher comme ${a('https://prismlauncher.org/', 'Prism')} qui le fait pour vous.
        </li>
        <li>
          Téléchargez ${a('https://modrinth.com/mod/fabric-api', 'Fabric API')}
          et ${a('https://modrinth.com/mod/simple-voice-chat', 'Simple Voice Chat')} en version ${MC_VERSION}.
        </li>
      </ol>

      <h3 class="${stepH}">Étape 2 — Poser les mods</h3>
      <p class="mb-2">Placez ces trois fichiers dans votre dossier <code class="${icode}">mods/</code> :</p>
      <pre class="${pre}"><code>.minecraft/mods/
 ├─ fabric-api-*.jar
 ├─ voicechat-fabric-*.jar
 └─ ${JAR}      ← le mod téléchargé ici</code></pre>
      <p class="mt-2 text-sm text-slate-400">
        Le dossier <code class="${icode}">mods/</code> est dans <code class="${icode}">.minecraft/mods/</code>
        (ou le dossier d'instance de votre launcher). Lancez Minecraft avec le profil Fabric.
      </p>

      <h3 class="${stepH}">Étape 3 — Lancer le serveur (l'hôte)</h3>
      <p class="mb-4">Une seule personne héberge. Il faut <b>Docker</b> installé
      (${a('https://docs.docker.com/get-docker/', 'Docker Desktop')} sur Windows/Mac,
      <code class="${icode}">docker</code> + <code class="${icode}">docker compose</code> sur Linux).
      Deux façons de faire — la première ne demande <b>ni dépôt, ni Git, ni Java</b>.</p>

      <h4 class="mt-6 mb-2 font-bold text-lg">🟢 Option A — Copier-coller (recommandé, sans le dépôt)</h4>
      <ol class="list-decimal pl-5 space-y-2">
        <li>Créez un dossier vide, p. ex. <code class="${icode}">bdm-server/</code>.</li>
        <li>Dedans, créez un fichier <code class="${icode}">docker-compose.yml</code> avec ce contenu
          (bouton <b>Copier</b> ou <b>Télécharger</b>) :</li>
      </ol>
      ${codeFile('docker-compose.yml', composeUser)}
      <p class="mt-4 mb-2">Puis, depuis ce dossier, une seule commande :</p>
      <pre class="${pre}"><code>docker compose up -d</code></pre>
      <p class="mt-2 text-sm text-slate-400">
        Docker télécharge l'image prête à l'emploi (le mod est déjà dedans) et démarre le serveur.
        Le monde est sauvegardé dans le sous-dossier <code class="${icode}">data/</code>.
        Pour arrêter : <code class="${icode}">docker compose down</code>.
      </p>

      <h4 class="mt-8 mb-2 font-bold text-lg">🔧 Option B — Construire depuis les sources</h4>
      <p class="mb-2">Pour compiler le jar vous-même (contributions, version modifiée). Il faut le dépôt.
      Placez ces deux fichiers dans <code class="${icode}">docker/</code> (ils y sont déjà si vous clonez le dépôt) :</p>
      ${codeFile('docker/docker-compose.yml', composeBuild)}
      ${codeFile('docker/Dockerfile', dockerfile)}
      <p class="mt-4 mb-2">Puis, depuis la racine du dépôt :</p>
      <pre class="${pre}"><code>git clone &lt;url-du-depot&gt; blind-deaf-muted
cd blind-deaf-muted/docker
docker compose up -d --build   # --build : compile le jar la 1re fois</code></pre>

      <p class="my-4">Dans les deux cas, ouvrez ensuite les ports sur la machine hôte :</p>
      <pre class="${pre}"><code># Minecraft (TCP) + Simple Voice Chat (UDP)
sudo ufw allow 25565/tcp
sudo ufw allow 24454/udp</code></pre>
      <p class="mt-3 bg-[#2a1d0e] border border-[#6b4a17] rounded-lg px-5 py-3 text-[#f0d9b0]">
        ⚠️ Sans le port <b>UDP 24454</b>, les joueurs se connectent mais la voix échoue silencieusement.
      </p>
      <p class="mt-3 text-sm text-slate-400">
        Pas de serveur loué ? Sur le même réseau, connectez-vous à l'IP locale de l'hôte
        (<code class="${icode}">192.168.x.x</code>). À distance sans port-forwarding : utilisez un tunnel comme
        ${a('https://playit.gg/', 'playit.gg')} ou ${a('https://tailscale.com/', 'Tailscale')}
        (pensez à tunneller aussi l'UDP 24454).
      </p>

      <h3 class="${stepH}">Étape 4 — Se connecter</h3>
      <p>
        Lancez Minecraft (profil Fabric) et rejoignez l'adresse de l'hôte :
        <code class="${icode}">&lt;IP&gt;:25565</code>. Le <code class="${icode}">:25565</code> est optionnel (port par défaut).
      </p>

      <h3 class="${stepH}">Étape 5 — Distribuer les rôles (admin)</h3>
      <p class="mb-2">Il faut être op (niveau de permission 2). Commandes :</p>
      <pre class="${pre}"><code>/bdm set &lt;joueur&gt; &lt;blind|deaf|muted|none&gt;   # attribuer / retirer un rôle
/bdm random                                  # roulette : un rôle aléatoire pour tout le monde
/bdm randomizer                              # te donne 4 fioles Randomizer (test)
/bdm skin &lt;on|off&gt;                           # afficher/cacher les accessoires de rôle
/bdm events &lt;on|off&gt;                         # re-tirage automatique des rôles à intervalle aléatoire
/bdm events now                              # force un re-tirage maintenant (test / vidéo)</code></pre>
      <p class="my-2">Exemples :</p>
      <pre class="${pre}"><code>/bdm set Alice blind
/bdm set Bob deaf
/bdm set Carol muted
/bdm set Alice none     # retirer le rôle</code></pre>
      <p class="mt-3 text-sm text-slate-400">
        Pour vous op, attachez-vous à la console du serveur :
        <code class="${icode}">docker attach blind-deaf-muted-server</code> puis tapez
        <code class="${icode}">op &lt;votrePseudo&gt;</code> (Ctrl-P Ctrl-Q pour détacher).
      </p>
      <p class="mt-3 text-sm text-slate-400">
        Une <b>fiole Randomizer</b> jetable re-tire les rôles de tout le monde quand elle se brise.
        On la trouve dans les coffres de structures et via le troc avec les Piglins.
      </p>
    </section>

    <section class="${section}">
      <h2 class="${h2}">Dépannage rapide</h2>
      <ul class="list-disc pl-5 space-y-2">
        <li><b>« version obsolète » à la connexion</b> — le mod n'a pas la même version côté client et serveur. Redistribuez le même jar.</li>
        <li><b>Fabric est là mais le mod ne fait rien</b> — il manque Fabric API dans <code class="${icode}">mods/</code>, ou vous avez lancé le profil vanilla.</li>
        <li><b>Pas de voix / micro barré</b> — Simple Voice Chat manque (client ou serveur), versions différentes, ou UDP 24454 fermé.</li>
        <li><b>Les effets ne s'appliquent pas après <code class="${icode}">/bdm set</code></b> — le joueur ciblé n'a pas le mod installé. Le serveur stocke le rôle mais seul un client moddé affiche l'effet.</li>
        <li><b>Les rôles ont disparu après un redémarrage</b> — normal pour l'instant, la persistance est prévue.</li>
      </ul>
    </section>

    <section class="${section} text-center">
      <h2 class="${h2}">Prêt ?</h2>
      <a class="${btnPrimary}" href="${JAR_URL}" download>⬇ Télécharger ${JAR}</a>
      <p class="mt-3 text-sm text-slate-400">Minecraft ${MC_VERSION} · Fabric · jar unique client + serveur</p>
    </section>
  </main>

  <footer class="text-center py-10 px-6 text-sm text-slate-400">
    <p>Blind Deaf Muted — mod Minecraft coopératif. Aveugle, sourd, muet : communiquez ou périssez.</p>
  </footer>
`

// Boutons Copier / Telecharger des fichiers Docker (delegation d'evenements :
// un seul listener pour tous les blocs codeFile()).
document.addEventListener('click', async (e) => {
  const btn = (e.target as HTMLElement).closest<HTMLElement>('[data-copy],[data-download]')
  if (!btn) return

  const copyId = btn.dataset.copy
  const dlId = btn.dataset.download

  if (copyId && files[copyId]) {
    try {
      await navigator.clipboard.writeText(files[copyId].text)
      const prev = btn.textContent
      btn.textContent = '✓ Copié'
      setTimeout(() => (btn.textContent = prev), 1500)
    } catch {
      // Clipboard bloque (http, permission) : on retombe sur le telechargement.
      btn.textContent = '⚠ Copie bloquée'
    }
  } else if (dlId && files[dlId]) {
    const { name, text } = files[dlId]
    const url = URL.createObjectURL(new Blob([text], { type: 'text/plain' }))
    const link = document.createElement('a')
    link.href = url
    // Nom de fichier plat (pas de "docker/…") pour le telechargement.
    link.download = name.split('/').pop()!
    link.click()
    URL.revokeObjectURL(url)
  }
})
