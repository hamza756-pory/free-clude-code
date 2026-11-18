/* Hamza Link — تطبيق الهاتف — تصميم Hamza Alfarai */
"use strict";

const S = {
  token: localStorage.getItem("hl_token") || "",
  name: localStorage.getItem("hl_name") || "",
  es: null,
  perms: {},
  ai: null,
  os: "",
  tab: "control",
  unread: 0,
  inbox: JSON.parse(localStorage.getItem("hl_inbox") || "[]"),
  audio: null,
};

$("#creditPair").innerHTML = CREDIT_HTML;
$("#creditApp").innerHTML = CREDIT_HTML;

// =============================================================================
//  الاتصال بالخادم
// =============================================================================
async function api(action, data = {}) {
  const r = await fetch("/api/phone", {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Token": S.token },
    body: JSON.stringify({ action, ...data }),
  });
  let j = {};
  try { j = await r.json(); } catch (_) { /* ignore */ }
  if (r.status === 401) { unpair(true); throw new Error("انتهى الاقتران"); }
  if (!r.ok || j.ok === false) throw new Error(j.error || `HTTP ${r.status}`);
  return j;
}

async function apiSafe(action, data, okMsg) {
  try {
    const j = await api(action, data);
    if (okMsg) toast(okMsg, "ok");
    return j;
  } catch (e) {
    toast(e.message, "err");
    return null;
  }
}

function showPair() {
  $("#appView").classList.add("hidden");
  $("#pairView").classList.remove("hidden");
  const m = location.hash.match(/pin=(\d{6})/);
  if (m) $("#pinIn").value = m[1];
  $("#nameIn").value = S.name || guessDeviceName();
}

function guessDeviceName() {
  const ua = navigator.userAgent;
  if (/iPhone/.test(ua)) return "iPhone";
  if (/iPad/.test(ua)) return "iPad";
  const m = ua.match(/Android [\d.]+; ([^;)]+)/);
  if (m) return m[1].replace(/Build.*/, "").trim().slice(0, 40);
  if (/Android/.test(ua)) return "Android";
  return "هاتفي";
}

async function pair() {
  unlockAudio();
  const pin = $("#pinIn").value.replace(/\D/g, "");
  const name = $("#nameIn").value.trim() || guessDeviceName();
  if (pin.length !== 6) return toast("أدخل رمز الاقتران المكوّن من 6 أرقام", "err");
  $("#pairBtn").disabled = true;
  try {
    const r = await fetch("/api/pair", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ pin, name }),
    });
    const j = await r.json();
    if (!r.ok || !j.ok) throw new Error(j.error || "فشل الاقتران");
    S.token = j.token; S.name = name;
    localStorage.setItem("hl_token", S.token);
    localStorage.setItem("hl_name", name);
    history.replaceState(null, "", location.pathname);
    toast(`تم الاتصال بـ ${j.pc} ✓`, "ok");
    startApp();
  } catch (e) {
    toast(e.message, "err");
  } finally {
    $("#pairBtn").disabled = false;
  }
}

function unpair(silent) {
  S.token = "";
  localStorage.removeItem("hl_token");
  if (S.es) { S.es.close(); S.es = null; }
  stopScreen();
  if (!silent) toast("تم إلغاء الاقتران");
  showPair();
}

function startApp() {
  $("#pairView").classList.add("hidden");
  $("#appView").classList.remove("hidden");
  renderInbox();
  connect();
}

function connect() {
  if (S.es) S.es.close();
  const es = new EventSource(`/api/events?role=phone&token=${encodeURIComponent(S.token)}`);
  S.es = es;
  es.addEventListener("hello", (e) => onHello(JSON.parse(e.data)));
  es.addEventListener("push", (e) => onPush(JSON.parse(e.data)));
  es.addEventListener("ai", (e) => onAi(JSON.parse(e.data)));
  es.addEventListener("perms", (e) => applyPerms(JSON.parse(e.data)));
  es.addEventListener("revoked", () => { toast("ألغى الحاسوب الاقتران", "err"); unpair(true); });
  es.onopen = () => setConn(true);
  es.onerror = async () => {
    setConn(false);
    if (es.readyState === EventSource.CLOSED) {
      try {
        await api("ping");
        setTimeout(connect, 2000);
      } catch (_) {
        if (S.token) setTimeout(connect, 4000);
      }
    }
  };
}

function setConn(on) {
  $("#connDot").className = "dot " + (on ? "on" : "off");
  $("#connBar").classList.toggle("hidden", on);
}

