import { api, getToken, setToken } from './api.js';
import {
  COSMETICS, BY_ID, RARITY, DEFAULT_EQUIPPED,
} from '../../shared/catalog.js';
import {
  drawCosmetic, drawOutfit, rosterBySlot, SLOTS,
} from './render.js';
import { Game, WEAPONS } from './game.js';

const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => [...document.querySelectorAll(sel)];
const fmt = (n) => new Intl.NumberFormat('ar-EG').format(n || 0);

const state = {
  profile: null,
  shop: null,
  tab: 'shop',
  lockerSlot: 'outfit',
  previewId: null,
  game: null,
  lastResult: null,
};

/* ============================================================ boot/auth */

async function boot() {
  wireAuth();
  wireApp();
  if (getToken()) {
    try {
      state.profile = await api.me();
      enterApp();
      return;
    } catch {
      setToken(null);
    }
  }
  $('#authScreen').classList.remove('hidden');
}

function wireAuth() {
  $('#createBtn').addEventListener('click', createProfile);
  $('#nameInput').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') createProfile();
  });
}

async function createProfile() {
  const btn = $('#createBtn');
  const name = $('#nameInput').value.trim() || 'لاعب';
  btn.disabled = true;
  btn.textContent = 'جارٍ التجهيز…';
  try {
    const { token, profile } = await api.createProfile(name);
    setToken(token);
    state.profile = profile;
    enterApp();
  } catch (e) {
    btn.textContent = 'تعذّر الاتصال — أعد المحاولة';
    setTimeout(() => { btn.textContent = 'ابدأ اللعب'; btn.disabled = false; }, 1800);
  }
}

function enterApp() {
  $('#authScreen').classList.add('hidden');
  $('#app').classList.remove('hidden');
  refreshProfileUI();
  loadShop();
  buildLocker();
  startRotTimer();
}

/* ============================================================ app wiring */

function wireApp() {
  $$('.tab').forEach((t) => t.addEventListener('click', () => selectTab(t.dataset.tab)));
  $('#playBtn').addEventListener('click', launchGame);
  $('#againBtn').addEventListener('click', () => { hideResult(); launchGame(); });
  $('#toLobbyBtn').addEventListener('click', () => { hideResult(); showApp(); });
  $('#exitGame').addEventListener('click', () => {
    if (state.game) { state.game.stop(); state.game = null; }
    showApp();
  });
  $('#dailyBtn').addEventListener('click', claimDaily);
  $('#equipPreviewBtn').addEventListener('click', equipPreview);
}

function selectTab(name) {
  state.tab = name;
  $$('.tab').forEach((t) => t.classList.toggle('active', t.dataset.tab === name));
  $$('.tabpane').forEach((p) => p.classList.toggle('active', p.id === `tab-${name}`));
  if (name === 'locker') buildLocker();
  if (name === 'stats') refreshStats();
  if (name === 'shop') loadShop();
}

function refreshProfileUI() {
  const p = state.profile;
  if (!p) return;
  $('#playerName').textContent = p.name;
  $('#levelText').textContent = 'مستوى ' + p.level;
  $('#coinText').textContent = fmt(p.currency);
  $('#xpFill').style.width = Math.min(100, Math.round((p.xp / p.xpNeeded) * 100)) + '%';
  const daily = $('#dailyBtn');
  daily.disabled = !p.dailyReady;
  daily.classList.toggle('disabled', !p.dailyReady);
  daily.textContent = p.dailyReady ? 'مكافأة اليوم' : 'تم الاستلام ✓';
  renderAvatar();
}

function renderAvatar() {
  const host = $('#avatarCanvas');
  host.innerHTML = '';
  const c = document.createElement('canvas');
  c.width = 120; c.height = 120;
  const ctx = c.getContext('2d');
  const cos = BY_ID.get(state.profile?.equipped?.outfit) || BY_ID.get(DEFAULT_EQUIPPED.outfit);
  ctx.clearRect(0, 0, 120, 120);
  ctx.save();
  ctx.translate(60, 110);
  ctx.scale(0.92, 0.92);
  drawCosmetic(ctx, cos, 0, 0, 100);
  ctx.restore();
  host.appendChild(c);
}

