# -*- coding: utf-8 -*-
"""
Hamza Link — تكامل الحاسوب والهاتف (تصميم Hamza Alfarai)
=========================================================

خادم محلي على الشبكة (Wi-Fi) يربط الحاسوب بالهاتف ويتيح التحكم في الاتجاهين:

  الهاتف ← يتحكم في → الحاسوب
    • لوحة لمس (ماوس) + لوحة مفاتيح (تدعم العربية) + أزرار الوسائط والصوت
    • عرض شاشة الحاسوب مباشرة + النقر على الشاشة
    • الطرفية (PowerShell / bash) — تُفعّل من الحاسوب فقط
    • تصفح ملفات الحاسوب وتنزيلها + رفع الملفات والصور إلى الحاسوب
    • الحافظة (جلب / إرسال) + فتح روابط على الحاسوب
    • جلسة Claude مشتركة (OpenRouter / DeepSeek) تظهر على الجهازين

  الحاسوب ← يتحكم في → الهاتف
    • رنين للعثور على الهاتف + اهتزاز + نطق نص بصوت الهاتف
    • إرسال نصوص وروابط وملفات للهاتف
    • طلب صورة من كاميرا الهاتف + طلب حافظة الهاتف + معلومات الجهاز

لا يحتاج أي مكتبات خارجية (Python القياسية فقط). Pillow اختيارية لعرض الشاشة.

التشغيل:
    py hamza_link.py                 # يفتح لوحة الحاسوب تلقائياً
    py hamza_link.py --port 8765 --no-browser
"""

from __future__ import annotations

import argparse
import collections
import ctypes
import io
import json
import mimetypes
import os
import platform
import queue
import secrets
import shutil
import socket
import ssl
import subprocess
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import webbrowser
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

APP_NAME = "Hamza Link"
APP_VERSION = "1.0.0"
APP_AUTHOR = "Hamza Alfarai"

HERE = Path(__file__).resolve().parent
WEB_DIR = HERE / "web"
STATE_PATH = Path.home() / ".hamza_link.json"
INBOX_DIR = Path.home() / "Downloads" / "Hamza Link"
MAX_UPLOAD = 4 * 1024 * 1024 * 1024  # 4 GB

# ------------------------------------------------------------------------------
#  التكامل مع نسخة الحاسوب (claude_code_suite.py): نفس المفاتيح والنماذج والإعدادات
# ------------------------------------------------------------------------------
sys.path.insert(0, str(HERE.parent))
try:
    import claude_code_suite as suite  # noqa: E402

    BUILTIN_MODELS = suite.BUILTIN_MODELS
    OPENROUTER_BASE_URL = suite.OPENROUTER_BASE_URL
    DEEPSEEK_BASE_URL = suite.DEEPSEEK_BASE_URL
    load_suite_config = suite.load_config
    save_suite_config = suite.save_config
except Exception:  # يعمل أيضاً بشكل مستقل
    suite = None
    OPENROUTER_BASE_URL = "https://openrouter.ai/api"
    DEEPSEEK_BASE_URL = "https://api.deepseek.com/anthropic"
    BUILTIN_MODELS = {
        "openrouter": [{"id": "anthropic/claude-sonnet-4.5", "label": "Claude Sonnet"}],
        "deepseek": [{"id": "deepseek-chat", "label": "DeepSeek Chat"}],
    }
    _cfg_path = Path.home() / ".claude_code_gui_config.json"

    def load_suite_config() -> dict:
        try:
            return json.loads(_cfg_path.read_text(encoding="utf-8"))
        except Exception:
            return {}

    def save_suite_config(data: dict):
        cfg = load_suite_config()
        cfg.update(data)
        _cfg_path.write_text(json.dumps(cfg, indent=2, ensure_ascii=False), encoding="utf-8")


def api_key_for(provider: str) -> str:
    cfg = load_suite_config()
    if provider == "deepseek":
        return cfg.get("deepseek_key") or os.environ.get("DEEPSEEK_API_KEY", "")
    return cfg.get("openrouter_key") or os.environ.get("OPENROUTER_API_KEY", "")


def ssl_context():
    try:
        import certifi  # type: ignore

        return ssl.create_default_context(cafile=certifi.where())
    except Exception:
        return ssl.create_default_context()


def now_ms() -> int:
    return int(time.time() * 1000)


def log_console(*a):
    try:
        print(*a, flush=True)
    except UnicodeEncodeError:
        print(" ".join(str(x) for x in a).encode("ascii", "replace").decode(), flush=True)


# ==============================================================================
#  الإعدادات الدائمة (الأجهزة المقترنة + الصلاحيات)
# ==============================================================================

DEFAULT_PERMS = {
    "input": True,      # ماوس + لوحة مفاتيح + وسائط
    "screen": True,     # عرض شاشة الحاسوب
    "files": True,      # تصفح / تنزيل / رفع الملفات
    "clipboard": True,  # الحافظة + فتح الروابط
    "ai": True,         # جلسة Claude
    "shell": False,     # الطرفية — مغلقة افتراضياً لأسباب أمنية
}


class Store:
    def __init__(self, path: Path):
        self.path = path
        self.lock = threading.Lock()
        self.data = {"devices": {}, "perms": dict(DEFAULT_PERMS)}
        try:
            loaded = json.loads(path.read_text(encoding="utf-8"))
            self.data["devices"] = loaded.get("devices", {})
            self.data["perms"].update(loaded.get("perms", {}))
        except Exception:
            pass

    def save(self):
        with self.lock:
            try:
                self.path.write_text(json.dumps(self.data, indent=2, ensure_ascii=False), encoding="utf-8")
            except Exception as e:
                log_console("! تعذر حفظ الإعدادات:", e)

    @property
    def devices(self) -> dict:
        return self.data["devices"]

    @property
    def perms(self) -> dict:
        return self.data["perms"]


# ==============================================================================
#  التحكم في الحاسوب: الماوس ولوحة المفاتيح (Windows عبر SendInput — يدعم العربية)
# ==============================================================================