function onHello(d) {
  setConn(true);
  S.os = d.os || "";
  $("#pcName").textContent = `${d.pc} · ${d.os}`;
  applyPerms(d.perms);
  S.ai = d.ai;
  renderAiAll();
  $("#cwd").textContent = d.cwd || "";
  renderQuickCmds();
}

function applyPerms(p) {
  S.perms = p || {};
  const map = { input: "#permInput", screen: "#permScreen", clipboard: "#permClip", ai: "#permAi", shell: "#permShell", files: "#permFiles" };
  for (const [k, sel] of Object.entries(map)) $(sel).classList.toggle("hidden", !!S.perms[k]);
  if (!S.perms.screen) stopScreen();
}

// =============================================================================
//  التبويبات
// =============================================================================
$$("#nav button").forEach((b) => b.addEventListener("click", () => setTab(b.dataset.tab)));
function setTab(t) {
  S.tab = t;
  $$("#nav button").forEach((b) => b.classList.toggle("on", b.dataset.tab === t));
  $$(".tab").forEach((s) => s.classList.toggle("on", s.dataset.tab === t));
  if (t === "inbox") { S.unread = 0; updateBadge(); }
  if (t === "files" && !$("#fsList").childElementCount) loadDir("");
  if (t !== "control") stopScreen();
}
function updateBadge() {
  const b = $("#badge");
  b.textContent = S.unread;
  b.classList.toggle("hidden", !S.unread);
}

$$("#ctrlSeg button").forEach((b) => b.addEventListener("click", () => {
  $$("#ctrlSeg button").forEach((x) => x.classList.toggle("on", x === b));
  $$("[data-mode]").forEach((s) => s.classList.toggle("hidden", s.dataset.mode !== b.dataset.m));
  if (b.dataset.m !== "screen") stopScreen();
}));

// =============================================================================
//  لوحة اللمس (Trackpad)
// =============================================================================
const pad = $("#pad");
const ptrs = new Map();
let acc = { dx: 0, dy: 0, sy: 0 };
let inflight = false;
let gesture = null;
let dragging = false;

pad.addEventListener("pointerdown", (e) => {
  unlockAudio();
  pad.setPointerCapture(e.pointerId);
  ptrs.set(e.pointerId, { x: e.clientX, y: e.clientY });
  if (!gesture) gesture = { start: Date.now(), moved: 0, maxPtrs: 0 };
  gesture.maxPtrs = Math.max(gesture.maxPtrs, ptrs.size);
});
pad.addEventListener("pointermove", (e) => {
  const p = ptrs.get(e.pointerId);
  if (!p) return;
  const dx = e.clientX - p.x, dy = e.clientY - p.y;
  p.x = e.clientX; p.y = e.clientY;
  if (gesture) gesture.moved += Math.abs(dx) + Math.abs(dy);
  const sens = parseFloat($("#sens").value);
  if (ptrs.size === 1) {
    const speed = Math.hypot(dx, dy);
    const accel = 1 + Math.min(speed / 10, 1.6);
    acc.dx += dx * sens * accel;
    acc.dy += dy * sens * accel;
  } else if (ptrs.size === 2) {
    acc.sy += -dy / ptrs.size * 0.5;
  }
  flush();
});
function endPtr(e) {
  if (!ptrs.has(e.pointerId)) return;
  ptrs.delete(e.pointerId);
  if (ptrs.size === 0 && gesture) {
    const g = gesture; gesture = null;
    const quick = Date.now() - g.start < 280 && g.moved < 12;
    if (quick) {
      const btn = g.maxPtrs >= 2 ? "right" : "left";
      sendRaw("mouse_button", { button: btn, type: "click" });
      if (navigator.vibrate) navigator.vibrate(12);
    }
  }
}
pad.addEventListener("pointerup", endPtr);
pad.addEventListener("pointercancel", endPtr);

async function flush() {
  if (inflight) return;
  const dx = Math.trunc(acc.dx), dy = Math.trunc(acc.dy), sy = Math.trunc(acc.sy);
  if (!dx && !dy && !sy) return;
  acc.dx -= dx; acc.dy -= dy; acc.sy -= sy;
  inflight = true;
  try {
    if (dx || dy) await sendRaw("mouse_move", { dx, dy });
    if (sy) await sendRaw("scroll", { dy: sy });
  } finally {
    inflight = false;
    if (Math.abs(acc.dx) >= 1 || Math.abs(acc.dy) >= 1 || Math.abs(acc.sy) >= 1) requestAnimationFrame(flush);
  }
}

