/* Hamza Link — لوحة الحاسوب — تصميم Hamza Alfarai */
"use strict";

const D = { state: null, ai: null, devices: [], ip: localStorage.getItem("hl_ip") || "" };
$("#credit").innerHTML = CREDIT_HTML;

async function api(action, data = {}) {
  const r = await fetch("/api/desktop", {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ action, ...data }),
  });
  const j = await r.json().catch(() => ({}));
  if (!r.ok || j.ok === false) throw new Error(j.error || `HTTP ${r.status}`);
  return j;
}
async function apiSafe(action, data, okMsg) {
  try { const j = await api(action, data); if (okMsg) toast(okMsg, "ok"); return j; }
  catch (e) { toast(e.message, "err"); return null; }
}

// =============================================================================
//  الأحداث المباشرة
// =============================================================================
function connect() {
  const es = new EventSource("/api/events?role=desktop");
  es.addEventListener("hello", (e) => onHello(JSON.parse(e.data)));
  es.addEventListener("log", (e) => addLog(JSON.parse(e.data)));
  es.addEventListener("devices", (e) => renderDevices(JSON.parse(e.data)));
  es.addEventListener("pin", (e) => { D.state.pin = JSON.parse(e.data).pin; renderPair(); });
  es.addEventListener("inbox", (e) => addInbox(JSON.parse(e.data), true));
  es.addEventListener("perms", (e) => renderPerms(JSON.parse(e.data)));
  es.addEventListener("ai", (e) => onAi(JSON.parse(e.data)));
  es.addEventListener("shell", (e) => addShell(JSON.parse(e.data)));
  es.onopen = () => setSrv(true);
  es.onerror = () => setSrv(false);
}
function setSrv(on) {
  $("#srvDot").className = "dot " + (on ? "on" : "off");
  $("#srvTxt").textContent = on ? "الخادم يعمل" : "غير متصل بالخادم";
}

function onHello(s) {
  D.state = s;
  $("#ver").textContent = `v${s.version}`;
  $("#pcName").textContent = `${s.pc} · ${s.os}`;
  const sel = $("#ipSel");
  sel.innerHTML = "";
  for (const ip of s.ips) sel.append(h("option", { value: ip }, ip));
  if (s.ips.includes(D.ip)) sel.value = D.ip;
  renderPair();
  renderDevices(s.devices);
  renderPerms(s.perms);
  $("#log").innerHTML = "";
  s.log.forEach(addLog);
  $("#inbox").innerHTML = "";
  if (!s.inbox.length) $("#inbox").append(h("div", { class: "empty" }, "الصور والملفات والرسائل القادمة من الهاتف تظهر هنا."));
  s.inbox.forEach((i) => addInbox(i, false));
  D.ai = s.ai;
  renderAiAll();
}

// =============================================================================
//  الاقتران
// =============================================================================
function phoneBase() {
  const local = ["127.0.0.1", "localhost", "[::1]"].includes(location.hostname);
  if (!local) return location.origin; // وضع التجربة / المعاينة
  const ip = $("#ipSel").value || (D.state && D.state.ips[0]);
  return `http://${ip}:${D.state.port}`;
}
function renderPair() {
  if (!D.state) return;
  const url = `${phoneBase()}/#pin=${D.state.pin}`;
  $("#pin").textContent = D.state.pin;
  $("#url").textContent = phoneBase() + "/";
  try {
    const qr = qrcode(0, "M");
    qr.addData(url);
    qr.make();
    $("#qr").innerHTML = qr.createSvgTag({ cellSize: 4, margin: 0, scalable: true });
  } catch (e) {
    $("#qr").textContent = url;
  }
}
$("#ipSel").addEventListener("change", () => { D.ip = $("#ipSel").value; localStorage.setItem("hl_ip", D.ip); renderPair(); });
$("#newPin").addEventListener("click", () => apiSafe("new_pin", {}, "تم إنشاء رمز جديد"));