class InputController:
    VK = {
        "enter": 0x0D, "backspace": 0x08, "tab": 0x09, "esc": 0x1B, "space": 0x20,
        "left": 0x25, "up": 0x26, "right": 0x27, "down": 0x28,
        "delete": 0x2E, "insert": 0x2D, "home": 0x24, "end": 0x23,
        "pageup": 0x21, "pagedown": 0x22, "win": 0x5B,
        "ctrl": 0x11, "alt": 0x12, "shift": 0x10, "capslock": 0x14, "printscreen": 0x2C,
        "volume_mute": 0xAD, "volume_down": 0xAE, "volume_up": 0xAF,
        "next": 0xB0, "prev": 0xB1, "stop": 0xB2, "play_pause": 0xB3,
        **{f"f{i}": 0x6F + i for i in range(1, 13)},
    }
    EXTENDED = {0x25, 0x26, 0x27, 0x28, 0x2E, 0x2D, 0x24, 0x23, 0x21, 0x22, 0x5B,
                0xAD, 0xAE, 0xAF, 0xB0, 0xB1, 0xB2, 0xB3}
    PYAUTOGUI_KEYS = {"esc": "esc", "play_pause": "playpause", "next": "nexttrack",
                      "prev": "prevtrack", "volume_mute": "volumemute",
                      "volume_up": "volumeup", "volume_down": "volumedown", "win": "win"}

    def __init__(self):
        self.win = os.name == "nt"
        self._pag = None
        if self.win:
            self._init_win()

    # ---------------- Windows (ctypes) ----------------
    def _init_win(self):
        from ctypes import wintypes

        ULONG_PTR = ctypes.c_size_t

        class MOUSEINPUT(ctypes.Structure):
            _fields_ = [("dx", wintypes.LONG), ("dy", wintypes.LONG), ("mouseData", wintypes.DWORD),
                        ("dwFlags", wintypes.DWORD), ("time", wintypes.DWORD), ("dwExtraInfo", ULONG_PTR)]

        class KEYBDINPUT(ctypes.Structure):
            _fields_ = [("wVk", wintypes.WORD), ("wScan", wintypes.WORD), ("dwFlags", wintypes.DWORD),
                        ("time", wintypes.DWORD), ("dwExtraInfo", ULONG_PTR)]

        class HARDWAREINPUT(ctypes.Structure):
            _fields_ = [("uMsg", wintypes.DWORD), ("wParamL", wintypes.WORD), ("wParamH", wintypes.WORD)]

        class _U(ctypes.Union):
            _fields_ = [("mi", MOUSEINPUT), ("ki", KEYBDINPUT), ("hi", HARDWAREINPUT)]

        class INPUT(ctypes.Structure):
            _anonymous_ = ("u",)
            _fields_ = [("type", wintypes.DWORD), ("u", _U)]

        self._INPUT, self._MOUSEINPUT, self._KEYBDINPUT = INPUT, MOUSEINPUT, KEYBDINPUT
        self.user32 = ctypes.windll.user32
        try:
            ctypes.windll.shcore.SetProcessDpiAwareness(2)
        except Exception:
            try:
                self.user32.SetProcessDPIAware()
            except Exception:
                pass

    def _send(self, inputs):
        n = len(inputs)
        arr = (self._INPUT * n)(*inputs)
        self.user32.SendInput(n, arr, ctypes.sizeof(self._INPUT))

    def _mouse(self, flags, dx=0, dy=0, data=0):
        inp = self._INPUT(type=0)
        inp.mi = self._MOUSEINPUT(dx, dy, data & 0xFFFFFFFF, flags, 0, 0)
        return inp

    def _key(self, vk=0, scan=0, flags=0):
        inp = self._INPUT(type=1)
        inp.ki = self._KEYBDINPUT(vk, scan, flags, 0, 0)
        return inp

    def _vk_events(self, vk, up=False):
        flags = (0x0001 if vk in self.EXTENDED else 0) | (0x0002 if up else 0)
        return self._key(vk=vk, flags=flags)

    def _pyautogui(self):
        if self._pag is None:
            try:
                import pyautogui  # type: ignore

                pyautogui.FAILSAFE = False
                self._pag = pyautogui
            except Exception:
                self._pag = False
        if not self._pag:
            raise RuntimeError("التحكم بالماوس ولوحة المفاتيح مدعوم على Windows (أو ثبّت pyautogui)")
        return self._pag

    # ---------------- API ----------------
    def move(self, dx: float, dy: float):
        dx, dy = int(round(dx)), int(round(dy))
        if not dx and not dy:
            return
        if self.win:
            self._send([self._mouse(0x0001, dx, dy)])
        else:
            self._pyautogui().moveRel(dx, dy, _pause=False)

    def move_abs(self, fx: float, fy: float):
        fx, fy = min(max(fx, 0.0), 1.0), min(max(fy, 0.0), 1.0)
        if self.win:
            w, h = self.user32.GetSystemMetrics(0), self.user32.GetSystemMetrics(1)
            self.user32.SetCursorPos(int(fx * (w - 1)), int(fy * (h - 1)))
        else:
            pag = self._pyautogui()
            w, h = pag.size()
            pag.moveTo(int(fx * (w - 1)), int(fy * (h - 1)), _pause=False)

    def button(self, button="left", action="click"):
        flags = {"left": (0x0002, 0x0004), "right": (0x0008, 0x0010), "middle": (0x0020, 0x0040)}
        down, up = flags.get(button, flags["left"])
        if self.win:
            if action == "down":
                self._send([self._mouse(down)])
            elif action == "up":
                self._send([self._mouse(up)])
            else:
                times = 2 if action == "double" else 1
                self._send([self._mouse(down), self._mouse(up)] * times)
        else:
            pag = self._pyautogui()
            if action == "down":
                pag.mouseDown(button=button)
            elif action == "up":
                pag.mouseUp(button=button)
            else:
                pag.click(button=button, clicks=2 if action == "double" else 1)

    def scroll(self, dy: float, dx: float = 0):
        dy, dx = int(dy), int(dx)
        if self.win:
            ev = []
            if dy:
                ev.append(self._mouse(0x0800, data=-dy * 40))
            if dx:
                ev.append(self._mouse(0x1000, data=dx * 40))
            if ev:
                self._send(ev)
        else:
            if dy:
                self._pyautogui().scroll(-dy)

    def type_text(self, text: str):
        if not text:
            return
        if self.win:
            events = []
            for ch in text.replace("\r\n", "\n"):
                if ch == "\n":
                    events += [self._vk_events(0x0D), self._vk_events(0x0D, up=True)]
                    continue
                data = ch.encode("utf-16-le")
                for i in range(0, len(data), 2):
                    unit = int.from_bytes(data[i:i + 2], "little")
                    events += [self._key(scan=unit, flags=0x0004), self._key(scan=unit, flags=0x0004 | 0x0002)]
            for i in range(0, len(events), 200):
                self._send(events[i:i + 200])
        else:
            self._pyautogui().write(text)

    def key(self, name: str, mods=None):
        mods = [m for m in (mods or []) if m in ("ctrl", "alt", "shift", "win")]
        name = (name or "").lower()
        if self.win:
            if name in self.VK:
                vk = self.VK[name]
            elif len(name) == 1 and name.isalnum():
                vk = ord(name.upper())
            else:
                raise ValueError(f"مفتاح غير معروف: {name}")
            ev = [self._vk_events(self.VK[m]) for m in mods]
            ev += [self._vk_events(vk), self._vk_events(vk, up=True)]
            ev += [self._vk_events(self.VK[m], up=True) for m in reversed(mods)]
            self._send(ev)
        else:
            pag = self._pyautogui()
            k = self.PYAUTOGUI_KEYS.get(name, name)
            if mods:
                pag.hotkey(*mods, k)
            else:
                pag.press(k)

    def lock_screen(self):
        if self.win:
            self.user32.LockWorkStation()
        elif sys.platform == "darwin":
            subprocess.Popen(["pmset", "displaysleepnow"])
        else:
            subprocess.Popen(["loginctl", "lock-session"])