let lastErr = 0;
async function sendRaw(action, data) {
  try { await api(action, data); }
  catch (e) { if (Date.now() - lastErr > 2500) { lastErr = Date.now(); toast(e.message, "err"); } }
}

$$("[data-mb]").forEach((b) => b.addEventListener("click", () => sendRaw("mouse_button", { button: b.dataset.mb, type: "click" })));
$("#dragBtn").addEventListener("click", () => {
  dragging = !dragging;
  $("#dragBtn").classList.toggle("active", dragging);
  pad.classList.toggle("drag", dragging);
  sendRaw("mouse_button", { button: "left", type: dragging ? "down" : "up" });
});
$("#sens").value = localStorage.getItem("hl_sens") || "1.8";
$("#sens").addEventListener("change", () => localStorage.setItem("hl_sens", $("#sens").value));

// =============================================================================
//  عرض شاشة الحاسوب
// =============================================================================
let screenOn = false, screenUrl = null, frames = 0, fpsT = Date.now();
$("#screenToggle").addEventListener("click", () => (screenOn ? stopScreen() : startScreen()));
function startScreen() {
  if (!S.perms.screen) return toast("عرض الشاشة معطّل من الحاسوب", "err");
  screenOn = true;
  $("#screenToggle").textContent = "⏸️ إيقاف العرض";
  nextFrame();
}
function stopScreen() {
  screenOn = false;
  const b = $("#screenToggle");
  if (b) b.textContent = "▶️ بدء العرض";
}
async function nextFrame() {
  if (!screenOn) return;
  const [w, q] = $("#screenQ").value.split(",");
  try {
    const r = await fetch(`/api/screen?token=${encodeURIComponent(S.token)}&w=${w}&q=${q}&t=${Date.now()}`);
    if (!r.ok) {
      const j = await r.json().catch(() => ({}));
      throw new Error(j.error || `HTTP ${r.status}`);
    }
    const blob = await r.blob();
    const url = URL.createObjectURL(blob);
    $("#screenImg").src = url;
    if (screenUrl) URL.revokeObjectURL(screenUrl);
    screenUrl = url;
    frames++;
    if (Date.now() - fpsT > 1000) {
      $("#fps").textContent = `${(frames * 1000 / (Date.now() - fpsT)).toFixed(1)} fps`;
      frames = 0; fpsT = Date.now();
    }
    setTimeout(nextFrame, 40);
  } catch (e) {
    toast(e.message, "err");
    stopScreen();
  }
}
(() => {
  const img = $("#screenImg");
  let pressT = null, lastTap = 0, longFired = false;
  const frac = (e) => {
    const r = img.getBoundingClientRect();
    return { x: (e.clientX - r.left) / r.width, y: (e.clientY - r.top) / r.height };
  };
  img.addEventListener("pointerdown", (e) => {
    longFired = false;
    const f = frac(e);
    pressT = setTimeout(() => { longFired = true; sendRaw("mouse_abs", { ...f, click: "click", button: "right" }); if (navigator.vibrate) navigator.vibrate(30); }, 550);
  });
  img.addEventListener("pointerup", (e) => {
    clearTimeout(pressT);
    if (longFired) return;
    const f = frac(e);
    const dbl = Date.now() - lastTap < 300;
    lastTap = Date.now();
    sendRaw("mouse_abs", { ...f, click: dbl ? "double" : "click", button: "left" });
  });
  img.addEventListener("pointercancel", () => clearTimeout(pressT));
  img.addEventListener("contextmenu", (e) => e.preventDefault());
})();

