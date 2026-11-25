import { COSMETICS } from '../../shared/catalog.js';

const OUT_SUFFIX = { backbling: '_back', pickaxe: '_pick', wrap: '_wrap', outfit: '_full' };
const SLOTS = [
  { slot: 'outfit', label: 'الأزياء', suffix: '_full' },
  { slot: 'backbling', label: 'حقائب الظهر', suffix: '_back' },
  { slot: 'pickaxe', label: 'الفؤوس', suffix: '_pick' },
  { slot: 'wrap', label: 'أثر الحركة', suffix: '_wrap' },
];

let acc = 0;
export function resetAcc() { acc = 0; }
export function addAcc(ms) { acc += ms; }
export function getAcc() { return acc; }

/** Draw a cosmetic onto a 2d canvas context. `s` is the target height in px. */
export function drawCosmetic(ctx, cos, x, y, s) {
  if (!cos) return;
  ctx.save();
  ctx.translate(x, y);
  const u = s / 100; // 100-unit design space
  ctx.scale(u, u);
  if (cos.slot === 'outfit') drawOutfit(ctx, cos);
  else if (cos.slot === 'backbling') drawBack(ctx, cos);
  else if (cos.slot === 'pickaxe') drawPick(ctx, cos);
  else if (cos.slot === 'wrap') drawWrap(ctx, cos);
  ctx.restore();
}

/* ---------------------------------------------------------------- outfits */

const BOB = (t) => Math.sin(t * 0.004) * 2.4;

export function drawOutfit(ctx, cos, t = 0) {
  const [body, accent, trim] = cos.colors;
  const bob = BOB(t);
  // back bling sits behind the torso
  ctx.save();
  ctx.translate(0, bob);
  // shadow
  ctx.globalAlpha = 0.28;
  ctx.fillStyle = '#000';
  ctx.beginPath();
  ctx.ellipse(0, 50, 20, 5.5, 0, 0, Math.PI * 2);
  ctx.fill();
  ctx.globalAlpha = 1;

  // legs
  ctx.fillStyle = accent;
  roundRect(ctx, -13, 22, 10, 27, 3);
  roundRect(ctx, 3, 22, 10, 27, 3);
  ctx.fillStyle = trim;
  roundRect(ctx, -14, 44, 12, 6, 2);
  roundRect(ctx, 2, 44, 12, 6, 2);

  // torso
  ctx.fillStyle = body;
  roundRect(ctx, -16, -6, 32, 31, 7);
  // chest accent
  ctx.fillStyle = accent;
  roundRect(ctx, -12, 2, 24, 9, 4);
  // arms
  ctx.fillStyle = body;
  roundRect(ctx, -23, -4, 9, 26, 4);
  roundRect(ctx, 14, -4, 9, 26, 4);
  ctx.fillStyle = trim;
  roundRect(ctx, -23, 18, 9, 6, 3);
  roundRect(ctx, 14, 18, 9, 6, 3);

  // head
  ctx.fillStyle = trim;
  roundRect(ctx, -11, -25, 22, 20, 8);
  ctx.fillStyle = body;
  roundRect(ctx, -9.5, -23.5, 19, 17, 7);
  if (cos.visor) {
    ctx.shadowColor = accent;
    ctx.shadowBlur = 10;
    ctx.fillStyle = accent;
    roundRect(ctx, -8, -18, 16, 5, 2.5);
    ctx.shadowBlur = 0;
  } else {
    ctx.fillStyle = '#1b2130';
    ctx.beginPath();
    ctx.arc(-4, -15.5, 1.9, 0, Math.PI * 2);
    ctx.arc(4, -15.5, 1.9, 0, Math.PI * 2);
    ctx.fill();
  }
  drawHat(ctx, cos.hat, accent, trim);
  ctx.restore();
}