# ==============================================================================
#  أدوات النظام: الحافظة، لقطة الشاشة، الطرفية، الملفات
# ==============================================================================

NO_WINDOW = getattr(subprocess, "CREATE_NO_WINDOW", 0)


def clipboard_get() -> str:
    if os.name == "nt":
        r = subprocess.run(
            ["powershell", "-NoProfile", "-Command",
             "[Console]::OutputEncoding=[Text.Encoding]::UTF8; Get-Clipboard -Raw"],
            capture_output=True, timeout=10, creationflags=NO_WINDOW,
        )
        return r.stdout.decode("utf-8", "replace").rstrip("\r\n")
    for cmd in (["pbpaste"], ["wl-paste", "-n"], ["xclip", "-selection", "clipboard", "-o"]):
        if shutil.which(cmd[0]):
            return subprocess.run(cmd, capture_output=True, timeout=10).stdout.decode("utf-8", "replace")
    raise RuntimeError("الحافظة غير مدعومة على هذا النظام")


def clipboard_set(text: str):
    if os.name == "nt":
        env = dict(os.environ, HL_CLIP=text)
        subprocess.run(["powershell", "-NoProfile", "-Command", "Set-Clipboard -Value $env:HL_CLIP"],
                       env=env, timeout=10, creationflags=NO_WINDOW, check=True)
        return
    for cmd in (["pbcopy"], ["wl-copy"], ["xclip", "-selection", "clipboard"]):
        if shutil.which(cmd[0]):
            subprocess.run(cmd, input=text.encode("utf-8"), timeout=10, check=True)
            return
    raise RuntimeError("الحافظة غير مدعومة على هذا النظام")


_PIL_STATE = {"tried_install": False}


def _ensure_pillow():
    try:
        from PIL import ImageGrab  # noqa: F401

        return True
    except Exception:
        if not _PIL_STATE["tried_install"]:
            _PIL_STATE["tried_install"] = True

            def bg():
                log_console("[..] تثبيت Pillow لعرض الشاشة...")
                subprocess.run([sys.executable, "-m", "pip", "install", "-q", "pillow"],
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                               creationflags=NO_WINDOW, check=False)

            threading.Thread(target=bg, daemon=True).start()
        return False


def screenshot_jpeg(width: int = 1280, quality: int = 60) -> bytes:
    if not _ensure_pillow():
        raise RuntimeError("عرض الشاشة يحتاج مكتبة Pillow — جاري تثبيتها تلقائياً، أعد المحاولة بعد قليل")
    from PIL import ImageGrab

    img = ImageGrab.grab()
    if img.mode != "RGB":
        img = img.convert("RGB")
    width = max(320, min(int(width), 2560))
    if img.width > width:
        img = img.resize((width, int(img.height * width / img.width)))
    buf = io.BytesIO()
    img.save(buf, "JPEG", quality=max(20, min(int(quality), 90)))
    return buf.getvalue()


class Shell:
    def __init__(self):
        self.cwd = str(Path.home())
        self.lock = threading.Lock()

    def run(self, cmd: str) -> dict:
        cmd = (cmd or "").strip()
        with self.lock:
            if not cmd:
                return {"output": "", "code": 0, "cwd": self.cwd}
            if cmd == "cd" or cmd.startswith("cd ") or cmd.startswith("cd\t"):
                target = cmd[2:].strip().strip('"') or str(Path.home())
                p = Path(os.path.expandvars(os.path.expanduser(target)))
                if not p.is_absolute():
                    p = Path(self.cwd) / p
                if p.is_dir():
                    self.cwd = str(p.resolve())
                    return {"output": "", "code": 0, "cwd": self.cwd}
                return {"output": f"المجلد غير موجود: {target}", "code": 1, "cwd": self.cwd}
            if os.name == "nt":
                argv = ["powershell", "-NoProfile", "-Command",
                        f"[Console]::OutputEncoding=[Text.Encoding]::UTF8; {cmd}"]
            else:
                argv = ["bash", "-lc", cmd]
            try:
                r = subprocess.run(argv, cwd=self.cwd, capture_output=True, timeout=120,
                                   creationflags=NO_WINDOW)
                out = (r.stdout + r.stderr).decode("utf-8", "replace")
                code = r.returncode
            except subprocess.TimeoutExpired:
                out, code = "انتهت المهلة (120 ثانية)", 124
            if len(out) > 20000:
                out = "…\n" + out[-20000:]
            return {"output": out, "code": code, "cwd": self.cwd}


def files_root() -> Path:
    return Path.home().resolve()


def safe_path(rel: str) -> Path:
    root = files_root()
    p = (root / (rel or "")).resolve()
    if p != root and root not in p.parents:
        raise PermissionError("المسار خارج مجلد المستخدم")
    return p


def list_dir(rel: str) -> dict:
    p = safe_path(rel)
    if not p.is_dir():
        raise FileNotFoundError("ليس مجلداً")
    items = []
    try:
        entries = list(os.scandir(p))
    except PermissionError:
        entries = []
    for e in entries:
        if e.name.startswith(".") or e.name in ("desktop.ini", "NTUSER.DAT"):
            continue
        try:
            is_dir = e.is_dir()
            st = e.stat()
            items.append({"name": e.name, "dir": is_dir, "size": 0 if is_dir else st.st_size,
                          "mtime": int(st.st_mtime)})
        except OSError:
            continue
    items.sort(key=lambda x: (not x["dir"], x["name"].lower()))
    rel_path = "" if p == files_root() else p.relative_to(files_root()).as_posix()
    return {"path": rel_path, "items": items[:2000], "root": str(files_root())}


def unique_path(folder: Path, name: str) -> Path:
    folder.mkdir(parents=True, exist_ok=True)
    name = "".join(c for c in name if c not in '<>:"/\\|?*\0').strip() or "file"
    target = folder / name
    stem, suf, i = target.stem, target.suffix, 1
    while target.exists():
        target = folder / f"{stem} ({i}){suf}"
        i += 1
    return target


def lan_ips() -> list[str]:
    ips = []
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("10.255.255.255", 1))
        ips.append(s.getsockname()[0])
        s.close()
    except Exception:
        pass
    try:
        for ip in socket.gethostbyname_ex(socket.gethostname())[2]:
            if ip not in ips and not ip.startswith("127."):
                ips.append(ip)
    except Exception:
        pass
    return ips or ["127.0.0.1"]


