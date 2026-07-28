const path = require('path');

module.exports = {
  apps: [
    {
      name: 'blind-deaf-muted',
      script: 'server.js',
      // Charge les variables d'environnement depuis site-modpack-api/.env
      node_args: '--env-file=.env',
      // On exécute le script depuis le dossier de l'API pour que les chemins relatifs (stats.json, ../site-modpack/dist) fonctionnent bien
      cwd: path.join(__dirname, 'site-modpack-api'),
      exec_mode: 'fork',
      instances: 1,
      autorestart: true,
      watch: false,
      max_memory_restart: '256M',
      env: {
        NODE_ENV: 'production',
      },
    },
  ],
};