// =============================================================================
//  لوحة المفاتيح والوسائط
// =============================================================================
const KEYS = [
  ["Esc", "esc"], ["Tab", "tab"], ["⌫", "backspace"], ["⏎ Enter", "enter"],
  ["←", "left"], ["↑", "up"], ["↓", "down"], ["→", "right"],
  ["Home", "home"], ["End", "end"], ["Del", "delete"], ["Space", "space"],
  ["Ctrl+C", "c", ["ctrl"]], ["Ctrl+V", "v", ["ctrl"]], ["Ctrl+X", "x", ["ctrl"]], ["Ctrl+Z", "z", ["ctrl"]],
  ["Ctrl+A", "a", ["ctrl"]], ["Ctrl+S", "s", ["ctrl"]], ["Alt+Tab", "tab", ["alt"]], ["Alt+F4", "f4", ["alt"]],
  ["⊞ Win", "win"], ["Win+D", "d", ["win"]], ["Win+E", "e", ["win"]], ["F5", "f5"],
];
for (const [label, key, mods] of KEYS) {
  $("#keysGrid").append(h("button", { class: "btn ltr", onclick: () => sendRaw("key", { key, mods: mods || [] }) }, label));
}
$("#typeBtn").addEventListener("click", async () => {
  const text = $("#typeIn").value;
  if (!text) return;
  const r = await apiSafe("type", { text, enter: $("#typeEnter").checked }, "تمت الكتابة على الحاسوب ✓");
  if (r) $("#typeIn").value = "";
});
$$("[data-media]").forEach((b) => b.addEventListener("click", () => sendRaw("media", { name: b.dataset.media })));
$("#lockBtn").addEventListener("click", () => { if (confirm("قفل الحاسوب الآن؟")) apiSafe("lock", {}, "تم قفل الحاسوب 🔒"); });

$("#clipGetBtn").addEventListener("click", async () => {
  const r = await apiSafe("clip_get");
  if (r) {
    $("#clipIn").value = r.text;
    const ok = await copyText(r.text);
    toast(ok ? "تم الجلب والنسخ إلى حافظة الهاتف ✓" : "تم جلب الحافظة ✓", "ok");
  }
});
$("#clipSetBtn").addEventListener("click", () => {
  const t = $("#clipIn").value;
  if (t) apiSafe("clip_set", { text: t }, "تم النسخ إلى حافظة الحاسوب ✓");
});
$("#openUrlBtn").addEventListener("click", () => {
  const t = $("#clipIn").value.trim();
  if (t) apiSafe("open_url", { url: t }, "تم فتح الرابط على الحاسوب 🌐");
});

// =============================================================================
//  Claude — جلسة مشتركة مع الحاسوب
// =============================================================================
function renderAiAll() {
  const ai = S.ai;
  if (!ai) return;
  const prov = $("#aiProvider");
  prov.innerHTML = "";
  for (const p of Object.keys(ai.models)) prov.append(h("option", { value: p }, p === "openrouter" ? "OpenRouter" : "DeepSeek"));
  prov.value = ai.provider;
  fillModels();
  $("#aiModel").value = ai.model;
  const k = ai.keys || {};
  $("#aiKeys").textContent = (k[ai.provider] ? "🔑 المفتاح جاهز" : "⚠️ لا يوجد مفتاح API لهذا المزوّد — أضفه من لوحة الحاسوب") + ` · ${ai.provider}`;
  const chat = $("#chat");
  chat.innerHTML = "";
  if (!ai.view.length) chat.append(h("div", { class: "empty" }, "ابدأ محادثة مع Claude — تظهر على الحاسوب والهاتف معاً."));
  for (const m of ai.view) chat.append(msgEl(m));
  chat.scrollTop = chat.scrollHeight;
  $("#aiSend").disabled = ai.busy;
}
function fillModels() {
  const p = $("#aiProvider").value;
  const sel = $("#aiModel");
  sel.innerHTML = "";
  for (const m of (S.ai.models[p] || [])) sel.append(h("option", { value: m.id }, m.label));
}
$("#aiProvider").addEventListener("change", () => {
  fillModels();
  const k = (S.ai && S.ai.keys) || {};
  $("#aiKeys").textContent = (k[$("#aiProvider").value] ? "🔑 المفتاح جاهز" : "⚠️ لا يوجد مفتاح API لهذا المزوّد — أضفه من لوحة الحاسوب");
});
function msgEl(m) {
  const el = h("div", { class: `msg ${m.role}${m.error ? " error" : ""}` });
  el.append(h("div", { class: "who" }, m.role === "user" ? m.from : `✳️ ${m.from}`));
  const body = h("div", { class: "body", html: renderMd(m.text) });
  el.append(body);
  return el;
}
function onAi(d) {
  if (!S.ai) return;
  const chat = $("#chat");
  if (d.type === "user") {
    $(".empty", chat)?.remove();
    S.ai.provider = d.provider; S.ai.model = d.model; S.ai.busy = true;
    const u = { role: "user", text: d.text, from: d.from };
    const a = { role: "assistant", text: "", from: d.model };
    S.ai.view.push(u, a);
    chat.append(msgEl(u));
    const ael = msgEl(a);
    $(".body", ael).classList.add("typing");
    chat.append(ael);
    $("#aiSend").disabled = true;
  } else if (d.type === "delta") {
    const last = S.ai.view[S.ai.view.length - 1];
    if (last) last.text += d.text;
    const el = chat.lastElementChild && $(".body", chat.lastElementChild);
    if (el) el.innerHTML = renderMd(last.text);
  } else if (d.type === "done" || d.type === "error") {
    S.ai.busy = false;
    $("#aiSend").disabled = false;
    const el = chat.lastElementChild && $(".body", chat.lastElementChild);
    if (el) {
      el.classList.remove("typing");
      if (d.type === "error") {
        chat.lastElementChild.classList.add("error");
        el.innerHTML += `<div class="small" style="color:#FFB0C0">[خطأ] ${esc(d.text)}</div>`;
      } else if (d.usage && (d.usage.in || d.usage.out)) {
        el.parentElement.append(h("div", { class: "who" }, `↑ ${d.usage.in} · ↓ ${d.usage.out} توكن`));
      }
    }
  } else if (d.type === "reset") {
    S.ai.view = [];
    renderAiAll();
  } else if (d.type === "state") {
    Object.assign(S.ai, d);
    renderAiAll();
  }
  chat.scrollTop = chat.scrollHeight;
}
$("#aiSend").addEventListener("click", sendAi);
$("#aiIn").addEventListener("keydown", (e) => { if (e.key === "Enter" && !e.shiftKey && !e.isComposing) { e.preventDefault(); sendAi(); } });
async function sendAi() {
  const prompt = $("#aiIn").value.trim();
  if (!prompt) return;
  const r = await apiSafe("ai_send", { prompt, provider: $("#aiProvider").value, model: $("#aiModel").value });
  if (r) $("#aiIn").value = "";
}
$("#aiReset").addEventListener("click", () => apiSafe("ai_reset", {}, "محادثة جديدة 🧹"));