# ==============================================================================
#  جلسة Claude المشتركة (Anthropic Messages API عبر OpenRouter / DeepSeek)
# ==============================================================================

class AISession:
    def __init__(self, hub: "Hub"):
        self.hub = hub
        self.history: list[dict] = []   # رسائل API
        self.view: list[dict] = []      # ما يُعرض على الشاشتين
        self.busy = False
        self.lock = threading.Lock()
        cfg = load_suite_config()
        self.provider = cfg.get("provider", "openrouter")
        if self.provider not in BUILTIN_MODELS:
            self.provider = "openrouter"
        ids = [m["id"] for m in BUILTIN_MODELS[self.provider]]
        self.model = cfg.get("model") if cfg.get("model") in ids else ids[0]

    def models(self) -> dict:
        return {p: [{"id": m["id"], "label": m.get("label", m["id"])} for m in lst]
                for p, lst in BUILTIN_MODELS.items()}

    def snapshot(self) -> dict:
        return {"provider": self.provider, "model": self.model, "busy": self.busy,
                "view": self.view[-60:], "models": self.models(),
                "keys": {"openrouter": bool(api_key_for("openrouter")),
                         "deepseek": bool(api_key_for("deepseek"))}}

    def reset(self):
        with self.lock:
            self.history.clear()
            self.view.clear()
        self.hub.broadcast("ai", {"type": "reset"})

    def send(self, prompt: str, sender: str, provider: str | None = None, model: str | None = None):
        prompt = (prompt or "").strip()
        if not prompt:
            raise ValueError("اكتب رسالة")
        with self.lock:
            if self.busy:
                raise RuntimeError("Claude مشغول بالرد على الرسالة السابقة")
            if provider in BUILTIN_MODELS:
                self.provider = provider
            if model:
                self.model = model
            self.busy = True
            self.history.append({"role": "user", "content": prompt})
            self.view.append({"role": "user", "text": prompt, "from": sender, "ts": now_ms()})
            self.view.append({"role": "assistant", "text": "", "from": self.model, "ts": now_ms()})
        save_suite_config({"provider": self.provider, "model": self.model})
        self.hub.broadcast("ai", {"type": "user", "text": prompt, "from": sender,
                                  "provider": self.provider, "model": self.model})
        threading.Thread(target=self._worker, daemon=True).start()

    def _worker(self):
        provider, model = self.provider, self.model
        key = api_key_for(provider)
        text_parts: list[str] = []
        usage = {}
        try:
            if not key:
                raise RuntimeError(f"لا يوجد مفتاح API لـ {provider} — أضفه من لوحة الحاسوب (زر المفاتيح) أو عبر /keys في Claude Code")
            base = OPENROUTER_BASE_URL if provider == "openrouter" else DEEPSEEK_BASE_URL
            body = {
                "model": model, "max_tokens": 4096, "stream": True, "messages": self.history,
                "system": (f"You are Claude Code, connected through Hamza Link (designed by Hamza Alfarai) "
                           f"to the user's PC ({platform.system()}) and phone. Answer concisely; "
                           f"reply in the user's language."),
            }
            req = urllib.request.Request(
                base.rstrip("/") + "/v1/messages", data=json.dumps(body).encode("utf-8"), method="POST",
                headers={"content-type": "application/json", "x-api-key": key,
                         "authorization": f"Bearer {key}", "anthropic-version": "2023-06-01",
                         "HTTP-Referer": "https://github.com/hamza756-pory/free-clude-code",
                         "X-Title": "Hamza Link"},
            )
            with urllib.request.urlopen(req, timeout=300, context=ssl_context()) as resp:
                for raw in resp:
                    line = raw.decode("utf-8", "replace").strip()
                    if not line.startswith("data:"):
                        continue
                    data = line[5:].strip()
                    if data == "[DONE]":
                        break
                    try:
                        ev = json.loads(data)
                    except ValueError:
                        continue
                    t = ev.get("type")
                    if t == "content_block_delta" and ev.get("delta", {}).get("type") == "text_delta":
                        chunk = ev["delta"].get("text", "")
                        text_parts.append(chunk)
                        self.view[-1]["text"] += chunk
                        self.hub.broadcast("ai", {"type": "delta", "text": chunk})
                    elif t == "message_start":
                        usage.update(ev.get("message", {}).get("usage", {}) or {})
                    elif t == "message_delta":
                        usage.update(ev.get("usage", {}) or {})
                    elif t == "error":
                        raise RuntimeError(ev.get("error", {}).get("message", "API error"))
            full = "".join(text_parts)
            self.history.append({"role": "assistant", "content": full or "(empty)"})
            self.hub.broadcast("ai", {"type": "done", "usage": {
                "in": usage.get("input_tokens", 0), "out": usage.get("output_tokens", 0)}})
        except urllib.error.HTTPError as e:
            try:
                detail = json.loads(e.read().decode("utf-8", "replace"))
                msg = detail.get("error", {}).get("message") or str(detail)
            except Exception:
                msg = str(e)
            self._fail(f"HTTP {e.code}: {msg}")
        except Exception as e:
            self._fail(str(e))
        finally:
            self.busy = False

    def _fail(self, msg: str):
        if self.history and self.history[-1]["role"] == "user":
            self.history.pop()
        if self.view and self.view[-1]["role"] == "assistant":
            self.view[-1]["text"] += f"\n[خطأ] {msg}"
            self.view[-1]["error"] = True
        self.hub.broadcast("ai", {"type": "error", "text": msg})
        self.hub.log(f"Claude: {msg}", "err")


# ==============================================================================
#  المحور (Hub): العملاء المتصلون + الأحداث + الاقتران
# ==============================================================================

class Client:
    def __init__(self, role: str, token: str | None, name: str):
        self.id = secrets.token_hex(6)
        self.role = role
        self.token = token
        self.name = name
        self.q: queue.Queue = queue.Queue(maxsize=2000)
        self.since = now_ms()