/* ================================================================ shop */

async function loadShop() {
  const grid = $('#shopGrid');
  try {
    state.shop = await api.shop();
  } catch {
    $('#shopEmpty').classList.remove('hidden');
    return;
  }
  $('#shopEmpty').classList.add('hidden');
  grid.innerHTML = '';
  for (const entry of state.shop.items) {
    const item = BY_ID.get(entry.id);
    if (!item) continue;
    grid.appendChild(shopCard(item, entry));
  }
}

function shopCard(item, entry) {
  const rar = RARITY[item.rarity];
  const card = document.createElement('article');
  card.className = 'card';
  card.style.setProperty('--rar', rar.color);
  card.innerHTML = `
    <div class="card-art"><canvas width="260" height="260"></canvas>
      ${entry.discount ? `<div class="badge">-${entry.discount}%</div>` : ''}
    </div>
    <div class="card-body">
      <div class="card-name">${item.name}</div>
      <div class="card-sub">${item.en} · ${rar.name}</div>
    </div>
    <button class="btn buy">${entry.owned ? 'مملوك' : `◈ ${fmt(entry.price)}`}</button>
  `;
  const cvs = card.querySelector('canvas');
  drawCardArt(cvs, item);
  const btn = card.querySelector('.buy');
  if (entry.owned) { btn.disabled = true; btn.classList.add('disabled'); }
  btn.addEventListener('click', () => buyItem(item, entry, btn));
  return card;
}

function drawCardArt(canvas, item) {
  const ctx = canvas.getContext('2d');
  const g = ctx.createLinearGradient(0, 0, 0, canvas.height);
  g.addColorStop(0, 'rgba(255,255,255,0.06)');
  g.addColorStop(1, 'rgba(0,0,0,0.25)');
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  // rarity glow
  const rar = RARITY[item.rarity];
  const rg = ctx.createRadialGradient(canvas.width / 2, canvas.height * 0.55, 6, canvas.width / 2, canvas.height * 0.55, canvas.width * 0.5);
  rg.addColorStop(0, hexA(rar.color, 0.28));
  rg.addColorStop(1, 'rgba(0,0,0,0)');
  ctx.fillStyle = rg;
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  const scale = item.slot === 'pickaxe' ? 2.0 : item.slot === 'wrap' ? 1.5 : 2.0;
  if (item.slot === 'outfit') {
    ctx.save();
    ctx.translate(canvas.width / 2, canvas.height / 2 + 40);
    ctx.scale(2.05, 2.05);
    drawOutfit(ctx, item);
    ctx.restore();
  } else {
    drawCosmetic(ctx, item, canvas.width / 2, canvas.height / 2, 100 * scale);
  }
}

async function buyItem(item, entry, btn) {
  btn.disabled = true;
  try {
    const res = await api.buy(item.id);
    state.profile = res.profile;
    refreshProfileUI();
    btn.textContent = 'مملوك';
    btn.classList.add('disabled');
    toast(`تم شراء ${item.name}`, 'ok');
    buildLocker();
  } catch (e) {
    const msg = {
      insufficient_funds: 'لا تملك عملات كافية',
      not_in_shop_today: 'هذا العنصر ليس معروضاً اليوم',
      already_owned: 'تملك هذا العنصر بالفعل',
    }[e.message] || 'تعذّر الشراء';
    toast(msg, 'bad');
    btn.disabled = false;
    if (e.message === 'already_owned') btn.textContent = 'مملوك';
  }
}

let rotTimerId = null;
function startRotTimer() {
  if (rotTimerId) clearInterval(rotTimerId);
  const tick = () => {
    const el = $('#rotTimer');
    if (!el) return;
    const now = Date.now();
    const next = Date.UTC(
      new Date().getUTCFullYear(), new Date().getUTCMonth(), new Date().getUTCDate() + 1
    );
    const d = Math.max(0, next - now);
    const h = String(Math.floor(d / 3600000)).padStart(2, '0');
    const m = String(Math.floor((d % 3600000) / 60000)).padStart(2, '0');
    const s = String(Math.floor((d % 60000) / 1000)).padStart(2, '0');
    el.textContent = `${h}:${m}:${s}`;
  };
  tick();
  rotTimerId = setInterval(tick, 1000);
}

