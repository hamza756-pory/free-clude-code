import http from 'node:http';
import fs from 'node:fs';
import fsp from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import {
  BY_ID, STARTER_OWNED, DEFAULT_EQUIPPED, dailyShop, dayKey,
} from './shared/catalog.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PORT = Number(process.env.PORT || 3000);
const HOST = '0.0.0.0';
const DATA_DIR = path.join(__dirname, 'data');
const DB_FILE = path.join(DATA_DIR, 'db.json');
const SEASON_DAYS = 30;
const DAILY_BONUS = 150;
const MAX_LEVEL = 50;

/* ------------------------------------------------------------------ store */

let db = { players: {} };
let saveTimer = null;

async function loadDb() {
  try {
    db = JSON.parse(await fsp.readFile(DB_FILE, 'utf8'));
    if (!db.players) db.players = {};
  } catch {
    db = { players: {} };
  }
}
function saveDbSoon() {
  if (saveTimer) return;
  saveTimer = setTimeout(async () => {
    saveTimer = null;
    try {
      await fsp.mkdir(DATA_DIR, { recursive: true });
      await fsp.writeFile(DB_FILE, JSON.stringify(db, null, 2));
    } catch (e) {
      console.error('save failed', e.message);
    }
  }, 400);
}

/* ---------------------------------------------------------------- helpers */

const rand = (n) => crypto.randomInt(n);
const newId = () => crypto.randomBytes(12).toString('hex');
const today = () => dayKey();

function xpForLevel(level) {
  // level 1 -> 2 needs 300 xp, grows linearly
  return 250 + level * 50;
}

function newProfile(id, name) {
  return {
    id,
    name,
    createdAt: Date.now(),
    currency: 600,
    xp: 0,
    level: 1,
    owned: [...STARTER_OWNED],
    equipped: { ...DEFAULT_EQUIPPED },
    lastDailyClaim: null,
    lifetime: { matches: 0, kills: 0, wins: 0, damage: 0 },
  };
}

function publicProfile(p) {
  return {
    id: p.id,
    name: p.name,
    currency: p.currency,
    xp: p.xp,
    xpNeeded: xpForLevel(p.level),
    level: p.level,
    maxLevel: MAX_LEVEL,
    owned: p.owned,
    equipped: p.equipped,
    dailyReady: p.lastDailyClaim !== today(),
    seasonProgress: Math.min(1, (p.level - 1) / (MAX_LEVEL - 1)),
    lifetime: p.lifetime,
  };
}

function addXp(p, amount) {
  let gained = 0;
  p.xp += amount;
  while (p.level < MAX_LEVEL && p.xp >= xpForLevel(p.level)) {
    p.xp -= xpForLevel(p.level);
    p.level++;
    gained++;
  }
  if (p.level >= MAX_LEVEL) p.xp = Math.min(p.xp, xpForLevel(MAX_LEVEL) - 1);
  return gained;
}

const sessions = new Map(); // token -> playerId

function playerFromReq(req) {
  const auth = req.headers.authorization || '';
  const token = auth.startsWith('Bearer ') ? auth.slice(7) : null;
  if (!token) return null;
  const id = sessions.get(token);
  return id ? db.players[id] || null : null;
}

/* ------------------------------------------------------------------ http  */

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.ico': 'image/x-icon',
  '.map': 'application/json',
};

function send(res, code, body, headers = {}) {
  const payload = typeof body === 'string' || Buffer.isBuffer(body)
    ? body
    : JSON.stringify(body);
  res.writeHead(code, {
    'Content-Type': typeof body === 'object' && !Buffer.isBuffer(body)
      ? 'application/json; charset=utf-8' : 'text/plain; charset=utf-8',
    'Cache-Control': 'no-store',
    ...headers,
  });
  res.end(payload);
}

async function readJson(req) {
  const chunks = [];
  let size = 0;
  for await (const c of req) {
    size += c.length;
    if (size > 1e6) throw new Error('payload too large');
    chunks.push(c);
  }
  if (!chunks.length) return {};
  try { return JSON.parse(Buffer.concat(chunks).toString('utf8')); }
  catch { throw new Error('invalid json'); }
}