function renderDevices(list) {
  D.devices = list || [];
  const box = $("#devices");
  box.innerHTML = "";
  if (!D.devices.length) box.append(h("div", { class: "empty" }, "لا توجد أجهزة بعد — امسح رمز QR بهاتفك."));
  for (const d of D.devices) {
    box.append(h("div", { class: "dev" },
      h("span", { class: "dot " + (d.online ? "on" : "") }),
      h("div", { class: "grow" },
        h("div", { class: "n" }, d.name),
        h("div", { class: "small muted" }, d.online ? "متصل الآن" : `آخر ظهور: ${fmtAgo(d.last_seen)}`)),
      h("button", { class: "btn sm danger", title: "إلغاء الاقتران", onclick: () => { if (confirm(`إلغاء اقتران «${d.name}»؟`)) apiSafe("revoke", { device: d.id }); } }, "إلغاء")));
  }
  const online = D.devices.filter((d) => d.online);
  $("#onlineCount").textContent = online.length;
  const tgt = $("#target");
  const prev = tgt.value;
  tgt.innerHTML = "";
  tgt.append(h("option", { value: "" }, "كل الهواتف"));
  for (const d of D.devices) tgt.append(h("option", { value: d.id }, `${d.online ? "🟢" : "⚪"} ${d.name}`));
  tgt.value = D.devices.some((d) => d.id === prev) ? prev : "";
}

const PERMS = [
  ["input", "🖱️ الماوس ولوحة المفاتيح", "تحريك الماوس، الكتابة، الوسائط، قفل الشاشة"],
  ["screen", "🖥️ عرض الشاشة", "بث لقطات الشاشة إلى الهاتف"],
  ["files", "📁 الملفات", "تصفح وتنزيل ملفات مجلد المستخدم + رفع ملفات من الهاتف"],
  ["clipboard", "📋 الحافظة والروابط", "قراءة/كتابة الحافظة وفتح الروابط"],
  ["ai", "✳️ Claude", "إرسال رسائل لـ Claude باستخدام مفاتيحك"],
  ["shell", "💻 الطرفية", "تنفيذ أوامر على الحاسوب — فعّلها فقط إن كنت تثق بالهاتف", true],
];
function renderPerms(p) {
  const box = $("#perms");
  box.innerHTML = "";
  for (const [key, label, desc, danger] of PERMS) {
    const input = h("input", { type: "checkbox" });
    input.checked = !!p[key];
    input.addEventListener("change", () => apiSafe("perm", { name: key, value: input.checked }));
    box.append(h("label", { class: "toggle" + (danger ? " danger" : "") },
      h("span", { class: "lbl" }, label, h("small", {}, desc)),
      h("span", { class: "switch" }, input, h("span"))));
  }
}

// =============================================================================
//  التحكم بالهاتف
// =============================================================================
const target = () => $("#target").value || null;
async function push(kind, text = "") {
  const r = await apiSafe("push", { kind, text, target: target() });
  if (!r) return;
  if (r.delivered) toast(`تم الإرسال إلى ${r.delivered} هاتف ✓`, "ok");
  else if (["text", "link"].includes(kind)) toast("لا يوجد هاتف متصل — سيُسلَّم عند الاتصال");
  else toast("لا يوجد هاتف متصل", "err");
}
$$("[data-push]").forEach((b) => b.addEventListener("click", () => push(b.dataset.push)));
function pushText(kind) {
  const t = $("#pushText").value.trim();
  if (!t) return toast("اكتب نصاً أولاً", "err");
  push(kind, t);
}
$("#sendText").addEventListener("click", () => pushText("text"));
$("#sendLink").addEventListener("click", () => pushText("link"));
$("#sendSpeak").addEventListener("click", () => pushText("speak"));
$("#sendToast").addEventListener("click", () => pushText("toast"));
$("#pasteClip").addEventListener("click", async () => {
  try { $("#pushText").value = await navigator.clipboard.readText(); }
  catch (_) { toast("اسمح للمتصفح بقراءة الحافظة", "err"); }
});

