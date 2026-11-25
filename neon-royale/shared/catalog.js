// Shared catalog: imported by the Node server (prices/validation) and by the
// browser (rendering + shop UI). All content is original to this project.

export const RARITY = {
  common: { name: 'عادي', color: '#9aa4b2', mul: 1.0 },
  rare: { name: 'نادر', color: '#3aa0ff', mul: 1.12 },
  epic: { name: 'ملحمي', color: '#b45cff', mul: 1.26 },
  legendary: { name: 'أسطوري', color: '#ff9f1a', mul: 1.45 },
};

export const SLOT = {
  outfit: 'زي',
  backbling: 'حقيبة ظهر',
  pickaxe: 'فأس',
  wrap: 'أثر الحركة',
};

/**
 * Cosmetic descriptor consumed by the renderer:
 *  colors[0] = body, colors[1] = accent, colors[2] = trim
 *  hat: none | cap | helmet | crown | hood | antenna
 *  visor: true -> glowing eye band
 */
export const COSMETICS = [
  // ---------- Outfits ----------
  { id: 'outfit_recruit', name: 'مجنّد المدينة', en: 'City Recruit', slot: 'outfit', rarity: 'common', price: 0,
    colors: ['#3d7ea6', '#f2c14e', '#23384a'], hat: 'cap', visor: false },
  { id: 'outfit_neon', name: 'عدّاء النيون', en: 'Neon Runner', slot: 'outfit', rarity: 'rare', price: 800,
    colors: ['#13e8c8', '#0b2b45', '#7cff3a'], hat: 'antenna', visor: true },
  { id: 'outfit_desert', name: 'صقر الصحراء', en: 'Desert Falcon', slot: 'outfit', rarity: 'rare', price: 800,
    colors: ['#d9a45b', '#6b4a2b', '#f5e2c1'], hat: 'hood', visor: false },
  { id: 'outfit_sakura', name: 'عاصفة الساكورا', en: 'Sakura Storm', slot: 'outfit', rarity: 'rare', price: 900,
    colors: ['#ff9ec4', '#6d2b4b', '#fff0f6'], hat: 'hood', visor: false },
  { id: 'outfit_frost', name: 'بايت الصقيع', en: 'Frostbyte', slot: 'outfit', rarity: 'epic', price: 1200,
    colors: ['#a9e7ff', '#2f6fa8', '#ffffff'], hat: 'helmet', visor: true },
  { id: 'outfit_ember', name: 'حارس الجمر', en: 'Ember Warden', slot: 'outfit', rarity: 'epic', price: 1200,
    colors: ['#ff6b35', '#4a1d0f', '#ffd166'], hat: 'helmet', visor: true },
  { id: 'outfit_cyber', name: 'الصقر السيبراني', en: 'Cyber Falcon', slot: 'outfit', rarity: 'epic', price: 1300,
    colors: ['#2b2f52', '#ff2e88', '#4de3ff'], hat: 'helmet', visor: true },
  { id: 'outfit_void', name: 'سالك العدم', en: 'Voidwalker', slot: 'outfit', rarity: 'legendary', price: 1800,
    colors: ['#231b3a', '#8b5cff', '#c9aaff'], hat: 'hood', visor: true },
  { id: 'outfit_shadow', name: 'عميل الظل', en: 'Shadow Agent', slot: 'outfit', rarity: 'legendary', price: 1600,
    colors: ['#14161f', '#c0c8ff', '#3b3f52'], hat: 'cap', visor: true },
  { id: 'outfit_gold', name: 'الأسطورة الذهبية', en: 'Golden Legend', slot: 'outfit', rarity: 'legendary', price: 2000,
    colors: ['#ffd700', '#8a6300', '#fff8cc'], hat: 'crown', visor: false },

  // ---------- Back blings ----------
  { id: 'back_none', name: 'بدون', en: 'None', slot: 'backbling', rarity: 'common', price: 0,
    colors: ['#000000', '#000000'], shape: 'none' },
  { id: 'back_satchel', name: 'حقيبة المؤونة', en: 'Ration Satchel', slot: 'backbling', rarity: 'common', price: 400,
    colors: ['#7a5c3e', '#d9c9a8'], shape: 'satchel' },
  { id: 'back_shield', name: 'حاجز الطاقة', en: 'Power Barrier', slot: 'backbling', rarity: 'rare', price: 700,
    colors: ['#2f6fa8', '#7ce7ff'], shape: 'shield' },
  { id: 'back_jetpack', name: 'حزمة الدفع', en: 'Boost Pack', slot: 'backbling', rarity: 'epic', price: 1000,
    colors: ['#4a1d0f', '#ff8a3d'], shape: 'jetpack' },
  { id: 'back_drone', name: 'طائرة المراقبة', en: 'Scout Drone', slot: 'backbling', rarity: 'epic', price: 1100,
    colors: ['#2b2f52', '#4de3ff'], shape: 'drone' },
  { id: 'back_wings', name: 'أجنحة العدم', en: 'Void Wings', slot: 'backbling', rarity: 'legendary', price: 1500,
    colors: ['#3a2a6b', '#b98cff'], shape: 'wings' },

  // ---------- Pickaxes ----------
  { id: 'pick_default', name: 'فأس المجنّد', en: 'Recruit Axe', slot: 'pickaxe', rarity: 'common', price: 0,
    colors: ['#9aa4b2', '#6b4a2b'], shape: 'axe' },
  { id: 'pick_neon', name: 'حدّ النيون', en: 'Neon Edge', slot: 'pickaxe', rarity: 'rare', price: 600,
    colors: ['#13e8c8', '#0b2b45'], shape: 'blade' },
  { id: 'pick_frost', name: 'مِبرد الصقيع', en: 'Frost Cleaver', slot: 'pickaxe', rarity: 'epic', price: 900,
    colors: ['#a9e7ff', '#2f6fa8'], shape: 'cleaver' },
  { id: 'pick_void', name: 'منجل العدم', en: 'Void Scythe', slot: 'pickaxe', rarity: 'legendary', price: 1400,
    colors: ['#8b5cff', '#231b3a'], shape: 'scythe' },
  { id: 'pick_gold', name: 'الفأس الذهبي', en: 'Golden Axe', slot: 'pickaxe', rarity: 'legendary', price: 1500,
    colors: ['#ffd700', '#8a6300'], shape: 'hammer' },

  // ---------- Movement wraps ----------
  { id: 'wrap_none', name: 'بدون', en: 'None', slot: 'wrap', rarity: 'common', price: 0, colors: ['#ffffff'] },
  { id: 'wrap_neon', name: 'أثر نيون', en: 'Neon Trail', slot: 'wrap', rarity: 'rare', price: 500, colors: ['#13e8c8'] },
  { id: 'wrap_ember', name: 'أثر جمر', en: 'Ember Trail', slot: 'wrap', rarity: 'epic', price: 800, colors: ['#ff6b35'] },
  { id: 'wrap_void', name: 'أثر عدم', en: 'Void Trail', slot: 'wrap', rarity: 'legendary', price: 1200, colors: ['#b98cff'] },
];