// =============================================================================
//  الطرفية
// =============================================================================
const cmdHist = [];
let histIdx = 0;
function renderQuickCmds() {
  const win = /Windows/i.test(S.os);
  const cmds = win ? ["dir", "ipconfig", "Get-Process | Sort CPU -Desc | Select -First 10", "git status", "py --version", "cd .."]
                   : ["ls -la", "pwd", "df -h", "git status", "python3 --version", "cd .."];
  const box = $("#cmdQuick");
  box.innerHTML = "";
  for (const c of cmds) box.append(h("button", { class: "btn sm mono ltr", onclick: () => runCmd(c) }, c));
}
async function runCmd(cmd) {
  cmd = (cmd ?? $("#cmdIn").value).trim();
  if (!cmd) return;
  cmdHist.push(cmd); histIdx = cmdHist.length;
  $("#cmdIn").value = "";
  const term = $("#term");
  term.append(h("div", { class: "cmd" }, `${$("#cwd").textContent || ""}> ${cmd}`));
  const r = await apiSafe("shell", { cmd });
  if (r) {
    if (r.output) term.append(h("div", { class: r.code ? "err" : "" }, r.output));
    $("#cwd").textContent = r.cwd;
  }
  term.scrollTop = term.scrollHeight;
}
$("#cmdBtn").addEventListener("click", () => runCmd());
$("#cmdIn").addEventListener("keydown", (e) => {
  if (e.key === "Enter") runCmd();
  else if (e.key === "ArrowUp" && histIdx > 0) { histIdx--; $("#cmdIn").value = cmdHist[histIdx]; e.preventDefault(); }
  else if (e.key === "ArrowDown") { histIdx = Math.min(cmdHist.length, histIdx + 1); $("#cmdIn").value = cmdHist[histIdx] || ""; }
});