function sendFile(file) {
  return new Promise((resolve) => {
    const xhr = new XMLHttpRequest();
    const t = target();
    xhr.open("POST", `/api/upload?role=desktop&name=${encodeURIComponent(file.name)}${t ? `&target=${t}` : ""}`);
    xhr.upload.onprogress = (e) => { if (e.lengthComputable) $("#sendStatus").textContent = `⬆️ ${file.name}: ${Math.round(e.loaded * 100 / e.total)}%`; };
    xhr.onload = () => {
      const j = JSON.parse(xhr.responseText || "{}");
      if (j.ok) {
        $("#sendStatus").textContent = `✓ ${file.name} — ${j.delivered ? `أُرسل إلى ${j.delivered} هاتف` : "سيُسلَّم عند اتصال الهاتف"}`;
        toast(`تم إرسال ${file.name} ✓`, "ok");
      } else toast(j.error || "فشل الإرسال", "err");
      resolve();
    };
    xhr.onerror = () => { toast("فشل الإرسال", "err"); resolve(); };
    xhr.send(file);
  });
}
const drop = $("#drop");
$("#fileSend").addEventListener("change", async (e) => { for (const f of e.target.files) await sendFile(f); e.target.value = ""; });
["dragenter", "dragover"].forEach((ev) => drop.addEventListener(ev, (e) => { e.preventDefault(); drop.classList.add("over"); }));
["dragleave", "drop"].forEach((ev) => drop.addEventListener(ev, (e) => { e.preventDefault(); drop.classList.remove("over"); }));
drop.addEventListener("drop", async (e) => { for (const f of e.dataTransfer.files) await sendFile(f); });