function drawHat(ctx, hat, accent, trim) {
  ctx.save();
  if (hat === 'cap') {
    ctx.fillStyle = accent;
    roundRect(ctx, -12, -29, 24, 8, 4);
    roundRect(ctx, 4, -23.5, 16, 4.5, 2);
  } else if (hat === 'helmet') {
    ctx.fillStyle = accent;
    ctx.beginPath();
    ctx.arc(0, -18, 13.5, Math.PI, 0);
    ctx.fill();
    ctx.fillStyle = trim;
    roundRect(ctx, -13.5, -20, 27, 3.5, 1.5);
  } else if (hat === 'crown') {
    ctx.fillStyle = '#ffd700';
    ctx.beginPath();
    ctx.moveTo(-11, -27);
    ctx.lineTo(-7, -36);
    ctx.lineTo(-2.5, -29);
    ctx.lineTo(0, -38);
    ctx.lineTo(2.5, -29);
    ctx.lineTo(7, -36);
    ctx.lineTo(11, -27);
    ctx.closePath();
    ctx.fill();
    ctx.fillStyle = '#fff8cc';
    ctx.beginPath();
    ctx.arc(0, -31, 2, 0, Math.PI * 2);
    ctx.fill();
  } else if (hat === 'hood') {
    ctx.fillStyle = accent;
    ctx.beginPath();
    ctx.moveTo(-14, -8);
    ctx.quadraticCurveTo(-16, -34, 0, -33);
    ctx.quadraticCurveTo(16, -34, 14, -8);
    ctx.quadraticCurveTo(10, -22, 0, -23);
    ctx.quadraticCurveTo(-10, -22, -14, -8);
    ctx.fill();
  } else if (hat === 'antenna') {
    ctx.strokeStyle = trim;
    ctx.lineWidth = 1.8;
    ctx.beginPath();
    ctx.moveTo(0, -25);
    ctx.quadraticCurveTo(6, -34, 2, -40);
    ctx.stroke();
    ctx.fillStyle = trim;
    ctx.shadowColor = trim;
    ctx.shadowBlur = 8;
    ctx.beginPath();
    ctx.arc(2, -41, 2.6, 0, Math.PI * 2);
    ctx.fill();
    ctx.shadowBlur = 0;
  }
  ctx.restore();
}

/* ------------------------------------------------------------- back bling */

export function drawBack(ctx, cos, t = 0) {
  const [c0, c1] = cos.colors;
  ctx.save();
  switch (cos.shape) {
    case 'satchel':
      ctx.fillStyle = c0;
      roundRect(ctx, -13, -6, 26, 24, 6);
      ctx.fillStyle = c1;
      roundRect(ctx, -9, -2, 18, 5, 2);
      break;
    case 'shield':
      ctx.fillStyle = c0;
      ctx.beginPath();
      ctx.moveTo(0, -12); ctx.lineTo(15, -3); ctx.lineTo(11, 16);
      ctx.lineTo(0, 22); ctx.lineTo(-11, 16); ctx.lineTo(-15, -3);
      ctx.closePath(); ctx.fill();
      ctx.strokeStyle = c1; ctx.lineWidth = 2.4; ctx.stroke();
      break;
    case 'jetpack':
      ctx.fillStyle = c0;
      roundRect(ctx, -12, -8, 24, 26, 6);
      ctx.fillStyle = c1;
      roundRect(ctx, -8, 0, 6, 16, 3);
      roundRect(ctx, 2, 0, 6, 16, 3);
      ctx.globalAlpha = 0.9;
      ctx.fillStyle = '#ffd166';
      ctx.beginPath();
      ctx.ellipse(-5, 20 + Math.sin(t * 0.01) * 2, 3.4, 6, 0, 0, Math.PI * 2);
      ctx.ellipse(5, 20 + Math.cos(t * 0.01) * 2, 3.4, 6, 0, 0, Math.PI * 2);
      ctx.fill();
      break;
    case 'drone':
      ctx.fillStyle = c0;
      roundRect(ctx, -14, -10, 28, 18, 9);
      ctx.fillStyle = c1;
      ctx.shadowColor = c1; ctx.shadowBlur = 8;
      ctx.beginPath(); ctx.arc(0, -1, 4, 0, Math.PI * 2); ctx.fill();
      ctx.shadowBlur = 0;
      ctx.fillStyle = c0;
      roundRect(ctx, -20, -14, 10, 4, 2);
      roundRect(ctx, 10, -14, 10, 4, 2);
      break;
    case 'wings':
      for (const dir of [-1, 1]) {
        ctx.fillStyle = c0;
        ctx.beginPath();
        ctx.moveTo(0, -6);
        ctx.quadraticCurveTo(dir * 30, -22, dir * 34, 8);
        ctx.quadraticCurveTo(dir * 22, 2, dir * 16, 14);
        ctx.quadraticCurveTo(dir * 10, 2, 0, 12);
        ctx.closePath();
        ctx.fill();
        ctx.strokeStyle = c1; ctx.lineWidth = 1.6; ctx.stroke();
      }
      break;
    default:
      break;
  }
  ctx.restore();
}