// =============================================================================
//  الملفات
// =============================================================================
let curPath = "";
async function loadDir(path) {
  const r = await apiSafe("fs_list", { path });
  if (!r) return;
  curPath = r.path;
  const crumbs = $("#crumbs");
  crumbs.innerHTML = "";
  crumbs.append(h("button", { onclick: () => loadDir("") }, "🏠 المجلد الرئيسي"));
  let acc = "";
  for (const part of (r.path ? r.path.split("/") : [])) {
    acc = acc ? `${acc}/${part}` : part;
    const p = acc;
    crumbs.append(h("button", { onclick: () => loadDir(p) }, part));
  }
  const list = $("#fsList");
  list.innerHTML = "";
  if (!r.items.length) list.append(h("div", { class: "empty" }, "مجلد فارغ"));
  for (const it of r.items) {
    const full = r.path ? `${r.path}/${it.name}` : it.name;
    list.append(h("div", {
      class: "f",
      onclick: () => (it.dir ? loadDir(full) : downloadPc(full)),
    }, h("span", {}, it.dir ? "📁" : fileIcon(it.name)), h("span", { class: "n" }, it.name),
      h("span", { class: "s" }, it.dir ? "" : fmtBytes(it.size))));
  }
}
function fileIcon(n) {
  const e = n.split(".").pop().toLowerCase();
  if (["png", "jpg", "jpeg", "gif", "webp", "bmp", "heic"].includes(e)) return "🖼️";
  if (["mp4", "mkv", "mov", "avi", "webm"].includes(e)) return "🎬";
  if (["mp3", "wav", "m4a", "flac", "ogg"].includes(e)) return "🎵";
  if (["zip", "rar", "7z", "tar", "gz"].includes(e)) return "🗜️";
  if (["py", "js", "ts", "html", "css", "json", "kt", "java", "c", "cpp", "cs", "go", "rs"].includes(e)) return "🧩";
  if (["pdf"].includes(e)) return "📕";
  return "📄";
}
function downloadPc(path) {
  const a = h("a", { href: `/api/fs/download?token=${encodeURIComponent(S.token)}&path=${encodeURIComponent(path)}`, download: path.split("/").pop() });
  document.body.append(a); a.click(); a.remove();
  toast("جاري التنزيل…");
}
$("#fsRefresh").addEventListener("click", () => loadDir(curPath));

function upload(file, kind = "file") {
  return new Promise((resolve) => {
    const xhr = new XMLHttpRequest();
    xhr.open("POST", `/api/upload?token=${encodeURIComponent(S.token)}&kind=${kind}&name=${encodeURIComponent(file.name || "photo.jpg")}`);
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) $("#upStatus").textContent = `⬆️ ${file.name}: ${Math.round(e.loaded * 100 / e.total)}%`;
    };
    xhr.onload = () => {
      let j = {};
      try { j = JSON.parse(xhr.responseText); } catch (_) { /* ignore */ }
      if (xhr.status === 200 && j.ok) {
        $("#upStatus").textContent = `✓ تم الحفظ على الحاسوب: ${j.saved}`;
        toast(`تم إرسال ${file.name || "الصورة"} ✓`, "ok");
      } else {
        $("#upStatus").textContent = `✗ ${j.error || "فشل الرفع"}`;
        toast(j.error || "فشل الرفع", "err");
      }
      resolve();
    };
    xhr.onerror = () => { toast("فشل الرفع", "err"); resolve(); };
    xhr.send(file);
  });
}
$("#fileIn").addEventListener("change", async (e) => { for (const f of e.target.files) await upload(f); e.target.value = ""; });
$("#camIn").addEventListener("change", async (e) => { for (const f of e.target.files) await upload(f, "photo"); e.target.value = ""; });
$("#reqCamIn").addEventListener("change", async (e) => { for (const f of e.target.files) await upload(f, "photo"); e.target.value = ""; });

// =============================================================================
//  الوارد من الحاسوب (التحكم بالهاتف من الحاسوب)
// =============================================================================
function unlockAudio() {
  if (S.audio) return;
  try {
    S.audio = new (window.AudioContext || window.webkitAudioContext)();
    const o = S.audio.createOscillator(), g = S.audio.createGain();
    g.gain.value = 0; o.connect(g); g.connect(S.audio.destination); o.start(); o.stop(S.audio.currentTime + 0.01);
  } catch (_) { S.audio = null; }
}
document.addEventListener("pointerdown", unlockAudio, { once: true });

