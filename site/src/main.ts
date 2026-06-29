import './style.css'

// Source de verite cote site. A bumper en meme temps que mod_version dans
// gradle.properties, et recopier le nouveau jar dans public/downloads/.
const MOD_VERSION = '0.1.0'
const MC_VERSION = '1.21.4'
const JAR = `blind-deaf-muted-${MOD_VERSION}.jar`
const JAR_URL = `./downloads/${JAR}`

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
      <p class="mb-2">Avec Docker installé, depuis le dossier <code class="${icode}">docker/</code> du dépôt :</p>
      <pre class="${pre}"><code>git clone &lt;url-du-depot&gt; blind-deaf-muted
cd blind-deaf-muted/docker
docker compose up -d --build</code></pre>
      <p class="my-2">Ouvrez ensuite les ports sur le serveur :</p>
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
/bdm health &lt;on|off&gt;                         # mode vie partagée
/bdm skin &lt;on|off&gt;                           # afficher/cacher les accessoires de rôle
/bdm events &lt;on|off&gt;                         # minuteur d'événements aléatoires (re-tirage / potion)
/bdm events now                              # déclenche un événement maintenant (test / vidéo)</code></pre>
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