class Hub:
    def __init__(self, store: Store, port: int):
        self.store = store
        self.port = port
        self.lock = threading.RLock()
        self.clients: dict[str, Client] = {}
        self.pin = self._new_pin()
        self.fail_log: dict[str, collections.deque] = {}
        self.history = collections.deque(maxlen=300)
        self.inbox = collections.deque(maxlen=200)       # من الهاتف إلى الحاسوب
        self.pending: dict[str, list] = {}               # عناصر للهاتف أثناء انقطاعه
        self.shared: dict[str, dict] = {}                # ملفات مشتركة /api/file/<id>
        self.stopping = False
        self.input = InputController()
        self.shell = Shell()
        self.ai = AISession(self)
        self.pc_name = platform.node() or "PC"

    @staticmethod
    def _new_pin() -> str:
        return f"{secrets.randbelow(10**6):06d}"

    # ---------------- العملاء ----------------
    def connect(self, role, token, name) -> Client:
        c = Client(role, token, name)
        with self.lock:
            self.clients[c.id] = c
            if role == "phone" and token in self.store.devices:
                self.store.devices[token]["last_seen"] = now_ms()
                for item in self.pending.pop(token, []):
                    c.q.put(("push", item))
        if role == "phone":
            self.log(f"📱 اتصل الهاتف: {name}", "ok")
            self.devices_changed()
        return c

    def disconnect(self, c: Client):
        with self.lock:
            self.clients.pop(c.id, None)
        if c.role == "phone":
            if c.token in self.store.devices:
                self.store.devices[c.token]["last_seen"] = now_ms()
                self.store.save()
            self.log(f"📴 انقطع الهاتف: {c.name}", "warn")
            self.devices_changed()

    def online_tokens(self) -> set:
        with self.lock:
            return {c.token for c in self.clients.values() if c.role == "phone"}

    def device_list(self) -> list:
        online = self.online_tokens()
        out = []
        for tok, d in self.store.devices.items():
            out.append({"id": tok[:12], "name": d.get("name", "Phone"), "online": tok in online,
                        "paired_at": d.get("paired_at"), "last_seen": d.get("last_seen"),
                        "ua": d.get("ua", "")})
        out.sort(key=lambda x: (not x["online"], -(x["last_seen"] or 0)))
        return out

    def token_from_id(self, short_id: str) -> str | None:
        for tok in self.store.devices:
            if tok[:12] == short_id:
                return tok
        return None

    # ---------------- الأحداث ----------------
    def _put(self, c: Client, event: str, data):
        try:
            c.q.put_nowait((event, data))
        except queue.Full:
            pass

    def broadcast(self, event: str, data, role: str | None = None):
        with self.lock:
            targets = [c for c in self.clients.values() if role is None or c.role == role]
        for c in targets:
            self._put(c, event, data)

    def to_phones(self, event: str, data, target: str | None = None, queue_offline=False) -> int:
        """target = معرّف مختصر لجهاز، أو None لكل الهواتف."""
        tok_filter = self.token_from_id(target) if target else None
        if target and not tok_filter:
            return 0
        with self.lock:
            targets = [c for c in self.clients.values()
                       if c.role == "phone" and (tok_filter is None or c.token == tok_filter)]
        for c in targets:
            self._put(c, event, data)
        if queue_offline:
            online = {c.token for c in targets}
            for tok in ([tok_filter] if tok_filter else list(self.store.devices)):
                if tok and tok not in online:
                    self.pending.setdefault(tok, []).append(data)
                    self.pending[tok] = self.pending[tok][-50:]
        return len(targets)

    def log(self, text: str, level: str = "info"):
        entry = {"ts": now_ms(), "text": text, "level": level}
        self.history.append(entry)
        self.broadcast("log", entry, role="desktop")
        log_console(time.strftime("%H:%M:%S"), text)

    def devices_changed(self):
        self.broadcast("devices", self.device_list(), role="desktop")

    def perms_changed(self):
        self.broadcast("perms", self.store.perms)

    def add_inbox(self, item: dict, device_name: str):
        item = {"id": secrets.token_hex(6), "ts": now_ms(), "from": device_name, **item}
        self.inbox.append(item)
        self.broadcast("inbox", item, role="desktop")
        return item

    # ---------------- الاقتران ----------------
    def pair(self, pin: str, name: str, ua: str, ip: str) -> str:
        dq = self.fail_log.setdefault(ip, collections.deque(maxlen=10))
        recent = [t for t in dq if time.time() - t < 60]
        if len(recent) >= 5:
            raise PermissionError("محاولات كثيرة — انتظر دقيقة")
        if not secrets.compare_digest(str(pin).strip(), self.pin):
            dq.append(time.time())
            self.log(f"⚠️ رمز اقتران خاطئ من {ip}", "warn")
            raise PermissionError("رمز الاقتران غير صحيح")
        token = secrets.token_urlsafe(32)
        with self.lock:
            self.store.devices[token] = {"name": (name or "Phone")[:40], "paired_at": now_ms(),
                                         "last_seen": now_ms(), "ua": ua[:200]}
            self.pin = self._new_pin()  # رمز لمرة واحدة
        self.store.save()
        self.broadcast("pin", {"pin": self.pin}, role="desktop")
        self.log(f"🔗 تم اقتران جهاز جديد: {name}", "ok")
        self.devices_changed()
        return token

    def revoke(self, token: str):
        with self.lock:
            dev = self.store.devices.pop(token, None)
            targets = [c for c in self.clients.values() if c.token == token]
        for c in targets:
            self._put(c, "revoked", {})
            self._put(c, None, None)
        self.store.save()
        if dev:
            self.log(f"⛔ تم إلغاء اقتران: {dev.get('name')}", "warn")
        self.devices_changed()

    def share_file(self, path: Path, name: str, temp=False) -> dict:
        fid = secrets.token_urlsafe(12)
        mime = mimetypes.guess_type(name)[0] or "application/octet-stream"
        self.shared[fid] = {"path": str(path), "name": name, "mime": mime, "temp": temp,
                            "size": path.stat().st_size}
        return {"file_id": fid, "name": name, "mime": mime, "size": self.shared[fid]["size"]}

    def desktop_state(self) -> dict:
        return {
            "app": APP_NAME, "version": APP_VERSION, "author": APP_AUTHOR,
            "pc": self.pc_name, "os": f"{platform.system()} {platform.release()}",
            "pin": self.pin, "port": self.port, "ips": lan_ips(),
            "devices": self.device_list(), "perms": self.store.perms,
            "log": list(self.history)[-120:], "inbox": list(self.inbox)[-60:],
            "ai": self.ai.snapshot(), "inbox_dir": str(INBOX_DIR),
            "suite": suite is not None,
        }

    def phone_state(self, c: Client) -> dict:
        return {"pc": self.pc_name, "os": f"{platform.system()} {platform.release()}",
                "perms": self.store.perms, "ai": self.ai.snapshot(), "cwd": self.shell.cwd,
                "device": c.name, "author": APP_AUTHOR, "version": APP_VERSION}


# ==============================================================================
#  خادم HTTP
# ==============================================================================