let ringTimer = null, ringOv = null;
function startRing() {
  stopRing();
  ringOv = h("div", { class: "ring-ov" },
    h("div", { class: "bell" }, "🔔"),
    h("div", { style: "font-size:22px;font-weight:800" }, "الحاسوب يبحث عن هاتفك"),
    h("div", { class: "muted" }, "Hamza Link"),
    h("button", { class: "btn primary", style: "padding:16px 40px;font-size:18px", onclick: () => { stopRing(); api("reply", { kind: "ack", text: "تم العثور على الهاتف ✓" }).catch(() => {}); } }, "تم العثور عليه ✓"));
  document.body.append(ringOv);
  let n = 0;
  const beep = () => {
    if (navigator.vibrate) navigator.vibrate([400, 150, 400]);
    if (S.audio) {
      if (S.audio.state === "suspended") S.audio.resume();
      const t = S.audio.currentTime;
      for (let i = 0; i < 3; i++) {
        const o = S.audio.createOscillator(), g = S.audio.createGain();
        o.type = "square"; o.frequency.value = i % 2 ? 1320 : 880;
        g.gain.setValueAtTime(0.0001, t + i * 0.22);
        g.gain.exponentialRampToValueAtTime(0.5, t + i * 0.22 + 0.02);
        g.gain.exponentialRampToValueAtTime(0.0001, t + i * 0.22 + 0.2);
        o.connect(g); g.connect(S.audio.destination); o.start(t + i * 0.22); o.stop(t + i * 0.22 + 0.21);
      }
    }
    if (++n > 30) stopRing();
  };
  beep();
  ringTimer = setInterval(beep, 1000);
}
function stopRing() {
  clearInterval(ringTimer); ringTimer = null;
  if (ringOv) { ringOv.remove(); ringOv = null; }
  if (navigator.vibrate) navigator.vibrate(0);
}

function speak(text) {
  if (!("speechSynthesis" in window)) return toast("النطق غير مدعوم في هذا المتصفح", "err");
  const u = new SpeechSynthesisUtterance(text);
  u.lang = /[\u0600-\u06FF]/.test(text) ? "ar-SA" : "en-US";
  speechSynthesis.cancel();
  speechSynthesis.speak(u);
}

async function deviceInfo() {
  const n = navigator, c = n.connection || {};
  const info = {
    "الجهاز": guessDeviceName(),
    "المتصفح": n.userAgent,
    "النظام": n.userAgentData?.platform || n.platform || "",
    "اللغة": n.language,
    "الشاشة": `${screen.width}×${screen.height} @${devicePixelRatio}x`,
    "نافذة العرض": `${innerWidth}×${innerHeight}`,
    "المنطقة الزمنية": Intl.DateTimeFormat().resolvedOptions().timeZone,
    "الوقت على الهاتف": new Date().toLocaleString("ar"),
    "الشبكة": c.effectiveType ? `${c.effectiveType} · ${c.downlink || "?"} Mbps` : (n.onLine ? "متصل" : "غير متصل"),
    "المعالج": n.hardwareConcurrency ? `${n.hardwareConcurrency} أنوية` : "",
    "الذاكرة": n.deviceMemory ? `${n.deviceMemory} GB` : "",
    "اللمس": n.maxTouchPoints ? `${n.maxTouchPoints} نقاط` : "لا",
    "وضع التطبيق": matchMedia("(display-mode: standalone)").matches ? "مثبّت على الشاشة الرئيسية" : "متصفح",
  };
  try {
    if (n.getBattery) {
      const b = await n.getBattery();
      info["البطارية"] = `${Math.round(b.level * 100)}% ${b.charging ? "⚡ يشحن" : ""}`;
    }
  } catch (_) { /* غير متاح */ }
  for (const k of Object.keys(info)) if (!info[k]) delete info[k];
  return info;
}

async function onPush(item) {
  switch (item.kind) {
    case "ring": startRing(); break;
    case "stop_ring": stopRing(); return;
    case "vibrate": if (navigator.vibrate) navigator.vibrate([300, 120, 300, 120, 700]); toast("📳 اهتزاز من الحاسوب"); break;
    case "speak": speak(item.text); break;
    case "info_request": {
      const info = await deviceInfo();
      api("reply", { kind: "info", info }).catch(() => {});
      item.text = "تم إرسال معلومات الجهاز إلى الحاسوب ✓";
      break;
    }
    case "toast": toast(item.text); break;
    case "photo_request": if (navigator.vibrate) navigator.vibrate([200, 100, 200]); toast("📷 الحاسوب يطلب صورة"); break;
    case "clip_request": if (navigator.vibrate) navigator.vibrate(200); toast("📋 الحاسوب يطلب حافظة الهاتف"); break;
    default: if (navigator.vibrate) navigator.vibrate(60);
  }
  S.inbox.unshift(item);
  S.inbox = S.inbox.slice(0, 60);
  localStorage.setItem("hl_inbox", JSON.stringify(S.inbox.filter((i) => i.kind !== "ring")));
  if (S.tab !== "inbox") { S.unread++; updateBadge(); }
  renderInbox();
}

