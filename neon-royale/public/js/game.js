// Battle royale gameplay: 3000-unit arena, 64 combatants, loot, builds, storm.
// Perspective is hand-rolled (no engine, no external assets): entities are
// depth-sorted by distance and scaled by focal/depth.
import { drawOutfit, drawPick, roundRect, resetAcc, addAcc, getAcc } from './render.js';
import { BY_ID, DEFAULT_EQUIPPED } from '../../shared/catalog.js';

const TAU = Math.PI * 2;
const MAP = 3000;
const ENEMY_COUNT = 63;
const STORM_PAD = 900;
// max damage the human player can absorb per second (anti-gang-up guard)
const DAMAGE_BUDGET_PER_SEC = 72;
const HORIZON = 0.46; // fraction of screen height where the ground plane starts
const MAX_MATCH_MS = 12 * 60000; // safety valve for a stalling endgame

const WEAPONS = {
  pistol: { name: 'مسدس', type: 'hitscan', dmg: 15, rate: 300, spread: 0.10, range: 900, mag: 10, reload: 1100, head: 1.7, color: '#c9d4ff' },
  smg: { name: 'رشاش', type: 'hitscan', dmg: 11, rate: 95, spread: 0.14, range: 800, mag: 30, reload: 1500, head: 1.5, color: '#8ef2c6' },
  rifle: { name: 'بندقية', type: 'hitscan', dmg: 23, rate: 210, spread: 0.05, range: 1600, mag: 20, reload: 1700, head: 2.0, color: '#ffd08a' },
  shotgun: { name: 'شوزن', type: 'pellets', dmg: 9, pellets: 9, rate: 850, spread: 0.34, range: 380, mag: 6, reload: 2100, head: 1.4, color: '#ff9d7a', falloff: true },
  sniper: { name: 'قناص', type: 'hitscan', dmg: 95, rate: 1900, spread: 0.008, range: 3200, mag: 1, reload: 2600, head: 2.5, color: '#c9a4ff' },
};