async function claimDaily() {
  try {
    const res = await api.claimDaily();
    state.profile = res.profile;
    refreshProfileUI();
    toast(`مكافأة اليوم: +${fmt(res.amount)} ◈ (سلسلة ${res.streak} يوم)`, 'gold');
    loadShop();
  } catch (e) {
    toast(e.message === 'already_claimed' ? 'استلمت مكافأة اليوم بالفعل' : 'تعذّر الاستلام', 'bad');
  }
}

/* ============================================================== locker */

function buildLocker() {
  const tabs = $('#slotTabs');
  tabs.innerHTML = '';
  for (const s of SLOTS) {
    const b = document.createElement('button');
    b.className = 'slot-tab' + (state.lockerSlot === s.slot ? ' active' : '');
    b.textContent = s.label;
    b.addEventListener('click', () => {
      state.lockerSlot = s.slot;
      state.previewId = null;
      buildLocker();
    });
    tabs.appendChild(b);
  }
  const grid = $('#lockerGrid');
  grid.innerHTML = '';
  const group = rosterBySlot().find((g) => g.slot === state.lockerSlot);
  const owned = new Set(state.profile?.owned || []);
  const equipped = state.profile?.equipped || {};
  for (const item of group.items) {
    if (!owned.has(item.id)) continue;
    const rar = RARITY[item.rarity];
    const card = document.createElement('button');
    card.className = 'cos-card' + (equipped[item.slot] === item.id ? ' equipped' : '');
    card.style.setProperty('--rar', rar.color);
    card.innerHTML = `<canvas width="150" height="150"></canvas>
      <span class="cos-name">${item.name}</span>
      ${equipped[item.slot] === item.id ? '<span class="eq-tag">مُجهّز</span>' : ''}`;
    drawCosmeticArt(card.querySelector('canvas'), item);
    card.addEventListener('click', () => {
      state.previewId = item.id;
      renderPreview();
    });
    grid.appendChild(card);
  }
  if (!grid.children.length) {
    grid.innerHTML = '<p class="empty">لا تملك عناصر في هذه الفئة بعد — تفضّل بزيارة المتجر.</p>';
  }
  renderPreview();
}

function drawCosmeticArt(canvas, item) {
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  if (item.slot === 'outfit') {
    ctx.save();
    ctx.translate(canvas.width / 2, canvas.height / 2 + 26);
    ctx.scale(1.18, 1.18);
    drawOutfit(ctx, item);
    ctx.restore();
  } else {
    drawCosmetic(ctx, item, canvas.width / 2, canvas.height / 2, 86);
  }
}

