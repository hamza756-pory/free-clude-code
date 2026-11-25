// Headless smoke test: runs the game loop with a stub canvas so logic bugs
// (physics, AI, storm, shooting, loot, builds, win/lose) surface in CI.
// Usage: node tools/headless-test.mjs

const calls = { fill: 0, stroke: 0, fillRect: 0 };

function makeCtx() {
  const ctx = {
    canvas: null,
    setTransform() {}, save() {}, restore() {}, translate() {}, scale() {}, rotate() {},
    beginPath() {}, closePath() {}, moveTo() {}, lineTo() {}, arc() {}, arcTo() {},
    ellipse() {}, quadraticCurveTo() {}, fill() { calls.fill++; }, stroke() { calls.stroke++; },
    fillRect() { calls.fillRect++; }, strokeRect() {}, clearRect() {}, clip() {},
    fillText() {}, strokeText() {}, measureText: () => ({ width: 10 }),
    createLinearGradient: () => ({ addColorStop() {} }),
    createRadialGradient: () => ({ addColorStop() {} }),
    globalAlpha: 1, lineWidth: 1, lineCap: '', font: '', textAlign: '',
    fillStyle: '', strokeStyle: '', shadowColor: '', shadowBlur: 0,
  };
  return ctx;
}

const listeners = new Map();
globalThis.window = {
  devicePixelRatio: 1,
  addEventListener: (t, f) => listeners.set(t, f),
  removeEventListener: (t) => listeners.delete(t),
};
const canvas = {
  width: 1280, height: 720,
  getContext: () => makeCtx(),
  getBoundingClientRect: () => ({ left: 0, top: 0, width: 1280, height: 720 }),
  addEventListener() {}, removeEventListener() {},
};
globalThis.requestAnimationFrame = () => 1;
globalThis.cancelAnimationFrame = () => {};
globalThis.performance = globalThis.performance || { now: () => Date.now() };

const { Game } = await import('../public/js/game.js');

const game = new Game(canvas, { equipped: { outfit: 'outfit_neon', backbling: 'back_shield', pickaxe: 'pick_neon', wrap: 'wrap_neon' } });
let ended = null;
game.on('end', (r) => { ended = r; });
game.start();

// force a weapon so shooting paths execute
game.player.weapon = 'rifle';
game.player.mag = 20;
game.player.ammo = 200;

const dt = 1 / 60;
let frames = 0;
const start = Date.now();
const mode = process.argv.includes('--combat') ? 'combat' : 'lobby';

if (mode === 'combat') {
  // duel scenario: ring of bots at medium range, player fights back
  game.player.x = 0; game.player.z = 0;
  game.player.shield = 100;
  game.bots.forEach((b, i) => {
    const a = (i / game.bots.length) * Math.PI * 2;
    const r = 320 + (i % 6) * 80;
    b.x = Math.cos(a) * r; b.z = Math.sin(a) * r;
    b.hp = 70; b.shield = 0;
  });
}

while (!ended && frames < 60 * 240) {
  game.keys.clear();
  if (mode === 'combat') {
    if (frames % 90 < 45) game.keys.add('KeyW');
    if (frames % 120 < 40) game.keys.add('KeyD');
    game.mouse.down = frames % 3 === 0;
    // aim at the closest living bot
    const p = game.player;
    let best = null, bd = 1e9;
    for (const b of game.bots) {
      if (!b.alive) continue;
      const d = Math.hypot(b.x - p.x, b.z - p.z);
      if (d < bd) { bd = d; best = b; }
    }
    if (best) {
      const a = Math.atan2(best.z - p.z, best.x - p.x);
      game.mouse.sx = game.W / 2 + Math.cos(a) * 200;
      game.mouse.sy = game.H / 2 + Math.sin(a) * 200;
    } else {
      game.mouse.sx = game.W / 2; game.mouse.sy = game.H / 2;
    }
  } else {
    // normal drop: stand in the final circle and let the lobby fight it out
    game.player.x = game.storm.cx;
    game.player.z = game.storm.cz;
    game.player.hp = Math.min(100, game.player.hp + 0.25);
  }
  game.time += 16;
  game.update(dt, 16);
  if (frames % 10 === 0) game.draw(frames * 16.6);
  frames++;

  // exercise builds + pickups + reload a few times
  if (frames % 200 === 0) { game.buildMode = true; game.tryBuild(); game.buildMode = false; }
  if (frames % 150 === 0) {
    const it = game.loot[0];
    if (it) { game.player.x = it.x; game.player.z = it.z; game.pickup(it); }
  }
  if (frames % 500 === 0) game.reload();
}

console.log(JSON.stringify({
  mode,
  frames,
  ms: Date.now() - start,
  ended: !!ended,
  result: ended,
  kills: game.player.kills,
  hud: game.hud,
  alive: game.aliveCount,
  stormPhase: game.storm.phase,
  structures: game.structures.length,
  botsAlive: game.bots.filter((b) => b.alive).length,
  lootLeft: game.loot.length,
  drawCalls: calls,
}, null, 2));

if (!ended) { console.error('FAIL: match never resolved'); process.exit(1); }
console.log('PASS: headless match resolved');