HUB: Hub | None = None
ALLOW_REMOTE_DESKTOP = False


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = f"HamzaLink/{APP_VERSION}"

    def log_message(self, fmt, *args):  # هدوء في الطرفية
        pass

    # ---------------- أدوات ----------------
    @property
    def hub(self) -> Hub:
        return HUB  # type: ignore

    def _query(self) -> dict:
        return {k: v[0] for k, v in urllib.parse.parse_qs(urllib.parse.urlsplit(self.path).query).items()}

    def _route(self) -> str:
        return urllib.parse.unquote(urllib.parse.urlsplit(self.path).path)

    def _is_local(self) -> bool:
        """طلبات لوحة الحاسوب: من الحاسوب نفسه فقط + حماية من CSRF و DNS rebinding."""
        if ALLOW_REMOTE_DESKTOP:
            return True
        if self.client_address[0] not in ("127.0.0.1", "::1", "::ffff:127.0.0.1"):
            return False
        host = (self.headers.get("Host") or "").lower()
        hostname = host.rsplit(":", 1)[0] if not host.startswith("[") else host.split("]")[0] + "]"
        if hostname not in ("127.0.0.1", "localhost", "[::1]"):
            return False
        origin = self.headers.get("Origin")
        if origin and origin.lower() != f"http://{host}":
            return False
        return True

    def _send(self, code: int, body: bytes, ctype: str, extra: dict | None = None):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def _json(self, data, code=200):
        self._send(code, json.dumps(data, ensure_ascii=False).encode("utf-8"), "application/json; charset=utf-8")

    def _err(self, msg: str, code=400):
        self._json({"ok": False, "error": msg}, code)

    def _read_json(self) -> dict:
        n = int(self.headers.get("Content-Length") or 0)
        if n > 5 * 1024 * 1024:
            raise ValueError("الطلب كبير جداً")
        raw = self.rfile.read(n) if n else b"{}"
        return json.loads(raw.decode("utf-8") or "{}")

    def _phone_token(self) -> str | None:
        tok = self.headers.get("X-Token") or self._query().get("token")
        if tok and tok in self.hub.store.devices:
            return tok
        return None

    def _static(self, rel: str):
        p = (WEB_DIR / rel).resolve()
        if WEB_DIR.resolve() not in p.parents or not p.is_file():
            return self._err("not found", 404)
        ctype = mimetypes.guess_type(p.name)[0] or "application/octet-stream"
        if p.suffix in (".html", ".js", ".css", ".svg", ".webmanifest", ".json"):
            ctype += "; charset=utf-8"
        if p.suffix == ".webmanifest":
            ctype = "application/manifest+json; charset=utf-8"
        self._send(200, p.read_bytes(), ctype)

    def _stream_file(self, path: Path, name: str, mime: str, inline=False):
        size = path.stat().st_size
        self.send_response(200)
        self.send_header("Content-Type", mime)
        self.send_header("Content-Length", str(size))
        disp = "inline" if inline else "attachment"
        self.send_header("Content-Disposition", f"{disp}; filename*=UTF-8''{urllib.parse.quote(name)}")
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        with open(path, "rb") as f:
            shutil.copyfileobj(f, self.wfile, 256 * 1024)

    # ---------------- GET ----------------
    def do_HEAD(self):
        self.do_GET()

    def do_GET(self):
        try:
            route = self._route()
            if route in ("/", "/phone", "/index.html"):
                return self._static("phone.html")
            if route == "/desktop":
                if not self._is_local():
                    return self._err("لوحة الحاسوب متاحة من الحاسوب نفسه فقط", 403)
                return self._static("desktop.html")
            if route == "/manifest.webmanifest":
                return self._static("manifest.webmanifest")
            if route.startswith("/static/"):
                return self._static(route[len("/static/"):])
            if route == "/api/events":
                return self._events()
            if route == "/api/screen":
                return self._screen()
            if route.startswith("/api/file/"):
                return self._shared_file(route[len("/api/file/"):])
            if route == "/api/fs/download":
                return self._fs_download()
            if route == "/api/health":
                return self._json({"ok": True, "app": APP_NAME, "version": APP_VERSION})
            self._err("not found", 404)
        except (BrokenPipeError, ConnectionResetError):
            pass
        except Exception as e:
            try:
                self._err(str(e), 500)
            except Exception:
                pass

    def _events(self):
        q = self._query()
        role = q.get("role", "phone")
        if role == "desktop":
            if not self._is_local():
                return self._err("forbidden", 403)
            token, name = None, "desktop"
        else:
            token = self._phone_token()
            if not token:
                return self._err("unpaired", 401)
            name = self.hub.store.devices[token].get("name", "Phone")
        c = self.hub.connect(role, token, name)
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream; charset=utf-8")
        self.send_header("Cache-Control", "no-cache, no-transform")
        self.send_header("X-Accel-Buffering", "no")
        self.send_header("Connection", "close")
        self.end_headers()
        self.close_connection = True

        def emit(event, data):
            payload = json.dumps(data, ensure_ascii=False)
            self.wfile.write(f"event: {event}\ndata: {payload}\n\n".encode("utf-8"))
            self.wfile.flush()

        try:
            self.wfile.write(b"retry: 2000\n\n")
            emit("hello", self.hub.desktop_state() if role == "desktop" else self.hub.phone_state(c))
            while not self.hub.stopping:
                try:
                    event, data = c.q.get(timeout=15)
                except queue.Empty:
                    self.wfile.write(b": ping\n\n")
                    self.wfile.flush()
                    continue
                if event is None:
                    break
                emit(event, data)
        except (BrokenPipeError, ConnectionResetError, OSError):
            pass
        finally:
            self.hub.disconnect(c)

    def _screen(self):
        if not (self._phone_token() or self._is_local()):
            return self._err("unpaired", 401)
        if not self.hub.store.perms.get("screen"):
            return self._err("عرض الشاشة معطّل من الحاسوب", 403)
        q = self._query()
        try:
            data = screenshot_jpeg(int(q.get("w", 1280)), int(q.get("q", 60)))
        except Exception as e:
            return self._err(str(e), 503)
        self._send(200, data, "image/jpeg")

    def _shared_file(self, fid: str):
        info = self.hub.shared.get(fid)
        if not info:
            return self._err("الملف غير متاح", 404)
        if not (self._phone_token() or self._is_local()):
            return self._err("unpaired", 401)
        inline = self._query().get("inline") == "1"
        self._stream_file(Path(info["path"]), info["name"], info["mime"], inline)

    def _fs_download(self):
        tok = self._phone_token()
        if not tok:
            return self._err("unpaired", 401)
        if not self.hub.store.perms.get("files"):
            return self._err("الملفات معطّلة من الحاسوب", 403)
        p = safe_path(self._query().get("path", ""))
        if not p.is_file():
            return self._err("الملف غير موجود", 404)
        self.hub.log(f"⬇️ {self.hub.store.devices[tok].get('name')} نزّل: {p.name}")
        self._stream_file(p, p.name, mimetypes.guess_type(p.name)[0] or "application/octet-stream")

    # ---------------- POST ----------------
    def do_POST(self):
        try:
            route = self._route()
            if route == "/api/pair":
                body = self._read_json()
                token = self.hub.pair(body.get("pin", ""), body.get("name", "Phone"),
                                      self.headers.get("User-Agent", ""), self.client_address[0])
                return self._json({"ok": True, "token": token, "pc": self.hub.pc_name})
            if route == "/api/phone":
                tok = self._phone_token()
                if not tok:
                    return self._err("unpaired", 401)
                return self._json(self._phone_action(tok, self._read_json()))
            if route == "/api/desktop":
                if not self._is_local() or "application/json" not in (self.headers.get("Content-Type") or ""):
                    return self._err("forbidden", 403)
                return self._json(self._desktop_action(self._read_json()))
            if route == "/api/upload":
                return self._upload()
            self._err("not found", 404)
        except PermissionError as e:
            self._err(str(e), 403)
        except (BrokenPipeError, ConnectionResetError):
            pass
        except Exception as e:
            try:
                self._err(str(e), 400)
            except Exception:
                pass

    def _need(self, perm: str):
        if not self.hub.store.perms.get(perm):
            names = {"input": "التحكم بالماوس ولوحة المفاتيح", "screen": "عرض الشاشة", "files": "الملفات",
                     "clipboard": "الحافظة", "ai": "Claude", "shell": "الطرفية"}
            raise PermissionError(f"«{names.get(perm, perm)}» معطّل — فعّله من لوحة الحاسوب")

    def _phone_action(self, tok: str, b: dict) -> dict:
        hub = self.hub
        dev = hub.store.devices[tok]
        name = dev.get("name", "Phone")
        a = b.get("action")
        inp = hub.input

        if a == "ping":
            return {"ok": True, "ts": now_ms()}
        # --- الماوس ولوحة المفاتيح ---
        if a in ("mouse_move", "mouse_abs", "mouse_button", "scroll", "type", "key", "media", "lock"):
            self._need("input")
            if a == "mouse_move":
                inp.move(float(b.get("dx", 0)), float(b.get("dy", 0)))
            elif a == "mouse_abs":
                inp.move_abs(float(b.get("x", 0)), float(b.get("y", 0)))
                if b.get("click"):
                    inp.button(b.get("button", "left"), b.get("click"))
            elif a == "mouse_button":
                inp.button(b.get("button", "left"), b.get("type", "click"))
            elif a == "scroll":
                inp.scroll(float(b.get("dy", 0)), float(b.get("dx", 0)))
            elif a == "type":
                inp.type_text(str(b.get("text", ""))[:5000])
                if b.get("enter"):
                    inp.key("enter")
            elif a == "key":
                inp.key(str(b.get("key", "")), b.get("mods") or [])
            elif a == "media":
                inp.key(str(b.get("name", "")))
            elif a == "lock":
                inp.lock_screen()
                hub.log(f"🔒 {name} قفل الحاسوب", "warn")
            return {"ok": True}
        # --- الحافظة والروابط ---
        if a == "clip_get":
            self._need("clipboard")
            return {"ok": True, "text": clipboard_get()}
        if a == "clip_set":
            self._need("clipboard")
            clipboard_set(str(b.get("text", "")))
            hub.log(f"📋 {name} نسخ نصاً إلى حافظة الحاسوب")
            return {"ok": True}
        if a == "open_url":
            self._need("clipboard")
            url = str(b.get("url", "")).strip()
            if not url.lower().startswith(("http://", "https://")):
                url = "https://" + url
            webbrowser.open(url)
            hub.log(f"🌐 {name} فتح رابطاً على الحاسوب: {url}")
            return {"ok": True}
        # --- الطرفية ---
        if a == "shell":
            self._need("shell")
            cmd = str(b.get("cmd", ""))
            hub.log(f"💻 {name}: {cmd[:120]}", "warn")
            res = hub.shell.run(cmd)
            hub.broadcast("shell", {"from": name, "cmd": cmd, **res}, role="desktop")
            return {"ok": True, **res}
        # --- الملفات ---
        if a == "fs_list":
            self._need("files")
            return {"ok": True, **list_dir(str(b.get("path", "")))}
        # --- Claude ---
        if a == "ai_send":
            self._need("ai")
            hub.ai.send(str(b.get("prompt", "")), f"📱 {name}", b.get("provider"), b.get("model"))
            return {"ok": True}
        if a == "ai_reset":
            self._need("ai")
            hub.ai.reset()
            return {"ok": True}
        # --- رسائل وردود للحاسوب ---
        if a == "send_text":
            text = str(b.get("text", ""))[:20000]
            hub.add_inbox({"kind": "text", "text": text}, name)
            hub.log(f"💬 رسالة من {name}")
            return {"ok": True}
        if a == "reply":
            kind = b.get("kind")
            if kind == "info":
                hub.add_inbox({"kind": "info", "info": b.get("info", {})}, name)
            elif kind == "clipboard":
                hub.add_inbox({"kind": "clipboard", "text": str(b.get("text", ""))[:20000]}, name)
            elif kind == "ack":
                hub.log(f"✓ {name}: {str(b.get('text', ''))[:120]}", "ok")
            return {"ok": True}
        raise ValueError(f"إجراء غير معروف: {a}")

    def _desktop_action(self, b: dict) -> dict:
        hub = self.hub
        a = b.get("action")
        if a == "state":
            return {"ok": True, **hub.desktop_state()}
        if a == "perm":
            name = b.get("name")
            if name in DEFAULT_PERMS:
                hub.store.perms[name] = bool(b.get("value"))
                hub.store.save()
                hub.perms_changed()
                hub.log(f"⚙️ {name} = {'مفعّل' if hub.store.perms[name] else 'معطّل'}")
            return {"ok": True, "perms": hub.store.perms}
        if a == "new_pin":
            hub.pin = hub._new_pin()
            hub.broadcast("pin", {"pin": hub.pin}, role="desktop")
            return {"ok": True, "pin": hub.pin}
        if a == "revoke":
            tok = hub.token_from_id(str(b.get("device", "")))
            if tok:
                hub.revoke(tok)
            return {"ok": True}
        if a == "push":
            kind = b.get("kind")
            allowed = {"text", "link", "speak", "ring", "stop_ring", "vibrate", "photo_request",
                       "clip_request", "info_request", "toast"}
            if kind not in allowed:
                raise ValueError("نوع غير معروف")
            item = {"id": secrets.token_hex(6), "ts": now_ms(), "kind": kind,
                    "text": str(b.get("text", ""))[:20000]}
            queue_offline = kind in ("text", "link")
            n = hub.to_phones("push", item, b.get("target") or None, queue_offline=queue_offline)
            labels = {"text": "نص", "link": "رابط", "speak": "نطق", "ring": "رنين", "stop_ring": "إيقاف الرنين",
                      "vibrate": "اهتزاز", "photo_request": "طلب صورة", "clip_request": "طلب الحافظة",
                      "info_request": "طلب معلومات", "toast": "إشعار"}
            hub.log(f"📤 إلى الهاتف ({n}): {labels.get(kind, kind)}" + (" — سيُسلَّم عند الاتصال" if not n and queue_offline else ""))
            return {"ok": True, "delivered": n}
        if a == "keys":
            data = {}
            if b.get("openrouter") is not None:
                data["openrouter_key"] = str(b["openrouter"]).strip()
            if b.get("deepseek") is not None:
                data["deepseek_key"] = str(b["deepseek"]).strip()
            save_suite_config(data)
            hub.broadcast("ai", {"type": "state", **hub.ai.snapshot()})
            hub.log("🔑 تم تحديث مفاتيح API")
            return {"ok": True}
        if a == "ai_send":
            hub.ai.send(str(b.get("prompt", "")), "🖥️ الحاسوب", b.get("provider"), b.get("model"))
            return {"ok": True}
        if a == "ai_reset":
            hub.ai.reset()
            return {"ok": True}
        if a == "open_inbox":
            INBOX_DIR.mkdir(parents=True, exist_ok=True)
            if os.name == "nt":
                os.startfile(str(INBOX_DIR))  # type: ignore[attr-defined]
            elif sys.platform == "darwin":
                subprocess.Popen(["open", str(INBOX_DIR)])
            else:
                subprocess.Popen(["xdg-open", str(INBOX_DIR)])
            return {"ok": True}
        if a == "copy":
            clipboard_set(str(b.get("text", "")))
            return {"ok": True}
        raise ValueError(f"إجراء غير معروف: {a}")

    def _upload(self):
        q = self._query()
        role = q.get("role", "phone")
        name = urllib.parse.unquote(q.get("name", "file")) or "file"
        n = int(self.headers.get("Content-Length") or 0)
        if n <= 0 or n > MAX_UPLOAD:
            return self._err("حجم غير صالح")
        if role == "desktop":
            if not self._is_local():
                return self._err("forbidden", 403)
            tmpdir = Path(tempfile.gettempdir()) / "hamza_link_share"
            target = unique_path(tmpdir, name)
        else:
            tok = self._phone_token()
            if not tok:
                return self._err("unpaired", 401)
            if not self.hub.store.perms.get("files") and q.get("kind") != "photo":
                return self._err("الملفات معطّلة من الحاسوب", 403)
            target = unique_path(INBOX_DIR, name)
        remaining = n
        with open(target, "wb") as f:
            while remaining > 0:
                chunk = self.rfile.read(min(remaining, 1024 * 1024))
                if not chunk:
                    break
                f.write(chunk)
                remaining -= len(chunk)
        if remaining:
            target.unlink(missing_ok=True)
            return self._err("انقطع الرفع")
        info = self.hub.share_file(target, target.name, temp=(role == "desktop"))
        if role == "desktop":
            item = {"id": secrets.token_hex(6), "ts": now_ms(), "kind": "file", **info}
            delivered = self.hub.to_phones("push", item, q.get("target") or None, queue_offline=True)
            self.hub.log(f"📤 ملف إلى الهاتف ({delivered}): {target.name}")
            return self._json({"ok": True, "delivered": delivered, **info})
        dev_name = self.hub.store.devices[tok].get("name", "Phone")
        kind = "photo" if q.get("kind") == "photo" else "file"
        self.hub.add_inbox({"kind": kind, "path": str(target), **info}, dev_name)
        self.hub.log(f"📥 {dev_name} أرسل {'صورة' if kind == 'photo' else 'ملفاً'}: {target.name}", "ok")
        return self._json({"ok": True, "saved": str(target)})


