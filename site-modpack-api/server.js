import express from 'express';
import cors from 'cors';
import fs from 'fs';
import path from 'path';

const app = express();
const PORT = 3000;
const STATS_FILE = path.resolve('stats.json');

app.use(cors());
app.use(express.json());

let stats = { views: 0, clicks: { download: 0, modrinth: 0, kofi: 0 } };

const initStats = () => {
  if (fs.existsSync(STATS_FILE)) {
    try {
      const data = fs.readFileSync(STATS_FILE, 'utf8');
      stats = JSON.parse(data);
    } catch (err) {
      console.error('Erreur lecture stats.json, utilisation des valeurs par défaut.', err);
    }
  }
};

const writeStats = () => {
  try {
    fs.writeFileSync(STATS_FILE, JSON.stringify(stats, null, 2));
    console.log('Stats sauvegardées sur le disque.');
  } catch (err) {
    console.error('Erreur lors de la sauvegarde des stats:', err);
  }
};

initStats();

// Sauvegarde sur le disque toutes les 30 minutes (30 * 60 * 1000 ms)
setInterval(writeStats, 30 * 60 * 1000);

// Sauvegarde lors de la fermeture de l'application
const gracefulShutdown = () => {
  console.log('\nExtinction en cours... Sauvegarde des stats.');
  writeStats();
  process.exit(0);
};

process.on('SIGINT', gracefulShutdown);
process.on('SIGTERM', gracefulShutdown);

// Routes
app.post('/api/track/view', (req, res) => {
  stats.views = (stats.views || 0) + 1;
  res.json({ success: true, views: stats.views });
});

app.post('/api/track/click/:type', (req, res) => {
  const type = req.params.type;
  if (['download', 'modrinth', 'kofi'].includes(type)) {
    stats.clicks[type] = (stats.clicks[type] || 0) + 1;
    res.json({ success: true, clicks: stats.clicks[type] });
  } else {
    res.status(400).json({ error: 'Type invalide' });
  }
});

app.get('/api/stats', (req, res) => {
  res.json(stats);
});

app.listen(PORT, () => {
  console.log(`Tracking API à l'écoute sur http://localhost:${PORT}`);
});