function renderPreview() {
  const equippedId = state.profile?.equipped?.[state.lockerSlot];
  const item = BY_ID.get(state.previewId || equippedId) ||
    BY_ID.get(DEFAULT_EQUIPPED[state.lockerSlot]);
  const cvs = $('#previewCanvas');
  const ctx = cvs.getContext('2d');
  ctx.clearRect(0, 0, cvs.width, cvs.height);
  const bg = ctx.createRadialGradient(cvs.width / 2, cvs.height * 0.45, 20, cvs.width / 2, cvs.height * 0.45, cvs.width * 0.75);
  bg.addColorStop(0, hexA(RARITY[item.rarity].color, 0.22));
  bg.addColorStop(1, 'rgba(0,0,0,0)');
  ctx.fillStyle = bg;
  ctx.fillRect(0, 0, cvs.width, cvs.height);
  // pedestal
  ctx.fillStyle = 'rgba(255,255,255,0.06)';
  ctx.beginPath();
  ctx.ellipse(cvs.width / 2, cvs.height - 60, 110, 26, 0, 0, Math.PI * 2);
  ctx.fill();

  if (state.lockerSlot === 'outfit') {
    // draw the full outfit set: back bling behind, pickaxe beside
    const outfit = item.slot === 'outfit' ? item : BY_ID.get(state.profile?.equipped.outfit);
    const back = BY_ID.get(state.profile?.equipped.backbling);
    const pick = BY_ID.get(state.profile?.equipped.pickaxe);
    if (back) drawCosmetic(ctx, back, cvs.width / 2 - 70, cvs.height / 2 - 20, 150);
    ctx.save();
    ctx.translate(cvs.width / 2 + 10, cvs.height / 2 + 40);
    ctx.scale(2.35, 2.35);
    drawOutfit(ctx, outfit, performance.now());
    ctx.restore();
    if (pick) drawCosmetic(ctx, pick, cvs.width / 2 + 100, cvs.height / 2, 130);
  } else {
    ctx.save();
    ctx.translate(cvs.width / 2, cvs.height / 2 + 30);
    ctx.scale(2.3, 2.3);
    drawCosmetic(ctx, item, 0, -20, 100);
    ctx.restore();
  }

  $('#previewName').textContent = item.name;
  $('#previewRarity').textContent = RARITY[item.rarity].name + ' · ' + item.en;
  $('#previewRarity').style.color = RARITY[item.rarity].color;
  const btn = $('#equipPreviewBtn');
  const isEquipped = state.profile?.equipped?.[state.lockerSlot] === item.id;
  btn.classList.toggle('hidden', isEquipped);
}

async function equipPreview() {
  const id = state.previewId || state.profile?.equipped?.[state.lockerSlot];
  const item = BY_ID.get(id);
  if (!item) return;
  try {
    const res = await api.equip(item.slot, item.id);
    state.profile = res.profile;
    toast(`تم تجهيز ${item.name}`, 'ok');
    buildLocker();
    renderAvatar();
  } catch { toast('تعذّر التجهيز', 'bad'); }
}

function refreshStats() {
  const p = state.profile;
  if (!p) return;
  $('#stMatches').textContent = fmt(p.lifetime.matches);
  $('#stWins').textContent = fmt(p.lifetime.wins);
  $('#stKills').textContent = fmt(p.lifetime.kills);
  $('#stDamage').textContent = fmt(p.lifetime.damage);
  $('#stLevel').textContent = fmt(p.level);
  $('#stOwned').textContent = `${fmt(p.owned.length)} / ${COSMETICS.length}`;
}

/* ================================================================ game */

function launchGame() {
  $('#resultScreen').classList.add('hidden');
  $('#app').classList.add('hidden');
  $('#gameScreen').classList.remove('hidden');
  const canvas = $('#gameCanvas');
  const game = new Game(canvas, { equipped: state.profile.equipped });
  state.game = game;
  wireGameEvents(game);
  game.start();
  requestAnimationFrame(() => game.resize());
  startHudLoop();
}

function wireGameEvents(game) {
  game.on('toast', (msg) => toast(msg, 'ok'));
  game.on('kill', (name) => addKillFeed(`أقصيت ${name}`));
  game.on('zone', (z) => {
    toast(`العاصفة تتقلّص — المرحلة ${z.phase}`, 'bad');
  });
  game.on('end', (result) => {
    stopHudLoop();
    showResult(result);
  });
}