export class Game {
  constructor(canvas, opts = {}) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');
    this.equipped = opts.equipped || DEFAULT_EQUIPPED;
    this.cosmeticCache = {};
    this.listeners = {};
    this.onPickupPrompt = opts.onPickupPrompt || (() => {});
    this.onZoneEvent = opts.onZoneEvent || (() => {});
    this.keys = new Set();
    this.mouse = { sx: 0, sy: 0, down: false };
    this.running = false;
    this.resize();
    this._onResize = () => this.resize();
    window.addEventListener('resize', this._onResize);
  }

  on(evt, fn) { this.listeners[evt] = fn; return this; }
  emit(evt, payload) { this.listeners[evt]?.(payload); }

  cosmetic(id) {
    if (!this.cosmeticCache[id]) this.cosmeticCache[id] = BY_ID.get(id) || null;
    return this.cosmeticCache[id];
  }

  resize() {
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    const rect = this.canvas.getBoundingClientRect();
    this.W = Math.max(320, rect.width);
    this.H = Math.max(240, rect.height);
    this.canvas.width = Math.floor(this.W * dpr);
    this.canvas.height = Math.floor(this.H * dpr);
    this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    this.focal = this.W / 2;
  }

  /* -------------------------------------------------------------- lifecycle */

  start() {
    this.acc = 0;
    this.time = 0;
    this.over = false;
    this.storm = { cx: 0, cz: 0, r: 2400, tx: 0, tz: 0, targetR: 1700, next: 12, phase: 0 };
    this.placements = ENEMY_COUNT + 1;
    this.aliveCount = ENEMY_COUNT + 1;
    this.structures = [];
    this.bullets = [];
    this.particles = [];
    this.loot = [];
    this.damageNumbers = [];
    this.buildMode = false;
    this.buildIndex = 0;
    this.pickupTarget = null;
    this.materials = { wood: 120, stone: 60, metal: 30 };
    this.buildCost = { wood: 10, stone: 10, metal: 10 };
    this.hud = { kills: 0, damage: 0, shots: 0, hits: 0 };
    this.player = this.makePlayer('أنت', true);
    this.bots = [];
    // random spawn ring
    const sr = 1500 + Math.random() * 500;
    const sa = Math.random() * TAU;
    this.player.x = Math.cos(sa) * sr;
    this.player.z = Math.sin(sa) * sr;
    this.spawnBots();
    this.scatterLoot();
    this.running = true;
    this.last = performance.now();
    this.loop = this.loop.bind(this);
    this.raf = requestAnimationFrame(this.loop);
    this._bindUI();
    this.emit('start');
  }

  stop() {
    this.running = false;
    cancelAnimationFrame(this.raf);
    this._unbindUI();
  }

  makePlayer(name, isHuman, gear) {
    const startGear = gear || (isHuman ? { weapon: null, ammo: 0 } : { weapon: null, ammo: 0 });
    return {
      name,
      isHuman,
      x: 0, z: 0,
      hp: 100, shield: isHuman ? 0 : 50,
      yaw: 0,
      pitch: 0,
      alive: true,
      team: isHuman ? 'p' : 'bot',
      outfit: isHuman ? 'outfit_recruit' : this.randomRoster(),
      weapon: startGear.weapon,
      ammo: startGear.ammo,
      mag: 0,
      reloadingUntil: 0,
      // staggered warm-up: no one fires in the first instant of a match
      nextShot: 900 + Math.random() * 2400,
      kills: 0,
      ai: {
        target: null,
        aimErr: 0,
        wander: Math.random() * TAU,
        thinkT: 0,
        strafe: Math.random() < 0.5 ? -1 : 1,
      },
    };
  }

  randomRoster() {
    const pool = ['outfit_neon', 'outfit_desert', 'outfit_sakura', 'outfit_frost',
      'outfit_ember', 'outfit_cyber', 'outfit_void', 'outfit_shadow', 'outfit_gold'];
    return pool[Math.floor(Math.random() * pool.length)];
  }

  spawnBots() {
    const roster = ['outfit_neon', 'outfit_desert', 'outfit_sakura', 'outfit_frost',
      'outfit_ember', 'outfit_cyber', 'outfit_void', 'outfit_shadow', 'outfit_gold'];
    const wl = ['pistol', 'pistol', 'smg', 'smg', 'rifle', 'rifle', 'shotgun', 'sniper'];
    for (let i = 0; i < ENEMY_COUNT; i++) {
      const b = this.makePlayer('بوت ' + (i + 1), false);
      const r = 500 + Math.random() * (MAP / 2 - 600);
      const a = Math.random() * TAU;
      b.x = Math.cos(a) * r;
      b.z = Math.sin(a) * r;
      if (Math.hypot(b.x - this.player.x, b.z - this.player.z) < 350) {
        b.x = -b.x; b.z = -b.z;
      }
      b.outfit = roster[i % roster.length];
      const wid = wl[Math.floor(Math.random() * wl.length)];
      b.weapon = wid;
      b.ammo = 999;
      b.hp = 80 + Math.random() * 40;
      b.shield = Math.random() < 0.45 ? 50 : 0;
      b.ai.aimErr = 0.05 + Math.random() * 0.1;
      b.ai.skill = 0.4 + Math.random() * 0.6;
      this.bots.push(b);
    }
  }

  scatterLoot() {
    const table = [
      ['pistol', 30], ['smg', 22], ['rifle', 16], ['shotgun', 16], ['sniper', 7],
      ['shield', 24], ['wood', 26], ['stone', 20], ['metal', 14], ['medkit', 14],
    ];
    const total = table.reduce((s, t) => s + t[1], 0);
    const types = ['pistol', 'smg', 'rifle', 'shotgun', 'sniper', 'shield', 'wood', 'stone', 'metal', 'medkit'];
    const weightOf = Object.fromEntries(table);
    for (let i = 0; i < 520; i++) {
      const r = 120 + Math.random() * (MAP / 2 - 220);
      const a = Math.random() * TAU;
      let roll = Math.random() * total;
      let type = 'wood';
      for (const t of types) { roll -= weightOf[t] || 0; if (roll <= 0) { type = t; break; } }
      this.loot.push({
        x: Math.cos(a) * r, z: Math.sin(a) * r, type,
        amount: type === 'medkit' ? 1 : type === 'shield' ? 1 : type === 'sniper' ? 1 : 2 + Math.floor(Math.random() * 3),
        bob: Math.random() * TAU,
      });
    }
    // guarantee one weapon near the drop point
    this.loot.push({ x: this.player.x + 120, z: this.player.z + 90, type: 'rifle', amount: 1, bob: 0 });
    this.loot.push({ x: this.player.x - 90, z: this.player.z + 130, type: 'shield', amount: 1, bob: 0 });
    this.loot.push({ x: this.player.x + 40, z: this.player.z - 150, type: 'wood', amount: 3, bob: 0 });
  }

  /* ------------------------------------------------------------------- UI */

  _bindUI() {
    const c = this.canvas;
    this._kd = (e) => {
      if (!this.running || this.over) return;
      this.keys.add(e.code);
      if (['Tab', 'Space', 'KeyQ', 'KeyE', 'KeyR', 'KeyB', 'KeyF', 'Digit1', 'Digit2'].includes(e.code)) e.preventDefault();
      if (e.code === 'Tab') this.buildMode = !this.buildMode;
      if (e.code === 'KeyB') this.buildMode = true;
      if (e.code === 'KeyQ') this.buildMode = false;
      if (e.code === 'KeyE') this.weaponSwitch();
      if (e.code === 'KeyR') this.reload();
      if (e.code === 'KeyF' || e.code === 'KeyZ') this.interact();
      if (e.code === 'Digit1') { this.buildMode = true; this.buildIndex = 0; }
      if (e.code === 'Digit2') { this.buildMode = true; this.buildIndex = 1; }
      if (e.code === 'Escape') this.buildMode = false;
      if (this.buildMode && ['Digit3', 'Digit4'].includes(e.code)) {
        this.buildIndex = e.code === 'Digit3' ? 2 : 3;
      }
    };
    this._ku = (e) => this.keys.delete(e.code);
    this._mm = (e) => {
      const r = c.getBoundingClientRect();
      this.mouse.sx = e.clientX - r.left;
      this.mouse.sy = e.clientY - r.top;
    };
    this._md = (e) => {
      if (!this.running || this.over) return;
      if (e.button === 0) {
        this.mouse.down = true;
        if (this.buildMode) this.tryBuild();
        else this.shoot();
      }
      if (e.button === 2) {
        this.buildMode = false;
        this.mouse.down = false;
      }
    };
    this._mu = (e) => { if (e.button === 0) this.mouse.down = false; };
    this._cm = (e) => e.preventDefault();
    window.addEventListener('keydown', this._kd);
    window.addEventListener('keyup', this._ku);
    window.addEventListener('mousemove', this._mm);
    c.addEventListener('mousedown', this._md);
    window.addEventListener('mouseup', this._mu);
    c.addEventListener('contextmenu', this._cm);
    this._mm({ clientX: 0, clientY: 0 });
  }

  _unbindUI() {
    window.removeEventListener('keydown', this._kd);
    window.removeEventListener('keyup', this._ku);
    window.removeEventListener('mousemove', this._mm);
    this.canvas.removeEventListener('mousedown', this._md);
    window.removeEventListener('mouseup', this._mu);
    this.canvas.removeEventListener('contextmenu', this._cm);
  }

  /* ---------------------------------------------------------------- combat */

  weaponSwitch() {
    const p = this.player;
    if (!p.weapon) return;
    const inv = this.player.inventory || [p.weapon];
    const i = inv.indexOf(p.weapon);
    p.weapon = inv[(i + 1) % inv.length];
    p.mag = Math.min(p.mag || 0, WEAPONS[p.weapon].mag);
    this.emit('toast', WEAPONS[p.weapon].name);
  }

  reload() {
    const p = this.player;
    if (!p.weapon || p.reloadingUntil > this.time) return;
    const w = WEAPONS[p.weapon];
    if (p.mag >= w.mag || p.ammo <= 0) return;
    p.reloadingUntil = this.time + w.reload;
  }

  shoot() {
    const p = this.player;
    if (!p.weapon) return;
    const w = WEAPONS[p.weapon];
    if (this.time < p.nextShot || this.time < p.reloadingUntil) return;
    if (p.mag <= 0) { this.reload(); return; }
    p.nextShot = this.time + w.rate;
    p.mag--;
    this.hud.shots++;
    const spread = w.spread * (this.keys.has('ShiftLeft') ? 0.55 : 1);
    const dirYaw = p.yaw + (Math.random() - 0.5) * spread;
    const pellets = w.pellets || 1;
    for (let i = 0; i < pellets; i++) {
      const a = dirYaw + (pellets > 1 ? (Math.random() - 0.5) * spread : 0);
      const hit = this.raycast(p, a, w);
      if (hit) {
        const dmg = Math.round(hit.dmg);
        hit.target.hp -= dmg;
        this.hud.hits++;
        this.hud.damage += dmg;
        this.damageNumbers.push({ x: hit.target.x, z: hit.target.z, y: 70, v: dmg, t: this.time });
        this.spawnParticles(hit.x, hit.z, '#ff5c7a', 5);
        if (hit.target.hp <= 0) this.killBot(hit.target);
      }
    }
    // muzzle flash particles
    this.spawnParticles(p.x + Math.cos(p.yaw) * 18, p.z + Math.sin(p.yaw) * 18, w.color, 3);
  }

  raycast(shooter, yaw, w) {
    const dirX = Math.cos(yaw);
    const dirZ = Math.sin(yaw);
    let best = null;
    const maxT = w.range;
    for (const b of this.bots) {
      if (!b.alive) continue;
      const rx = b.x - shooter.x;
      const rz = b.z - shooter.z;
      const t = rx * dirX + rz * dirZ;
      if (t < 0 || t > maxT) continue;
      const perp = Math.abs(rx * dirZ - rz * dirX);
      if (perp > 17) continue;
      if (this.structureBlocked(shooter, b.x, b.z)) continue;
      if (!best || t < best.t) {
        let dmg = w.dmg;
        if (w.falloff) {
          const f = 1 - t / (maxT * 1.4);
          dmg *= Math.max(0.35, f);
        }
        best = { target: b, t, x: b.x, z: b.z, dmg };
      }
    }
    return best;
  }

  structureBlocked() { return false; }

  killBot(b) {
    if (!b.alive) return;
    this.player.kills++;
    this.hud.kills++;
    this.eliminateBot(b, this.player);
    this.emit('kill', b.name);
  }

  /** Remove a bot from play. `killer` may be null (storm) or a player/bot. */
  eliminateBot(victim, killer) {
    if (!victim.alive) return;
    victim.alive = false;
    this.aliveCount = Math.max(0, this.aliveCount - 1);
    this.placements = Math.max(1, this.aliveCount);
    this.spawnParticles(victim.x, victim.z, '#ffd166', 16);
    this.loot.push({ x: victim.x, z: victim.z, type: 'shield', amount: 1, bob: 0 });
    if (victim.weapon) this.loot.push({ x: victim.x + 12, z: victim.z, type: victim.weapon, amount: 1, bob: 0 });
    if (killer === this.player) {
      this.player.kills++;
      this.hud.kills++;
    }
    const botsLeft = this.bots.reduce((n, b) => n + (b.alive ? 1 : 0), 0);
    this.aliveCount = botsLeft + (this.player.alive ? 1 : 0);
    if (this.aliveCount <= 1 && this.player.alive) this.finish(true);
  }

  damageBot(victim, dmg, from) {
    if (!victim.alive) return;
    let rest = dmg;
    if (victim.shield > 0) {
      const absorbed = Math.min(victim.shield, rest);
      victim.shield -= absorbed;
      rest -= absorbed;
    }
    victim.hp -= rest;
    this.damageNumbers.push({ x: victim.x, z: victim.z, y: 70, v: dmg, t: this.time });
    if (victim.hp <= 0) this.eliminateBot(victim, from);
  }

  finish(win) {
    if (this.over) return;
    this.over = true;
    this.running = false;
    cancelAnimationFrame(this.raf);
    this.emit('end', {
      win,
      place: win ? 1 : this.placements,
      kills: this.player.kills,
      damage: Math.round(this.hud.damage),
      accuracy: this.hud.shots ? Math.round((this.hud.hits / this.hud.shots) * 100) : 0,
    });
  }

  /* ---------------------------------------------------------------- builds */

  tryBuild() {
    const costs = [10, 10, 10, 10];
    const kinds = ['wall', 'wall', 'ramp', 'floor'];
    const kind = kinds[this.buildIndex];
    const cost = costs[this.buildIndex];
    const p = this.player;
    if (this.materials.wood < cost) { this.emit('toast', 'لا يوجد خشب كافٍ'); return; }
    this.materials.wood -= cost;
    const dist = 70;
    const x = p.x + Math.cos(p.yaw) * dist;
    const z = p.z + Math.sin(p.yaw) * dist;
    this.structures.push({
      kind, x, z, yaw: p.yaw, hp: kind === 'wall' ? 150 : 90,
      h: kind === 'ramp' ? 55 : 70, born: this.time,
    });
    if (this.structures.length > 120) this.structures.shift();
    this.emit('build', kind);
  }

  /* ------------------------------------------------------------- AI update */

  updateBots(dt, ms) {
    const storm = this.storm;
    // endgame: last few survivors hunt each other instead of camping
    const endgame = this.aliveCount <= 4 || storm.r < 340;
    for (const b of this.bots) {
      if (!b.alive) continue;
      b.ai.thinkT -= ms;
      const distToCenter = Math.hypot(b.x - storm.cx, b.z - storm.cz);
      // stay inside the storm
      if (distToCenter > storm.r - 120) {
        const a = Math.atan2(storm.cz - b.z, storm.cx - b.x);
        b.x += Math.cos(a) * 95 * dt;
        b.z += Math.sin(a) * 95 * dt;
        b.yaw = a;
        continue;
      }
      if (b.ai.thinkT <= 0) {
        b.ai.thinkT = 260 + Math.random() * 340;
        // find nearest visible target
        let best = null;
        let bestD = 900;
        const targets = this.bots.filter((o) => o !== b && o.alive);
        targets.push(this.player);
        for (const o of targets) {
          if (o === this.player && !this.player.alive) continue;
          const d = Math.hypot(o.x - b.x, o.z - b.z);
          if (d < bestD) { bestD = d; best = o; }
        }
        b.ai.target = best && (endgame || Math.random() < 0.75) ? best : null;
        if (Math.random() < 0.25) b.ai.wander = Math.random() * TAU;
      }
      const t = b.ai.target;
      if (t && t.alive) {
        const d = Math.hypot(t.x - b.x, t.z - b.z);
        const aim = Math.atan2(t.z - b.z, t.x - b.x) + (Math.random() - 0.5) * b.ai.aimErr;
        b.yaw = aim;
        if (d > 260 || endgame) {
          b.x += Math.cos(aim) * 78 * dt * b.ai.skill;
          b.z += Math.sin(aim) * 78 * dt * b.ai.skill;
        } else {
          // strafe
          const side = aim + Math.PI / 2 * b.ai.strafe;
          b.x += Math.cos(side) * 55 * dt;
          b.z += Math.sin(side) * 55 * dt;
          if (Math.random() < 0.006) b.ai.strafe *= -1;
        }
        // bot shooting
        if (this.time >= b.nextShot && (d < 820 || endgame)) {
          const w = WEAPONS[b.weapon || 'pistol'] || WEAPONS.pistol;
          b.nextShot = this.time + w.rate * (2.6 - b.ai.skill) + 260;
          const hitChance = Math.max(0.06, Math.min(0.62, 0.62 - d / 1600)) * b.ai.skill;
          if (Math.random() < hitChance) {
            const base = (w.dmg * (w.pellets ? 2.1 : 1)) * (0.75 + Math.random() * 0.4);
            if (t === this.player) {
              this.damagePlayer(Math.round(base), b);
            } else {
              // bots trade much softer with each other so a lobby lasts minutes
              this.damageBot(t, Math.round(base * (endgame ? 0.95 : 0.11)), b);
            }
          } else {
            this.spawnParticles(t.x + (Math.random() - 0.5) * 40, t.z + (Math.random() - 0.5) * 40, '#9aa4b2', 2);
          }
        }
      } else {
        b.x += Math.cos(b.ai.wander) * 55 * dt;
        b.z += Math.sin(b.ai.wander) * 55 * dt;
        b.yaw = b.ai.wander;
        if (Math.random() < 0.004) b.ai.wander = Math.random() * TAU;
      }
      // clamp
      const lim = MAP / 2 - 20;
      b.x = Math.max(-lim, Math.min(lim, b.x));
      b.z = Math.max(-lim, Math.min(lim, b.z));
      // storm damage
      if (Math.hypot(b.x - storm.cx, b.z - storm.cz) > storm.r) {
        b.hp -= 14 * dt * 4;
        if (b.hp <= 0) this.eliminateBot(b, null);
      }
    }
  }

  damagePlayer(dmg, from) {
    const p = this.player;
    if (!p.alive) return;
    // short spawn grace so a bad drop isn't an instant loss
    if (this.time < 2500) return;
    // fairness: rolling 1-second damage budget, so a converging lobby cannot
    // delete you between two frames (skill still decides the duel)
    if (!this.dmgWindow) this.dmgWindow = [];
    const now = this.time;
    while (this.dmgWindow.length && now - this.dmgWindow[0].t > 1000) this.dmgWindow.shift();
    const taken = this.dmgWindow.reduce((n, e) => n + e.d, 0);
    const allowance = Math.max(0, DAMAGE_BUDGET_PER_SEC - taken);
    if (allowance <= 0) return;
    const capped = Math.min(dmg, allowance);
    this.dmgWindow.push({ t: now, d: capped });
    let rest = capped;
    if (p.shield > 0) {
      const absorbed = Math.min(p.shield, rest);
      p.shield -= absorbed;
      rest -= absorbed;
    }
    p.hp -= rest;
    this.damageNumbers.push({ x: p.x, z: p.z, y: 80, v: dmg, t: this.time, self: true });
    this.hurtFlash = this.time;
    this.emit('hurt', { dmg, from: from.name });
    if (p.hp <= 0) {
      p.alive = false;
      this.finish(false);
    }
  }

  interact() {
    const p = this.player;
    let best = null;
    let bestD = 90;
    for (const it of this.loot) {
      const d = Math.hypot(it.x - p.x, it.z - p.z);
      if (d < bestD) { bestD = d; best = it; }
    }
    if (!best) return;
    this.pickup(best);
  }

  pickup(it) {
    const p = this.player;
    const idx = this.loot.indexOf(it);
    if (idx >= 0) this.loot.splice(idx, 1);
    if (WEAPONS[it.type]) {
      if (!p.inventory) p.inventory = [];
      if (!p.inventory.includes(it.type)) p.inventory.push(it.type);
      if (!p.weapon) {
        p.weapon = it.type;
        p.mag = WEAPONS[it.type].mag;
      }
      p.ammo += 24;
      this.emit('toast', 'التقطت ' + WEAPONS[it.type].name);
    } else if (it.type === 'shield') {
      p.shield = Math.min(100, p.shield + 50);
      this.emit('toast', '+50 درع');
    } else if (it.type === 'medkit') {
      p.hp = Math.min(100, p.hp + 40);
      this.emit('toast', '+40 صحة');
    } else if (['wood', 'stone', 'metal'].includes(it.type)) {
      this.materials[it.type] += 30 * it.amount;
      this.emit('toast', `+${30 * it.amount} ${it.type === 'wood' ? 'خشب' : it.type === 'stone' ? 'حجر' : 'معدن'}`);
    }
  }

  /* ---------------------------------------------------------------- update */

  update(dt, ms) {
    if (!this.running) return;
    this.time += ms;
    resetAcc();
    addAcc(ms);
    const p = this.player;

    // aim
    const cx = this.W / 2;
    const cy = this.H / 2;
    const dx = this.mouse.sx - cx;
    const dy = this.mouse.sy - cy;
    p.yaw = Math.atan2(dy, dx);

    // move
    let mx = 0;
    let mz = 0;
    if (this.keys.has('KeyW') || this.keys.has('ArrowUp')) mx += 1;
    if (this.keys.has('KeyS') || this.keys.has('ArrowDown')) mx -= 1;
    if (this.keys.has('KeyA') || this.keys.has('ArrowLeft')) mz -= 1;
    if (this.keys.has('KeyD') || this.keys.has('ArrowRight')) mz += 1;
    const len = Math.hypot(mx, mz) || 1;
    const speed = (this.keys.has('ShiftLeft') ? 315 : 235) * dt;
    // camera-relative: W is up the screen (negative z)
    p.x += (mx / len) * speed;
    p.z += (mz / len) * speed;
    const lim = MAP / 2 - 12;
    p.x = Math.max(-lim, Math.min(lim, p.x));
    p.z = Math.max(-lim, Math.min(lim, p.z));

    if (this.mouse.down && !this.buildMode) this.shoot();
    if (p.reloadingUntil && this.time >= p.reloadingUntil && p.reloadingUntil > 0) {
      const w = WEAPONS[p.weapon];
      const need = w.mag - p.mag;
      const take = Math.min(need, p.ammo);
      p.mag += take;
      p.ammo -= take;
      p.reloadingUntil = -1;
    }

    // storm phases
    const s = this.storm;
    s.next -= dt;
    if (s.next <= 0 && s.r > 90) {
      s.phase++;
      s.r = Math.max(90, s.r * 0.68);
      const nr = Math.max(90, s.r * 0.6);
      const off = (s.r - nr) * Math.random();
      const a = Math.random() * TAU;
      s.tx = Math.cos(a) * off;
      s.tz = Math.sin(a) * off;
      s.next = 28;
      this.emit('zone', { phase: s.phase, r: s.r });
    }
    // storm damage on player
    if (Math.hypot(p.x - s.cx, p.z - s.cz) > s.r && p.alive) {
      p.hp -= 12 * dt * 2;
      if (p.hp <= 0) { p.alive = false; this.finish(false); }
    }

    if (this.time > MAX_MATCH_MS && p.alive) { this.finish(false); return; }

    this.updateBots(dt, ms);
    // keep placement estimate fresh
    const aliveBots = this.bots.reduce((n, b) => n + (b.alive ? 1 : 0), 0);
    this.aliveCount = aliveBots + (p.alive ? 1 : 0);
    this.placements = Math.max(1, this.aliveCount);
    if (aliveBots === 0 && p.alive) this.finish(true);

    // particles
    for (let i = this.particles.length - 1; i >= 0; i--) {
      const pt = this.particles[i];
      pt.x += pt.vx * dt;
      pt.z += pt.vz * dt;
      pt.life -= dt;
      if (pt.life <= 0) this.particles.splice(i, 1);
    }
    for (let i = this.damageNumbers.length - 1; i >= 0; i--) {
      const dn = this.damageNumbers[i];
      dn.y += 40 * dt;
      if (this.time - dn.t > 900) this.damageNumbers.splice(i, 1);
    }

    // pickup prompt
    let near = null;
    let nd = 90;
    for (const it of this.loot) {
      const d = Math.hypot(it.x - p.x, it.z - p.z);
      if (d < nd) { nd = d; near = it; }
    }
    if (near !== this.pickupTarget) {
      this.pickupTarget = near;
      this.onPickupPrompt(near);
    }
    if (near) this.emit('prompt', { type: near.type, amount: near.amount, dist: nd });
    else this.emit('prompt', null);
  }

  spawnParticles(x, z, color, n) {
    for (let i = 0; i < n; i++) {
      const a = Math.random() * TAU;
      const sp = 30 + Math.random() * 120;
      this.particles.push({
        x, z, y: 45 + Math.random() * 25,
        vx: Math.cos(a) * sp, vz: Math.sin(a) * sp,
        life: 0.4 + Math.random() * 0.4, maxLife: 0.8, color,
      });
    }
  }

  /* ------------------------------------------------------------------ draw */

  loop(now) {
    if (!this.running) return;
    const ms = Math.min(50, now - this.last);
    this.last = now;
    this.update(ms / 1000, ms);
    this.draw(now);
    if (this.running) this.raf = requestAnimationFrame(this.loop);
  }

  project(x, y, z) {
    const cam = this.camera;
    const rx = x - cam.x;
    const rz = z - cam.z;
    const cy = Math.cos(-cam.yaw);
    const sy = Math.sin(-cam.yaw);
    let rxr = rx * cy - rz * sy;
    let rzr = rx * sy + rz * cy;
    if (rzr < 12) rzr = 12;
    const s = this.focal / rzr;
    return {
      x: this.W / 2 + rxr * s,
      y: this.H * HORIZON - (y - cam.y) * s,
      s, depth: rzr,
    };
  }

  draw(now) {
    const ctx = this.ctx;
    const p = this.player;
    const cam = (this.camera = {
      x: p.x, y: 190, z: p.z, yaw: p.yaw,
    });
    // sky gradient
    const sky = ctx.createLinearGradient(0, 0, 0, this.H);
    sky.addColorStop(0, '#0a1030');
    sky.addColorStop(0.45, '#243a72');
    sky.addColorStop(0.62, '#4a6fa5');
    sky.addColorStop(1, '#12203f');
    ctx.fillStyle = sky;
    ctx.fillRect(0, 0, this.W, this.H);

    // sun glow
    const sunX = this.W * 0.5 - Math.sin(p.yaw) * this.W * 0.3;
    const g = ctx.createRadialGradient(sunX, this.H * 0.28, 10, sunX, this.H * 0.28, this.W * 0.45);
    g.addColorStop(0, 'rgba(255,214,140,0.35)');
    g.addColorStop(1, 'rgba(255,214,140,0)');
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, this.W, this.H);

    this.drawGround();
    this.drawFog();
    this.drawStorm();

    // build draw list
    const items = [];
    for (const b of this.bots) {
      if (!b.alive) continue;
      const d = Math.hypot(b.x - p.x, b.z - p.z);
      if (d > 1600) continue;
      items.push({ kind: 'bot', ent: b, z: b.z });
    }
    for (const it of this.loot) {
      const d = Math.hypot(it.x - p.x, it.z - p.z);
      if (d > 900) continue;
      items.push({ kind: 'loot', ent: it, z: it.z });
    }
    for (const st of this.structures) {
      items.push({ kind: 'build', ent: st, z: st.z });
    }
    // painter's algorithm: far first (small z relative to camera)
    for (const it of items) {
      const e = it.ent;
      it.dist = Math.hypot(e.x - cam.x, e.z - cam.z);
    }
    items.sort((a, b) => b.dist - a.dist);

    for (const item of items) {
      if (item.kind === 'bot') this.drawBot(item.ent, now);
      else if (item.kind === 'loot') this.drawLootItem(item.ent, now);
      else this.drawStructure(item.ent);
    }

    this.drawDamageNumbers();
    this.drawParticles();

    this.drawViewmodel(now);
    // crosshair
    this.drawCrosshair();
    // storm vignette
    const inStorm = Math.hypot(p.x - this.storm.cx, p.z - this.storm.cz) > this.storm.r;
    if (inStorm) {
      const vg = ctx.createRadialGradient(this.W / 2, this.H / 2, this.H * 0.2, this.W / 2, this.H / 2, this.W * 0.7);
      vg.addColorStop(0, 'rgba(120,20,180,0)');
      vg.addColorStop(1, 'rgba(140,30,220,0.45)');
      ctx.fillStyle = vg;
      ctx.fillRect(0, 0, this.W, this.H);
    }
    if (this.hurtFlash && this.time - this.hurtFlash < 260) {
      ctx.fillStyle = 'rgba(255,40,80,0.16)';
      ctx.fillRect(0, 0, this.W, this.H);
    }
    this.drawMinimap();
  }

  drawGround() {
    const ctx = this.ctx;
    const cam = this.camera;
    const horizonY = this.H * HORIZON;
    const gg = ctx.createLinearGradient(0, horizonY, 0, this.H);
    gg.addColorStop(0, '#1d3326');
    gg.addColorStop(0.35, '#24422c');
    gg.addColorStop(1, '#2b4d31');
    ctx.fillStyle = gg;
    ctx.fillRect(0, horizonY, this.W, this.H - horizonY);

    // ground grid (world-aligned, near-plane clipped)
    const step = 150;
    ctx.lineWidth = 1;
    ctx.strokeStyle = 'rgba(120,220,160,0.10)';
    const startX = Math.floor((cam.x - 1800) / step) * step;
    const startZ = Math.floor((cam.z - 1800) / step) * step;
    for (let wx = startX; wx < cam.x + 1800; wx += step) {
      this.worldLine(wx, cam.z - 1800, wx, cam.z + 1800);
    }
    for (let wz = startZ; wz < cam.z + 1800; wz += step) {
      this.worldLine(cam.x - 1800, wz, cam.x + 1800, wz);
    }

    // landmarks: pillars and trees placed on a fixed hash grid
    for (let gx = -MAP / 2; gx < MAP / 2; gx += 300) {
      for (let gz = -MAP / 2; gz < MAP / 2; gz += 300) {
        const h = hash2(gx, gz);
        const d = Math.hypot(gx - cam.x, gz - cam.z);
        if (d > 1500) continue;
        if (h < 0.55) continue;
        const t = h > 0.85 ? 'pillar' : 'tree';
        this.drawLandmark(gx + h * 120, gz + (1 - h) * 120, t, h);
      }
    }
  }

  /** Distance haze so the map edge fades instead of ending in a hard line. */
  drawFog() {
    const ctx = this.ctx;
    const y0 = this.H * (HORIZON - 0.02);
    const fog = ctx.createLinearGradient(0, y0, 0, y0 + this.H * 0.24);
    fog.addColorStop(0, 'rgba(74,111,165,0.55)');
    fog.addColorStop(1, 'rgba(74,111,165,0)');
    ctx.fillStyle = fog;
    ctx.fillRect(0, y0, this.W, this.H * 0.24);
  }

  /** First-person viewmodel: the equipped weapon / pickaxe, bottom-right. */
  drawViewmodel(now) {
    const ctx = this.ctx;
    const p = this.player;
    const bob = Math.sin(this.time * 0.004) * (this.keys.has('KeyW') || this.keys.has('KeyS') ? 7 : 3);
    const kick = Math.max(0, (p.nextShot - this.time) / 260);
    const x = this.W * 0.80;
    const y = this.H + bob - kick * 14;
    const s = Math.min(this.W, this.H) / 720 * 1.15;

    ctx.save();
    ctx.translate(x, y);
    ctx.scale(s, s);
    ctx.rotate(-0.22 - kick * 0.16);

    if (this.buildMode) {
      const cos = BY_ID.get(this.equipped?.pickaxe) || BY_ID.get(DEFAULT_EQUIPPED.pickaxe);
      drawOutfitHand(ctx);
      if (cos) this.drawPickHeld(ctx, cos);
    } else if (p.weapon) {
      const w = WEAPONS[p.weapon];
      // arms + weapon silhouette
      ctx.fillStyle = '#2a3550';
      roundRectPath(ctx, -22, -6, 26, 120, 12);
      ctx.fill();
      ctx.fillStyle = '#39476b';
      roundRectPath(ctx, -14, -40, 20, 90, 8);
      ctx.fill();
      ctx.fillStyle = w.color;
      roundRectPath(ctx, -10, -104, 12, 90, 5);
      ctx.fill();
      roundRectPath(ctx, -6, -118, 22, 20, 6);
      ctx.fill();
      // muzzle flash
      if (kick > 0.55) {
        ctx.globalAlpha = (kick - 0.55) / 0.45;
        const g = ctx.createRadialGradient(4, -122, 2, 4, -122, 46);
        g.addColorStop(0, 'rgba(255,255,255,0.95)');
        g.addColorStop(0.4, w.color);
        g.addColorStop(1, 'rgba(255,180,80,0)');
        ctx.fillStyle = g;
        ctx.beginPath();
        ctx.arc(4, -122, 46, 0, TAU);
        ctx.fill();
        ctx.globalAlpha = 1;
      }
    } else {
      // fists
      ctx.fillStyle = '#2a3550';
      roundRectPath(ctx, -18, -10, 30, 90, 14);
      ctx.fill();
    }
    ctx.restore();
  }

  drawPickHeld(ctx, cos) {
    ctx.save();
    ctx.fillStyle = '#2a3550';
    roundRectPath(ctx, -26, -20, 26, 110, 12);
    ctx.fill();
    ctx.translate(0, -30);
    ctx.scale(1.5, 1.5);
    drawPick(ctx, cos, 0);
    ctx.restore();
  }

  /** Project + clip a ground-level segment against the camera near plane. */
  worldLine(x1, z1, x2, z2) {
    const cam = this.camera;
    const toCam = (x, z) => {
      const rx = x - cam.x;
      const rz = z - cam.z;
      const cy = Math.cos(-cam.yaw);
      const sy = Math.sin(-cam.yaw);
      return { x: rx * cy - rz * sy, z: rx * sy + rz * cy };
    };
    let a = toCam(x1, z1);
    let b = toCam(x2, z2);
    const NEAR = 14;
    if (a.z < NEAR && b.z < NEAR) return;
    if (a.z < NEAR) {
      const t = (NEAR - a.z) / (b.z - a.z);
      a = { x: a.x + (b.x - a.x) * t, z: NEAR };
    } else if (b.z < NEAR) {
      const t = (NEAR - b.z) / (a.z - b.z);
      b = { x: b.x + (a.x - b.x) * t, z: NEAR };
    }
    const pa = { x: this.W / 2 + a.x * (this.focal / a.z), y: this.H * HORIZON + cam.y * (this.focal / a.z) };
    const pb = { x: this.W / 2 + b.x * (this.focal / b.z), y: this.H * HORIZON + cam.y * (this.focal / b.z) };
    const ctx = this.ctx;
    ctx.beginPath();
    ctx.moveTo(pa.x, pa.y);
    ctx.lineTo(pb.x, pb.y);
    ctx.stroke();
  }

  drawLandmark(x, z, type, seed) {
    const ctx = this.ctx;
    const base = this.project(x, 0, z);
    if (base.depth < 40) return;
    const top = this.project(x, type === 'pillar' ? 260 : 170, z);
    const w = Math.max(2, 34 * base.s);
    const fade = Math.max(0.25, 1 - base.depth / 1700);
    ctx.globalAlpha = fade;
    if (type === 'pillar') {
      const grad = ctx.createLinearGradient(base.x - w, 0, base.x + w, 0);
      grad.addColorStop(0, '#5a6478');
      grad.addColorStop(1, '#8c96ab');
      ctx.fillStyle = grad;
      ctx.fillRect(base.x - w / 2, top.y, w, base.y - top.y);
      ctx.fillStyle = '#a9b4c9';
      ctx.fillRect(base.x - w / 2, top.y, w, Math.max(2, 8 * base.s));
    } else {
      ctx.strokeStyle = '#3b2a1c';
      ctx.lineWidth = Math.max(1.5, 8 * base.s);
      ctx.beginPath();
      ctx.moveTo(base.x, base.y);
      ctx.lineTo(top.x, top.y + 60 * base.s);
      ctx.stroke();
      ctx.fillStyle = seed > 0.7 ? '#2f6b3f' : '#25553a';
      ctx.beginPath();
      ctx.ellipse(top.x, top.y + 55 * base.s, 46 * base.s, 34 * base.s, 0, 0, TAU);
      ctx.fill();
    }
    ctx.globalAlpha = 1;
  }

  drawStorm() {
    const ctx = this.ctx;
    const s = this.storm;
    ctx.save();
    // project circle points
    ctx.beginPath();
    let started = false;
    for (let i = 0; i <= 64; i++) {
      const a = (i / 64) * TAU;
      const pt = this.project(s.cx + Math.cos(a) * s.r, 0, s.cz + Math.sin(a) * s.r);
      if (pt.depth < 20) continue;
      if (!started) { ctx.moveTo(pt.x, pt.y); started = true; }
      else ctx.lineTo(pt.x, pt.y);
    }
    ctx.strokeStyle = 'rgba(190,90,255,0.75)';
    ctx.lineWidth = 3;
    ctx.shadowColor = '#b45cff';
    ctx.shadowBlur = 18;
    ctx.stroke();
    ctx.restore();
  }

  drawBot(b, now) {
    const ctx = this.ctx;
    const pt = this.project(b.x, 0, b.z);
    if (pt.depth < 30) return;
    const cos = BY_ID.get(b.outfit);
    if (!cos) return;
    // the sprite design space is 100 units tall == 100 world units, so the
    // perspective scale is the projection scale itself
    const scale = Math.max(0.10, Math.min(2.6, pt.s * 0.62));
    ctx.save();
    ctx.globalAlpha = Math.max(0.45, 1 - pt.depth / 2100);
    ctx.translate(pt.x, pt.y);
    ctx.scale(scale, scale);
    drawOutfit(ctx, cos, now);
    ctx.restore();
    // nameplate + health bar
    if (pt.depth < 900) {
      const w = Math.max(16, 46 * scale * 2);
      const y = pt.y - 60 * scale - 10;
      ctx.save();
      ctx.fillStyle = 'rgba(0,0,0,0.45)';
      ctx.fillRect(pt.x - w / 2, y, w, 4);
      ctx.fillStyle = b.hp > 40 ? '#2bd46f' : '#ff5c7a';
      ctx.fillRect(pt.x - w / 2, y, w * Math.max(0, Math.min(1, b.hp / 100)), 4);
      ctx.restore();
    }
  }

  drawLootItem(it, now) {
    const ctx = this.ctx;
    const bob = Math.sin(now * 0.003 + it.bob) * 6;
    const pt = this.project(it.x, 30 + bob, it.z);
    if (pt.depth < 20) return;
    const r = Math.max(3, 16 * pt.s);
    const colors = {
      pistol: '#c9d4ff', smg: '#8ef2c6', rifle: '#ffd08a', shotgun: '#ff9d7a',
      sniper: '#c9a4ff', shield: '#4de3ff', medkit: '#ff6b8a',
      wood: '#b78a5c', stone: '#9aa4b2', metal: '#d9e3f0',
    };
    ctx.save();
    ctx.translate(pt.x, pt.y);
    ctx.rotate(now * 0.001 + it.bob);
    ctx.fillStyle = colors[it.type] || '#fff';
    ctx.shadowColor = ctx.fillStyle;
    ctx.shadowBlur = 12 * Math.min(1.4, pt.s * 6);
    if (['wood', 'stone', 'metal'].includes(it.type)) {
      ctx.fillRect(-r / 2, -r / 2, r, r);
    } else {
      ctx.beginPath();
      ctx.moveTo(0, -r); ctx.lineTo(r * 0.7, 0); ctx.lineTo(0, r); ctx.lineTo(-r * 0.7, 0);
      ctx.closePath();
      ctx.fill();
    }
    ctx.restore();
  }

  drawStructure(st) {
    const ctx = this.ctx;
    const base = this.project(st.x, 0, st.z);
    if (base.depth < 30) return;
    const top = this.project(st.x, st.h, st.z);
    const w = Math.max(4, (st.kind === 'wall' ? 120 : 90) * base.s);
    ctx.globalAlpha = Math.max(0.3, 1 - base.depth / 1800);
    const ramp = st.kind === 'ramp';
    const grad = ctx.createLinearGradient(base.x - w, 0, base.x + w, 0);
    grad.addColorStop(0, '#8a6a45');
    grad.addColorStop(0.5, '#c99a63');
    grad.addColorStop(1, '#8a6a45');
    ctx.fillStyle = grad;
    if (ramp) {
      ctx.beginPath();
      ctx.moveTo(base.x - w / 2, base.y);
      ctx.lineTo(base.x + w / 2, base.y);
      ctx.lineTo(top.x + w / 2, top.y);
      ctx.lineTo(top.x - w / 2, top.y);
      ctx.closePath();
      ctx.fill();
    } else {
      ctx.fillRect(base.x - w / 2, top.y, w, base.y - top.y);
      ctx.strokeStyle = 'rgba(80,50,25,0.6)';
      ctx.lineWidth = 1.5;
      ctx.strokeRect(base.x - w / 2, top.y, w, base.y - top.y);
    }
    ctx.globalAlpha = 1;
  }

  drawDamageNumbers() {
    const ctx = this.ctx;
    ctx.save();
    ctx.textAlign = 'center';
    for (const dn of this.damageNumbers) {
      const pt = this.project(dn.x, dn.y, dn.z);
      if (pt.depth < 20) continue;
      const alpha = 1 - (this.time - dn.t) / 900;
      ctx.globalAlpha = Math.max(0, alpha);
      ctx.font = `700 ${Math.max(10, 26 * pt.s * 0.5)}px system-ui`;
      ctx.fillStyle = dn.self ? '#ff5c7a' : '#ffe066';
      ctx.strokeStyle = 'rgba(0,0,0,0.6)';
      ctx.lineWidth = 2;
      ctx.strokeText(dn.v, pt.x, pt.y);
      ctx.fillText(dn.v, pt.x, pt.y);
    }
    ctx.restore();
  }

  drawParticles() {
    const ctx = this.ctx;
    ctx.save();
    for (const pt of this.particles) {
      const pr = this.project(pt.x, pt.y, pt.z);
      if (pr.depth < 20) continue;
      ctx.globalAlpha = Math.max(0, pt.life / pt.maxLife);
      ctx.fillStyle = pt.color;
      const r = Math.max(1, 3 * pr.s);
      ctx.beginPath();
      ctx.arc(pr.x, pr.y, r, 0, TAU);
      ctx.fill();
    }
    ctx.restore();
  }

  drawCrosshair() {
    const ctx = this.ctx;
    const x = this.mouse.sx || this.W / 2;
    const y = this.mouse.sy || this.H / 2;
    ctx.save();
    ctx.strokeStyle = this.buildMode ? 'rgba(255,209,102,0.95)' : 'rgba(255,255,255,0.88)';
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.arc(x, y, 11, 0, TAU);
    ctx.moveTo(x - 18, y); ctx.lineTo(x - 6, y);
    ctx.moveTo(x + 6, y); ctx.lineTo(x + 18, y);
    ctx.moveTo(x, y - 18); ctx.lineTo(x, y - 6);
    ctx.moveTo(x, y + 6); ctx.lineTo(x, y + 18);
    ctx.stroke();
    if (this.buildMode) {
      ctx.fillStyle = 'rgba(255,209,102,0.9)';
      ctx.font = '700 12px system-ui';
      ctx.textAlign = 'center';
      ctx.fillText('وضع البناء', x, y - 26);
    }
    ctx.restore();
  }

  drawMinimap() {
    const ctx = this.ctx;
    const R = 78;
    const cx = this.W - R - 22;
    const cy = R + 22;
    ctx.save();
    ctx.beginPath();
    ctx.arc(cx, cy, R, 0, TAU);
    ctx.fillStyle = 'rgba(8,14,26,0.72)';
    ctx.fill();
    ctx.strokeStyle = 'rgba(120,200,255,0.35)';
    ctx.lineWidth = 2;
    ctx.stroke();
    ctx.clip();
    const scale = R / (MAP / 2);
    const s = this.storm;
    ctx.beginPath();
    ctx.arc(cx + s.cx * scale, cy + s.cz * scale, s.r * scale, 0, TAU);
    ctx.strokeStyle = 'rgba(190,90,255,0.9)';
    ctx.lineWidth = 1.6;
    ctx.stroke();
    for (const b of this.bots) {
      if (!b.alive) continue;
      ctx.fillStyle = 'rgba(255,90,110,0.85)';
      ctx.fillRect(cx + b.x * scale - 1, cy + b.z * scale - 1, 2, 2);
    }
    const p = this.player;
    ctx.fillStyle = '#4de3ff';
    ctx.beginPath();
    ctx.arc(cx + p.x * scale, cy + p.z * scale, 3.4, 0, TAU);
    ctx.fill();
    ctx.restore();
  }

  /* ------------------------------------------------------------ HUD extras */

  getHudData() {
    const p = this.player;
    const w = p.weapon ? WEAPONS[p.weapon] : null;
    return {
      hp: Math.max(0, Math.round(p.hp)),
      shield: Math.max(0, Math.round(p.shield)),
      weapon: w ? w.name : 'بلا سلاح',
      mag: p.mag || 0,
      magSize: w ? w.mag : 0,
      ammo: p.ammo || 0,
      reloading: p.reloadingUntil > this.time,
      materials: { ...this.materials },
      alive: this.aliveCount,
      kills: p.kills,
      buildMode: this.buildMode,
      buildKind: ['جدار', 'جدار', 'منحدر', 'أرضية'][this.buildIndex],
      stormPhase: this.storm.phase,
      stormNext: Math.max(0, Math.ceil(this.storm.next || 0)),
      inStorm: Math.hypot(p.x - this.storm.cx, p.z - this.storm.cz) > this.storm.r,
    };
  }
}

/** Fill a rounded rect path with the current fillStyle. */
function roundRectPath(ctx, x, y, w, h, r) {
  roundRect(ctx, x, y, w, h, r);
}

/** Simple forearm for the viewmodel. */
function drawOutfitHand(ctx) {
  ctx.fillStyle = '#1d2740';
  roundRectPath(ctx, -26, -14, 30, 104, 13);
  ctx.fill();
}

function hash2(x, z) {
  let h = Math.imul(x | 0, 374761393) ^ Math.imul(z | 0, 668265263);
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
}

export { WEAPONS };