# ==============================================================================
#  التشغيل
# ==============================================================================

def open_desktop_panel(url: str):
    if os.name == "nt":
        # نافذة تطبيق مستقلة عبر Edge / Chrome إن وُجدا
        for exe in ("msedge", "chrome"):
            try:
                subprocess.Popen(f'start "" {exe} --app="{url}" --window-size=1360,880', shell=True,
                                 stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                return
            except Exception:
                continue
    webbrowser.open(url)


def main(argv=None):
    global HUB, ALLOW_REMOTE_DESKTOP
    ap = argparse.ArgumentParser(description=f"{APP_NAME} — تصميم {APP_AUTHOR}")
    ap.add_argument("--host", default="0.0.0.0")
    ap.add_argument("--port", type=int, default=8765)
    ap.add_argument("--no-browser", action="store_true", help="لا تفتح لوحة الحاسوب تلقائياً")
    ap.add_argument("--demo", action="store_true",
                    help="(للتجربة فقط) السماح بفتح لوحة الحاسوب من جهاز آخر")
    args = ap.parse_args(argv)
    ALLOW_REMOTE_DESKTOP = args.demo

    for stream in (sys.stdout, sys.stderr):
        if stream is not None and hasattr(stream, "reconfigure"):
            try:
                stream.reconfigure(encoding="utf-8", errors="replace")
            except Exception:
                pass

    mimetypes.add_type("application/manifest+json", ".webmanifest")
    mimetypes.add_type("image/svg+xml", ".svg")
    mimetypes.add_type("text/javascript", ".js")

    port = args.port
    server = None
    for p in range(port, port + 20):
        try:
            server = ThreadingHTTPServer((args.host, p), Handler)
            port = p
            break
        except OSError:
            continue
    if server is None:
        raise SystemExit("تعذر فتح أي منفذ")
    server.daemon_threads = True

    HUB = Hub(Store(STATE_PATH), port)
    ips = lan_ips()
    local_url = f"http://127.0.0.1:{port}/desktop"
    log_console("=" * 62)
    log_console(f"  {APP_NAME} {APP_VERSION} — تكامل الحاسوب والهاتف — تصميم {APP_AUTHOR}")
    log_console("=" * 62)
    log_console(f"  لوحة الحاسوب : {local_url}")
    for ip in ips:
        log_console(f"  رابط الهاتف  : http://{ip}:{port}/   (نفس شبكة Wi-Fi)")
    log_console(f"  رمز الاقتران : {HUB.pin}")
    log_console("  Ctrl+C للإيقاف")
    log_console("=" * 62)
    HUB.log(f"🚀 بدأ {APP_NAME} على المنفذ {port}", "ok")
    _ensure_pillow()

    if not args.no_browser:
        threading.Timer(0.8, open_desktop_panel, args=(local_url,)).start()
    try:
        server.serve_forever(poll_interval=0.5)
    except KeyboardInterrupt:
        pass
    finally:
        HUB.stopping = True
        server.server_close()


if __name__ == "__main__":
    main()