let hudRaf = null;
function startHudLoop() {
  stopHudLoop();
  const tick = () => {
    const game = state.game;
    if (!game) return;
    const d = game.getHudData();
    $('#hudAlive').textContent = d.alive;
    $('#hudKills').textContent = d.kills;
    $('#hudWood').textContent = d.materials.wood;
    $('#hudStone').textContent = d.materials.stone;
    $('#hudMetal').textContent = d.materials.metal;
    $('#hpFill').style.width = d.hp + '%';
    $('#hpText').textContent = d.hp;
    $('#shieldFill').style.width = d.shield + '%';
    $('#shieldText').textContent = d.shield;
    $('#hudWeapon').textContent = d.weapon;
    $('#hudMag').textContent = d.mag;
    $('#hudAmmo').textContent = d.ammo;
    $('#reloadTag').classList.toggle('hidden', !d.reloading);
    $('#buildBadge').classList.toggle('hidden', !d.buildMode);
    $('#hudBuild').textContent = d.buildKind;
    $('#hudStorm').textContent = d.inStorm ? 'خطر!' : d.stormNext + 's';
    $('#stormPill').classList.toggle('danger', d.inStorm);
    $('#stormWarn').classList.toggle('hidden', !d.inStorm);
    const prompt = $('#pickupPrompt');
    if (game.pickupTarget) {
      const name = WEAPONS[game.pickupTarget.type]?.name ||
        ({ shield: 'درع', medkit: 'حقيبة إسعاف', wood: 'خشب', stone: 'حجر', metal: 'معدن' }[game.pickupTarget.type] || 'عنصر');
      prompt.innerHTML = `التقط <b>${name}</b> — اضغط <kbd>F</kbd>`;
      prompt.classList.remove('hidden');
    } else {
      prompt.classList.add('hidden');
    }
    hudRaf = requestAnimationFrame(tick);
  };
  hudRaf = requestAnimationFrame(tick);
}
function stopHudLoop() { if (hudRaf) cancelAnimationFrame(hudRaf); hudRaf = null; }

async function showResult(result) {
  state.lastResult = result;
  $('#resultTitle').textContent = result.win ? '🏆 فوز ملكي!' : 'انتهت المباراة';
  $('#resultPlace').textContent = '#' + result.place;
  $('#resultPlace').classList.toggle('win', result.win);
  $('#resKills').textContent = result.kills;
  $('#resDamage').textContent = fmt(result.damage);
  $('#resAcc').textContent = result.accuracy + '%';
  $('#resXp').textContent = '+0';
  $('#resCoins').textContent = '+0';
  $('#resultScreen').classList.remove('hidden');
  if (state.game) { state.game.stop(); }
  try {
    const res = await api.matchResult({
      place: result.place, kills: result.kills, damage: result.damage,
    });
    state.profile = res.profile;
    refreshProfileUI();
    $('#resXp').textContent = '+' + fmt(res.earnedXp);
    $('#resCoins').textContent = '+' + fmt(res.earnedCoins);
    if (res.levelsGained > 0) toast(`ارتفعت إلى المستوى ${res.profile.level}!`, 'gold');
    loadShop();
  } catch (e) {
    toast('تعذّر حفظ نتيجة المباراة', 'bad');
  }
}

function hideResult() {
  $('#resultScreen').classList.add('hidden');
  if (state.game) { state.game.stop(); state.game = null; }
}

function showApp() {
  stopHudLoop();
  if (state.game) { state.game.stop(); state.game = null; }
  $('#gameScreen').classList.add('hidden');
  $('#app').classList.remove('hidden');
  refreshProfileUI();
  loadShop();
  if (state.tab === 'locker') buildLocker();
  if (state.tab === 'stats') refreshStats();
}

/* =============================================================== misc */

function toast(msg, kind = 'ok') {
  const host = $('#toasts');
  if (!host) return;
  const el = document.createElement('div');
  el.className = 'toast ' + kind;
  el.textContent = msg;
  host.appendChild(el);
  setTimeout(() => el.classList.add('out'), 2200);
  setTimeout(() => el.remove(), 2700);
}

function addKillFeed(msg) {
  const host = $('#killfeed');
  if (!host) return;
  const el = document.createElement('div');
  el.className = 'feed-item';
  el.textContent = msg;
  host.appendChild(el);
  setTimeout(() => el.remove(), 4000);
}

function hexA(hex, a) {
  const h = hex.replace('#', '');
  const r = parseInt(h.slice(0, 2), 16);
  const g = parseInt(h.slice(2, 4), 16);
  const b = parseInt(h.slice(4, 6), 16);
  return `rgba(${r},${g},${b},${a})`;
}

boot();