/* --------------------------------------------------------------- pickaxes */

export function drawPick(ctx, cos, t = 0) {
  const [c0, c1] = cos.colors;
  ctx.save();
  ctx.rotate(Math.sin(t * 0.003) * 0.18 - 0.2);
  // handle
  ctx.fillStyle = c1;
  roundRect(ctx, -3, -6, 6, 44, 3);
  switch (cos.shape) {
    case 'blade':
      ctx.fillStyle = c0;
      ctx.shadowColor = c0; ctx.shadowBlur = 12;
      roundRect(ctx, -3, -20, 6, 18, 3);
      ctx.beginPath();
      ctx.moveTo(0, -30); ctx.lineTo(9, -14); ctx.lineTo(0, -18); ctx.lineTo(-9, -14);
      ctx.closePath(); ctx.fill();
      ctx.shadowBlur = 0;
      break;
    case 'cleaver':
      ctx.fillStyle = c0;
      ctx.beginPath();
      ctx.moveTo(-2, -22); ctx.lineTo(16, -16); ctx.lineTo(14, 2); ctx.lineTo(-2, -4);
      ctx.closePath(); ctx.fill();
      ctx.strokeStyle = '#fff'; ctx.lineWidth = 1.2; ctx.stroke();
      break;
    case 'scythe':
      ctx.fillStyle = c0;
      ctx.beginPath();
      ctx.moveTo(0, -20);
      ctx.quadraticCurveTo(26, -28, 24, -2);
      ctx.quadraticCurveTo(14, -16, 0, -14);
      ctx.closePath(); ctx.fill();
      ctx.shadowColor = c0; ctx.shadowBlur = 10;
      ctx.strokeStyle = c0; ctx.stroke();
      ctx.shadowBlur = 0;
      break;
    case 'hammer':
      ctx.fillStyle = c0;
      roundRect(ctx, -13, -24, 26, 14, 4);
      ctx.fillStyle = c1;
      roundRect(ctx, -9, -20, 18, 4, 2);
      break;
    default: // axe
      ctx.fillStyle = c0;
      ctx.beginPath();
      ctx.moveTo(-2, -22); ctx.lineTo(14, -18); ctx.lineTo(12, -2); ctx.lineTo(-2, -8);
      ctx.closePath(); ctx.fill();
      ctx.beginPath();
      ctx.moveTo(2, -22); ctx.lineTo(-12, -17); ctx.lineTo(-10, -4); ctx.lineTo(2, -9);
      ctx.closePath(); ctx.fill();
      break;
  }
  ctx.restore();
}

/* ------------------------------------------------------------ move wraps */

export function drawWrap(ctx, cos) {
  const [c0] = cos.colors;
  ctx.save();
  ctx.strokeStyle = c0;
  ctx.shadowColor = c0;
  ctx.shadowBlur = 12;
  ctx.lineWidth = 3;
  ctx.lineCap = 'round';
  for (let i = 0; i < 3; i++) {
    ctx.globalAlpha = 0.8 - i * 0.22;
    ctx.beginPath();
    ctx.moveTo(-18 + i * 4, 22 + i * 6);
    ctx.quadraticCurveTo(0, 6 + i * 6, 18 - i * 4, 22 + i * 6);
    ctx.stroke();
  }
  ctx.restore();
}

/* ---------------------------------------------------------------- helpers */

export function roundRect(ctx, x, y, w, h, r) {
  const rr = Math.min(r, w / 2, h / 2);
  ctx.beginPath();
  ctx.moveTo(x + rr, y);
  ctx.arcTo(x + w, y, x + w, y + h, rr);
  ctx.arcTo(x + w, y + h, x, y + h, rr);
  ctx.arcTo(x, y + h, x, y, rr);
  ctx.arcTo(x, y, x + w, y, rr);
  ctx.closePath();
  ctx.fill();
}

/** Full roster grouped by slot, rendered into the locker grid. */
export function rosterBySlot() {
  return SLOTS.map((s) => ({ ...s, items: COSMETICS.filter((c) => c.slot === s.slot) }));
}

export { SLOTS, OUT_SUFFIX };
