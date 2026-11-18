/* Hamza Link — أدوات مشتركة — تصميم Hamza Alfarai */
"use strict";

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));

function h(tag, attrs = {}, ...children) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v == null || v === false) continue;
    if (k === "class") el.className = v;
    else if (k === "html") el.innerHTML = v;
    else if (k.startsWith("on")) el.addEventListener(k.slice(2), v);
    else el.setAttribute(k, v === true ? "" : v);
  }
  for (const c of children.flat()) {
    if (c == null || c === false) continue;
    el.append(c instanceof Node ? c : document.createTextNode(String(c)));
  }
  return el;
}

function esc(s) {
  return String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

/** Markdown خفيف: كتل الكود + `كود` + **عريض** */
function renderMd(text) {
  const parts = String(text || "").split(/```/);
  return parts.map((p, i) => {
    if (i % 2 === 1) {
      const nl = p.indexOf("\n");
      const code = nl >= 0 && /^[\w+#.-]*$/.test(p.slice(0, nl).trim()) ? p.slice(nl + 1) : p;
      return `<pre>${esc(code.replace(/\n$/, ""))}</pre>`;
    }
    return esc(p)
      .replace(/`([^`\n]+)`/g, "<code>$1</code>")
      .replace(/\*\*([^*\n]+)\*\*/g, "<b>$1</b>");
  }).join("");
}

function fmtBytes(n) {
  if (!n && n !== 0) return "";
  const u = ["B", "KB", "MB", "GB", "TB"];
  let i = 0;
  while (n >= 1024 && i < u.length - 1) { n /= 1024; i++; }
  return `${n.toFixed(n >= 10 || i === 0 ? 0 : 1)} ${u[i]}`;
}

function fmtTime(ts) {
  if (!ts) return "";
  const d = new Date(ts);
  return d.toLocaleTimeString("ar", { hour: "2-digit", minute: "2-digit", second: "2-digit" });
}

function fmtAgo(ts) {
  if (!ts) return "—";
  const s = Math.max(0, (Date.now() - ts) / 1000);
  if (s < 60) return "الآن";
  if (s < 3600) return `منذ ${Math.floor(s / 60)} د`;
  if (s < 86400) return `منذ ${Math.floor(s / 3600)} س`;
  return new Date(ts).toLocaleDateString("ar");
}

function toast(msg, kind = "") {
  let box = $("#toasts");
  if (!box) { box = h("div", { id: "toasts" }); document.body.append(box); }
  const t = h("div", { class: `toast ${kind}` }, msg);
  box.append(t);
  setTimeout(() => { t.style.opacity = "0"; t.style.transition = "opacity .3s"; }, 2800);
  setTimeout(() => t.remove(), 3200);
}

/** نسخ يعمل حتى على HTTP (بدون سياق آمن) */
async function copyText(text) {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch (_) { /* fallback */ }
  const ta = h("textarea", { style: "position:fixed;opacity:0;top:0;left:0" });
  ta.value = text;
  document.body.append(ta);
  ta.select();
  let ok = false;
  try { ok = document.execCommand("copy"); } catch (_) { ok = false; }
  ta.remove();
  return ok;
}

const KIND_ICON = {
  text: "💬", link: "🔗", file: "📄", photo: "🖼️", speak: "🗣️", ring: "🔔", vibrate: "📳",
  photo_request: "📷", clip_request: "📋", clipboard: "📋", info_request: "ℹ️", info: "ℹ️", toast: "✨",
};

const CREDIT_HTML = 'تصميم <b>Hamza Alfarai</b>';