const KIND_LABEL = {
  text: "نص من الحاسوب", link: "رابط", file: "ملف", speak: "نطق", ring: "رنين", vibrate: "اهتزاز",
  photo_request: "طلب صورة", clip_request: "طلب الحافظة", info_request: "طلب معلومات", toast: "إشعار",
};
function renderInbox() {
  const box = $("#inbox");
  box.innerHTML = "";
  if (!S.inbox.length) {
    box.append(h("div", { class: "empty" }, "لا شيء بعد — أرسل من لوحة الحاسوب نصاً أو رابطاً أو ملفاً."));
    return;
  }
  for (const it of S.inbox) {
    const el = h("div", { class: "item" },
      h("div", { class: "head" }, h("span", { class: "k" }, KIND_ICON[it.kind] || "•"), KIND_LABEL[it.kind] || it.kind,
        h("span", { style: "flex:1" }), fmtTime(it.ts)));
    if (it.kind === "file") {
      el.append(h("div", { class: "body" }, `${it.name} · ${fmtBytes(it.size)}`));
      el.append(h("div", { class: "row", style: "margin-top:8px" },
        h("a", { class: "btn primary sm", href: `/api/file/${it.file_id}?token=${encodeURIComponent(S.token)}`, download: it.name }, "⬇️ تنزيل"),
        /^image\//.test(it.mime || "") ? h("a", { class: "btn sm", target: "_blank", href: `/api/file/${it.file_id}?inline=1&token=${encodeURIComponent(S.token)}` }, "👁️ عرض") : null));
    } else if (it.kind === "photo_request") {
      el.append(h("div", { class: "body" }, it.text || "الحاسوب يطلب صورة من كاميرا الهاتف"));
      el.append(h("div", { class: "row", style: "margin-top:8px" },
        h("button", { class: "btn primary sm", onclick: () => $("#reqCamIn").click() }, "📷 التقاط وإرسال")));
    } else if (it.kind === "clip_request") {
      const ta = h("textarea", { class: "input", rows: 2, placeholder: "الصق النص هنا…" });
      el.append(h("div", { class: "body muted small" }, "الحاسوب يطلب نصاً من حافظة هاتفك"), ta);
      el.append(h("div", { class: "row", style: "margin-top:8px" },
        (navigator.clipboard && window.isSecureContext) ? h("button", { class: "btn sm", onclick: async () => { try { ta.value = await navigator.clipboard.readText(); } catch (e) { toast("اسمح بالوصول للحافظة أو الصق يدوياً", "err"); } } }, "📋 لصق") : null,
        h("button", { class: "btn primary sm", onclick: async () => { if (ta.value) { await apiSafe("reply", { kind: "clipboard", text: ta.value }, "تم الإرسال ✓"); ta.value = ""; } } }, "إرسال")));
    } else {
      if (it.text) el.append(h("div", { class: "body" }, it.text));
      const row = h("div", { class: "row wrap", style: "margin-top:8px" });
      if (it.kind === "link" || /^https?:\/\//.test(it.text || "")) {
        const url = /^https?:\/\//i.test(it.text) ? it.text : `https://${it.text}`;
        row.append(h("a", { class: "btn primary sm", href: url, target: "_blank", rel: "noopener" }, "🔗 فتح"));
      }
      if (it.kind === "speak") row.append(h("button", { class: "btn sm", onclick: () => speak(it.text) }, "🔁 إعادة النطق"));
      if (it.text && ["text", "link", "speak", "toast"].includes(it.kind)) {
        row.append(h("button", { class: "btn sm", onclick: async () => toast((await copyText(it.text)) ? "تم النسخ ✓" : "تعذر النسخ", "ok") }, "📋 نسخ"));
      }
      if (row.childElementCount) el.append(row);
    }
    box.append(el);
  }
}
$("#inboxClear").addEventListener("click", () => { S.inbox = []; localStorage.removeItem("hl_inbox"); renderInbox(); });
$("#msgBtn").addEventListener("click", async () => {
  const text = $("#msgIn").value.trim();
  if (!text) return;
  const r = await apiSafe("send_text", { text }, "تم الإرسال إلى الحاسوب ✓");
  if (r) $("#msgIn").value = "";
});

// =============================================================================
//  البدء
// =============================================================================
$("#pairBtn").addEventListener("click", pair);
$("#pinIn").addEventListener("keydown", (e) => { if (e.key === "Enter") pair(); });
$("#unpairBtn").addEventListener("click", () => { if (confirm("إلغاء الاقتران مع هذا الحاسوب؟")) unpair(); });

if (location.hash.includes("pin=") && !S.token) showPair();
else if (S.token) startApp();
else showPair();