export const BY_ID = new Map(COSMETICS.map((c) => [c.id, c]));

export const STARTER_OWNED = COSMETICS.filter((c) => c.price === 0).map((c) => c.id);

export const DEFAULT_EQUIPPED = {
  outfit: 'outfit_recruit',
  backbling: 'back_none',
  pickaxe: 'pick_default',
  wrap: 'wrap_none',
};

export const SHOP_SIZE = 6;

/** Deterministic 32-bit PRNG so everyone sees the same shop for a given day. */
export function mulberry32(seed) {
  let a = seed >>> 0;
  return function rand() {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export function dayKey(d = new Date()) {
  return d.toISOString().slice(0, 10); // YYYY-MM-DD (UTC)
}

function seedFrom(str) {
  let h = 2166136261;
  for (let i = 0; i < str.length; i++) {
    h ^= str.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

/** Rotating shop: 6 non-starter items, deterministic per UTC day. */
export function dailyShop(d = new Date()) {
  const key = dayKey(d);
  const rand = mulberry32(seedFrom('neon-royale:' + key));
  const pool = COSMETICS.filter((c) => c.price > 0).slice();
  for (let i = pool.length - 1; i > 0; i--) {
    const j = Math.floor(rand() * (i + 1));
    [pool[i], pool[j]] = [pool[j], pool[i]];
  }
  const picks = pool.slice(0, SHOP_SIZE);
  const nextMidnight = Date.UTC(
    d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate() + 1, 0, 0, 0
  );
  return {
    day: key,
    rotatesAt: nextMidnight,
    // small daily discount for flavour: first item 20% off
    items: picks.map((c, i) => ({
      id: c.id,
      price: i === 0 ? Math.round(c.price * 0.8) : c.price,
      discount: i === 0 ? 20 : 0,
    })),
  };
}