// =============================================================================
//  الوارد من الهاتف
// =============================================================================
const IN_LABEL = { text: "رسالة", clipboard: "حافظة الهاتف", info: "معلومات الجهاز", photo: "صورة", file: "ملف" };
function addInbox(it, fresh) {
  const box = $("#inbox");
  $(".empty", box)?.remove();
  const el = h("div", { class: "item" },
    h("div", { class: "head" }, h("span", { class: "k" }, KIND_ICON[it.kind] || "•"),
      `${IN_LABEL[it.kind] || it.kind} — ${it.from}`, h("span", { style: "flex:1" }), fmtTime(it.ts)));
  if (it.kind === "text" || it.kind === "clipboard") {
    el.append(h("div", { class: "body" }, it.text));
    el.append(h("div", { class: "row", style: "margin-top:8px" },
      h("button", { class: "btn sm", onclick: () => apiSafe("copy", { text: it.text }, "تم النسخ إلى حافظة الحاسوب ✓") }, "📋 نسخ"),
      /^https?:\/\//.test(it.text) ? h("a", { class: "btn sm", href: it.text, target: "_blank" }, "🔗 فتح") : null));
  } else if (it.kind === "info") {
    const tb = h("table");
    for (const [k, v] of Object.entries(it.info || {})) tb.append(h("tr", {}, h("td", {}, k), h("td", {}, String(v))));
    el.append(tb);
  } else if (it.kind === "photo" || it.kind === "file") {
    if (/^image\//.test(it.mime || "")) {
      el.append(h("a", { href: `/api/file/${it.file_id}?inline=1`, target: "_blank" }, h("img", { src: `/api/file/${it.file_id}?inline=1`, alt: it.name })));
    }
    el.append(h("div", { class: "small muted", style: "margin-top:6px" }, `${it.name} · ${fmtBytes(it.size)}`));
    el.append(h("div", { class: "small mono ltr muted" }, it.path || ""));
  }
  box.prepend(el);
  if (fresh) {
    toast(`📥 ${IN_LABEL[it.kind] || "عنصر"} من ${it.from}`);
    document.title = "• Hamza Link — لوحة الحاسوب";
    setTimeout(() => (document.title = "Hamza Link — لوحة الحاسوب"), 4000);
  }
}
$("#openInbox").addEventListener("click", () => apiSafe("open_inbox"));

function addShell(d) {
  $("#shellCard").classList.remove("hidden");
  const t = $("#shellLog");
  t.append(h("div", { class: "cmd" }, `[${d.from}] ${d.cwd}> ${d.cmd}`));
  if (d.output) t.append(h("div", { class: d.code ? "err" : "" }, d.output.slice(-3000)));
  t.scrollTop = t.scrollHeight;
}

function addLog(e) {
  const log = $("#log");
  log.append(h("div", { class: `e ${e.level}` }, h("span", { class: "t" }, fmtTime(e.ts)), h("span", { class: "x" }, e.text)));
  while (log.childElementCount > 300) log.firstElementChild.remove();
  log.scrollTop = log.scrollHeight;
}

// =============================================================================
//  Claude — الجلسة المشتركة
// =============================================================================
function renderAiAll() {
  const ai = D.ai;
  if (!ai) return;
  const prov = $("#aiProvider");
  prov.innerHTML = "";
  for (const p of Object.keys(ai.models)) prov.append(h("option", { value: p }, p === "openrouter" ? "OpenRouter" : "DeepSeek"));
  prov.value = ai.provider;
  fillModels();
  $("#aiModel").value = ai.model;
  updateKeysHint();
  const chat = $("#chat");
  chat.innerHTML = "";
  if (!ai.view.length) chat.append(h("div", { class: "empty" }, "المحادثة هنا تظهر أيضاً على الهاتف — اكتب من أي جهاز."));
  for (const m of ai.view) chat.append(msgEl(m));
  chat.scrollTop = chat.scrollHeight;
  $("#aiSend").disabled = ai.busy;
}
function updateKeysHint() {
  const k = (D.ai && D.ai.keys) || {};
  const p = $("#aiProvider").value;
  $("#aiKeys").innerHTML = k[p] ? "🔑 المفتاح جاهز" : '⚠️ لا يوجد مفتاح API لهذا المزوّد — اضغط «🔑 المفاتيح»';
}
function fillModels() {
  const sel = $("#aiModel");
  sel.innerHTML = "";
  for (const m of (D.ai.models[$("#aiProvider").value] || [])) sel.append(h("option", { value: m.id }, m.label));
}
$("#aiProvider").addEventListener("change", () => { fillModels(); updateKeysHint(); });
function msgEl(m) {
  const el = h("div", { class: `msg ${m.role}${m.error ? " error" : ""}` });
  el.append(h("div", { class: "who" }, m.role === "user" ? m.from : `✳️ ${m.from}`));
  el.append(h("div", { class: "body", html: renderMd(m.text) }));
  return el;
}
function onAi(d) {
  if (!D.ai) return;
  const chat = $("#chat");
  if (d.type === "user") {
    $(".empty", chat)?.remove();
    D.ai.busy = true;
    const u = { role: "user", text: d.text, from: d.from }, a = { role: "assistant", text: "", from: d.model };
    D.ai.view.push(u, a);
    chat.append(msgEl(u));
    const ael = msgEl(a);
    $(".body", ael).classList.add("typing");
    chat.append(ael);
    $("#aiSend").disabled = true;
  } else if (d.type === "delta") {
    const last = D.ai.view[D.ai.view.length - 1];
    if (last) last.text += d.text;
    const el = chat.lastElementChild && $(".body", chat.lastElementChild);
    if (el) el.innerHTML = renderMd(last.text);
  } else if (d.type === "done" || d.type === "error") {
    D.ai.busy = false;
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
    D.ai.view = [];
    renderAiAll();
  } else if (d.type === "state") {
    Object.assign(D.ai, d);
    renderAiAll();
  }
  chat.scrollTop = chat.scrollHeight;
}
async function sendAi() {
  const prompt = $("#aiIn").value.trim();
  if (!prompt) return;
  const r = await apiSafe("ai_send", { prompt, provider: $("#aiProvider").value, model: $("#aiModel").value });
  if (r) $("#aiIn").value = "";
}
$("#aiSend").addEventListener("click", sendAi);
$("#aiIn").addEventListener("keydown", (e) => { if (e.key === "Enter" && !e.shiftKey && !e.isComposing) { e.preventDefault(); sendAi(); } });
$("#aiReset").addEventListener("click", () => apiSafe("ai_reset", {}, "محادثة جديدة 🧹"));

// ---------- المفاتيح ----------
$("#keysBtn").addEventListener("click", () => { $("#kOr").value = ""; $("#kDs").value = ""; $("#keysModal").classList.remove("hidden"); });
$("#kCancel").addEventListener("click", () => $("#keysModal").classList.add("hidden"));
$("#kSave").addEventListener("click", async () => {
  const data = {};
  if ($("#kOr").value.trim()) data.openrouter = $("#kOr").value.trim();
  if ($("#kDs").value.trim()) data.deepseek = $("#kDs").value.trim();
  if (!Object.keys(data).length) return $("#keysModal").classList.add("hidden");
  const r = await apiSafe("keys", data, "تم حفظ المفاتيح 🔑");
  if (r) $("#keysModal").classList.add("hidden");
});

setInterval(() => { if (D.devices.length) renderDevices(D.devices); }, 30000);
connect();