const api = {
  'GET /api/health': () => ({ ok: true, day: today() }),

  'POST /api/profile': async (req) => {
    const body = await readJson(req);
    const name = String(body.name || '').trim().slice(0, 18) || 'لاعب';
    const id = newId();
    const p = newProfile(id, name);
    db.players[id] = p;
    const token = crypto.randomBytes(24).toString('hex');
    sessions.set(token, id);
    saveDbSoon();
    return { token, profile: publicProfile(p) };
  },

  'GET /api/me': (req, res, ctx) => {
    if (!ctx.player) return send(res, 401, { error: 'unauthorized' });
    return publicProfile(ctx.player);
  },

  'GET /api/shop': (req, res, ctx) => {
    const shop = dailyShop(new Date());
    const owned = ctx.player ? new Set(ctx.player.owned) : new Set();
    return {
      ...shop,
      items: shop.items.map((it) => ({ ...it, owned: owned.has(it.id) })),
    };
  },

  'POST /api/daily': (req, res, ctx) => {
    if (!ctx.player) return send(res, 401, { error: 'unauthorized' });
    const p = ctx.player;
    if (p.lastDailyClaim === today()) {
      return send(res, 409, { error: 'already_claimed', profile: publicProfile(p) });
    }
    // Streak: consecutive days grant a bonus.
    const yesterday = dayKey(new Date(Date.now() - 86400000));
    p.streak = p.lastDailyClaim === yesterday ? Math.min(7, (p.streak || 1) + 1) : 1;
    p.lastDailyClaim = today();
    const amount = DAILY_BONUS + (p.streak - 1) * 25;
    p.currency += amount;
    saveDbSoon();
    return { amount, streak: p.streak, profile: publicProfile(p) };
  },

  'POST /api/buy': async (req, res, ctx) => {
    if (!ctx.player) return send(res, 401, { error: 'unauthorized' });
    const p = ctx.player;
    const { id } = await readJson(req);
    const item = BY_ID.get(id);
    if (!item || item.price === 0) return send(res, 400, { error: 'bad_item' });
    if (p.owned.includes(id)) return send(res, 409, { error: 'already_owned' });
    const shop = dailyShop(new Date());
    const entry = shop.items.find((i) => i.id === id);
    if (!entry) return send(res, 403, { error: 'not_in_shop_today' });
    if (p.currency < entry.price) return send(res, 402, { error: 'insufficient_funds' });
    p.currency -= entry.price;
    p.owned.push(id);
    saveDbSoon();
    return { ok: true, profile: publicProfile(p) };
  },

  'POST /api/equip': async (req, res, ctx) => {
    if (!ctx.player) return send(res, 401, { error: 'unauthorized' });
    const p = ctx.player;
    const { slot, id } = await readJson(req);
    const item = BY_ID.get(id);
    if (!item || item.slot !== slot) return send(res, 400, { error: 'bad_item' });
    if (!p.owned.includes(id)) return send(res, 403, { error: 'not_owned' });
    p.equipped[slot] = id;
    saveDbSoon();
    return { ok: true, profile: publicProfile(p) };
  },

  'POST /api/match-result': async (req, res, ctx) => {
    if (!ctx.player) return send(res, 401, { error: 'unauthorized' });
    const p = ctx.player;
    const body = await readJson(req);
    const place = Math.max(1, Math.min(100, Number(body.place) || 100));
    const kills = Math.max(0, Math.min(60, Number(body.kills) || 0));
    const damage = Math.max(0, Math.min(20000, Number(body.damage) || 0));
    const won = place === 1;

    const placementXp = Math.round((100 - place) * 2.2) + (won ? 120 : 0);
    const earnedXp = placementXp + kills * 35 + Math.round(damage * 0.12);
    const earnedCoins = (won ? 220 : Math.round(45 + (100 - place) * 0.9)) + kills * 12;

    p.lifetime.matches++;
    p.lifetime.kills += kills;
    p.lifetime.damage += damage;
    if (won) p.lifetime.wins++;
    p.currency += earnedCoins;
    const levels = addXp(p, earnedXp);
    saveDbSoon();
    return {
      earnedXp, earnedCoins, levelsGained: levels, profile: publicProfile(p),
    };
  },
};

function routeApi(req, res) {
  const url = new URL(req.url, 'http://x');
  const key = `${req.method} ${url.pathname}`;
  const handler = api[key];
  if (!handler) return send(res, 404, { error: 'no_route' });
  const ctx = { player: playerFromReq(req) };
  Promise.resolve()
    .then(() => handler(req, res, ctx))
    .then((result) => {
      if (result !== undefined && !res.writableEnded) send(res, 200, result);
    })
    .catch((e) => {
      if (!res.writableEnded) send(res, 400, { error: e.message });
    });
}

async function serveStatic(req, res) {
  const url = new URL(req.url, 'http://x');
  let rel = decodeURIComponent(url.pathname);
  if (rel === '/') rel = '/index.html';
  // /shared/* is served straight from the repo so client and server share one catalog.
  const base = rel.startsWith('/shared/') ? __dirname : path.join(__dirname, 'public');
  const filePath = path.join(base, path.normalize(rel).replace(/^(\.\.[/\\])+/, ''));
  if (!filePath.startsWith(base)) return send(res, 403, 'forbidden');
  try {
    const stat = await fsp.stat(filePath);
    if (stat.isDirectory()) throw new Error('dir');
    const ext = path.extname(filePath).toLowerCase();
    res.writeHead(200, {
      'Content-Type': MIME[ext] || 'application/octet-stream',
      'Cache-Control': ext === '.html' ? 'no-store' : 'public, max-age=300',
      'X-Content-Type-Options': 'nosniff',
    });
    fs.createReadStream(filePath).pipe(res);
  } catch {
    send(res, 404, 'not found');
  }
}

const server = http.createServer((req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Headers', 'content-type,authorization');
  res.setHeader('Access-Control-Allow-Methods', 'GET,POST,OPTIONS');
  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    return res.end();
  }
  if (req.url.startsWith('/api/')) return routeApi(req, res);
  return serveStatic(req, res);
});

await loadDb();
server.listen(PORT, HOST, () => {
  console.log(`NEON ROYALE running at http://${HOST}:${PORT}`);
});
