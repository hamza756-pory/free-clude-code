# -*- coding: utf-8 -*-
"""
Claude Code Desktop & Installer Suite (All-in-One)
- Generates pixel-perfect mascot icon (.ico / .png)
- Pre-downloads dependencies & official Claude Code binary (claude.exe)
- Self-compiles into ClaudeCode.exe and ClaudeCode_Setup.exe
- Setup Wizard: Choose install location + Shortcuts + PATH + Auto-launch after install
- Authentic Claude Code v2.1.70 UI + Pre-configured OpenRouter (Strongest Models) & DeepSeek
- Live Model/Provider Switcher + Authentic Bottom Status Line Token & Cost Meter

تصميم: Hamza Alfarai  |  Designed by Hamza Alfarai
"""

import hashlib
import json
import multiprocessing
import os
import platform
import re
import shutil
import ssl
import subprocess
import sys
import threading
import urllib.error
import urllib.request
from pathlib import Path

APP_VERSION = "v2.1.70"
APP_TITLE = "Claude Code"
APP_AUTHOR = "Hamza Alfarai"
APP_CREDIT = f"تصميم {APP_AUTHOR} — Designed by {APP_AUTHOR}"

# مفاتيح API — لا تضع مفاتيحك الحقيقية داخل الكود المرفوع على GitHub!
# تُقرأ المفاتيح من متغيرات البيئة، أو أدخلها من داخل البرنامج عبر الأمر /keys
# (تُحفظ محلياً في ~/.claude_code_gui_config.json على جهازك فقط).
DEFAULT_OPENROUTER_KEY = os.environ.get("OPENROUTER_API_KEY", "")
DEFAULT_DEEPSEEK_KEY = os.environ.get("DEEPSEEK_API_KEY", "")

OPENROUTER_BASE_URL = "https://openrouter.ai/api"
DEEPSEEK_BASE_URL = "https://api.deepseek.com/anthropic"

# كتالوج النماذج الافتراضي (مرتب من الأقوى للأسرع) مع الأسعار لكل مليون توكن وحجم السياق
BUILTIN_MODELS = {
    "openrouter": [
        {
            "id": "anthropic/claude-fable-5.2",
            "label": "Claude Fable 5.2 (الأقوى على الإطلاق)",
            "in_price": 8.00,
            "out_price": 40.00,
            "cache_price": 0.20,
            "context": 1_000_000,
        },
        {
            "id": "anthropic/claude-opus-5.5",
            "label": "Claude Opus 5.5 (أقوى نموذج برمجي يومي)",
            "in_price": 4.00,
            "out_price": 20.00,
            "cache_price": 0.20,
            "context": 1_000_000,
        },
        {
            "id": "anthropic/claude-sonnet-5.5",
            "label": "Claude Sonnet 5.5 (سريع وذكي)",
            "in_price": 3.00,
            "out_price": 15.00,
            "cache_price": 0.30,
            "context": 1_000_000,
        },
        {
            "id": "anthropic/claude-haiku-5.5",
            "label": "Claude Haiku 5.5 (فائق السرعة واقتصادي)",
            "in_price": 0.10,
            "out_price": 0.50,
            "cache_price": 0.01,
            "context": 1_000_000,
        },
        {
            "id": "~anthropic/claude-opus-latest",
            "label": "Claude Opus Latest (تلقائي للأحدث)",
            "in_price": 4.00,
            "out_price": 20.00,
            "cache_price": 0.20,
            "context": 1_000_000,
        },
        {
            "id": "~anthropic/claude-fable-latest",
            "label": "Claude Fable Latest (تلقائي للأحدث)",
            "in_price": 8.00,
            "out_price": 40.00,
            "cache_price": 0.20,
            "context": 1_000_000,
        },
    ],
    "deepseek": [
        {
            "id": "deepseek-flash",
            "label": "DeepSeek-V4.1-Flash (الأقوى والأحدث من DeepSeek)",
            "in_price": 0.14,
            "out_price": 0.28,
            "cache_price": 0.0028,
            "context": 1_000_000,
        },
        {
            "id": "deepseek-v4-pro",
            "label": "DeepSeek-V4-Pro",
            "in_price": 0.435,
            "out_price": 0.87,
            "cache_price": 0.003625,
            "context": 1_000_000,
        },
        {
            "id": "deepseek-reasoner",
            "label": "DeepSeek Reasoner (تفكير عميق)",
            "in_price": 0.28,
            "out_price": 0.42,
            "cache_price": 0.028,
            "context": 128_000,
        },
        {
            "id": "deepseek-chat",
            "label": "DeepSeek Chat (محادثة سريعة)",
            "in_price": 0.28,
            "out_price": 0.42,
            "cache_price": 0.028,
            "context": 128_000,
        },
    ],
}

# ألوان واجهة Windows PowerShell / Claude Code المطابقة للصورة
BG_DARK = "#1C1924"
BG_PANEL = "#24202E"
BG_INPUT = "#15121B"
BORDER_DIM = "#383347"
FG_CORAL = "#DE8B8C"
FG_MASCOT_ICON = (217, 119, 87, 255)  # #D97757 (الصورة الثانية)
FG_WHITE = "#E8E6F0"
FG_GRAY = "#7C7987"
FG_DIM = "#5A5666"
FG_BLUE = "#7AA2F7"
FG_GREEN = "#8CE09A"
FG_WARN = "#E5C07B"
FG_ERR = "#F7768E"
FG_MAGENTA = "#C695E6"
BTN_ACCENT = "#D97757"
BTN_ACCENT_HOVER = "#E58768"

# شبكة البكسل الدقيقة للشعار (16x16)
MASCOT_GRID = [
    "................",
    "................",
    "................",
    "..CCCCCCCCCCCC..",
    "..CCCCCCCCCCCC..",
    "..CC.CCCCCC.CC..",
    "..CC.CCCCCC.CC..",
    "CCCCCCCCCCCCCCCC",
    "CCCCCCCCCCCCCCCC",
    "..CCCCCCCCCCCC..",
    "..CCCCCCCCCCCC..",
    "...C.C....C.C...",
    "...C.C....C.C...",
    "................",
    "................",
    "................",
]

# شبكة الهلال داخل السماء الليلية
MOON_GRID = [
    "    WWMMDDDD  ",
    "  WWWWMMDDDD  ",
    " WWWWMMDD     ",
    " WWWWWMD      ",
    " WWWWWMD      ",
    " WWWWWMD      ",
    " WWWWWMD      ",
    "  WWWWMMDD  W ",
    "   WWWWWMMMMD ",
    "     WWMMDDD  ",
]


def safe_print(*args, **kwargs):
    if sys.stdout is not None:
        try:
            print(*args, **kwargs)
        except UnicodeEncodeError:
            text = " ".join(str(a) for a in args)
            print(text.encode("ascii", "replace").decode("ascii"), **kwargs)


def configure_console_utf8():
    for stream in (sys.stdout, sys.stderr):
        if stream is not None and hasattr(stream, "reconfigure"):
            try:
                stream.reconfigure(encoding="utf-8", errors="replace")
            except Exception:
                pass


def get_bundle_root() -> Path:
    if getattr(sys, "frozen", False):
        return Path(getattr(sys, "_MEIPASS", Path(sys.executable).parent))
    return Path(__file__).resolve().parent


def get_asset_path(filename: str) -> Path:
    root = get_bundle_root()
    candidates = [
        root / "assets" / filename,
        root / "payload" / filename,
        root / "build_cache" / "payload" / filename,
        Path(sys.executable).parent / filename if getattr(sys, "frozen", False) else root / filename,
    ]
    for p in candidates:
        if p.exists():
            return p
    return candidates[0]


def get_config_path() -> Path:
    return Path.home() / ".claude_code_gui_config.json"


def load_config() -> dict:
    cfg_path = get_config_path()
    if cfg_path.exists():
        try:
            return json.loads(cfg_path.read_text(encoding="utf-8"))
        except Exception:
            pass
    return {}


def save_config(data: dict):
    cfg_path = get_config_path()
    try:
        existing = load_config()
        existing.update(data)
        cfg_path.write_text(json.dumps(existing, indent=2, ensure_ascii=False), encoding="utf-8")
    except Exception:
        pass


def enable_windows_dark_titlebar(win):
    if os.name != "nt":
        return
    try:
        import ctypes
        win.update_idletasks()
        hwnd = ctypes.windll.user32.GetParent(win.winfo_id())
        value = ctypes.c_int(1)
        ctypes.windll.dwmapi.DwmSetWindowAttribute(
            hwnd, 20, ctypes.byref(value), ctypes.sizeof(value)
        )
    except Exception:
        pass


def set_window_icon(win):
    try:
        ico_path = get_asset_path("claude_mascot.ico")
        if os.name == "nt" and ico_path.exists():
            win.iconbitmap(default=str(ico_path))
    except Exception:
        pass
    try:
        png_path = get_asset_path("claude_mascot.png")
        if png_path.exists():
            import tkinter as tk
            img = tk.PhotoImage(file=str(png_path))
            win._icon_ref = img
            win.iconphoto(True, img)
    except Exception:
        pass


def find_official_claude_exe() -> Path | None:
    exe_name = "claude.exe" if os.name == "nt" else "claude"
    candidates = []
    if getattr(sys, "frozen", False):
        candidates.append(Path(sys.executable).parent / exe_name)
    candidates.extend([
        Path.cwd() / exe_name,
        Path(__file__).resolve().parent / "build_cache" / "payload" / exe_name,
        Path.home() / ".local" / "bin" / exe_name,
    ])
    for c in candidates:
        if c.exists() and c.is_file():
            return c
    which_path = shutil.which("claude")
    if which_path:
        return Path(which_path)
    return None


# ==============================================================================
#  Hamza Link — تكامل الحاسوب والهاتف (/phone)
# ==============================================================================

HAMZA_LINK_PORT = 8765


def find_hamza_link_script() -> Path | None:
    roots = [get_bundle_root(), Path(__file__).resolve().parent, Path.cwd()]
    if getattr(sys, "frozen", False):
        roots.insert(0, Path(sys.executable).parent)
    for r in roots:
        p = r / "hamza_link" / "hamza_link.py"
        if p.exists():
            return p
    return None


def launch_hamza_link() -> str:
    """يشغّل Hamza Link (أو يفتح لوحته إن كان يعمل) ويعيد رسالة للمستخدم."""
    import webbrowser

    panel = f"http://127.0.0.1:{HAMZA_LINK_PORT}/desktop"
    try:
        with urllib.request.urlopen(f"http://127.0.0.1:{HAMZA_LINK_PORT}/api/health", timeout=1.5) as r:
            if r.status == 200:
                webbrowser.open(panel)
                return f"Hamza Link يعمل بالفعل — تم فتح لوحة الحاسوب: {panel}"
    except Exception:
        pass
    script = find_hamza_link_script()
    if not script:
        return "لم يتم العثور على hamza_link/hamza_link.py بجانب البرنامج."
    if getattr(sys, "frozen", False):
        py = shutil.which("py") or shutil.which("python") or shutil.which("python3")
        if not py:
            return "Hamza Link يحتاج Python — شغّل install_python.bat أولاً."
    else:
        py = sys.executable
    flags = getattr(subprocess, "CREATE_NEW_CONSOLE", 0) if os.name == "nt" else 0
    subprocess.Popen([py, str(script), "--port", str(HAMZA_LINK_PORT)], cwd=str(script.parent), creationflags=flags)
    return f"تم تشغيل Hamza Link — امسح رمز QR من لوحة الحاسوب بهاتفك ({panel})"


def get_ssl_context():
    try:
        import certifi
        return ssl.create_default_context(cafile=certifi.where())
    except Exception:
        return ssl.create_default_context()


def format_tokens(n: int) -> str:
    if n >= 1_000_000:
        return f"{n / 1_000_000:.2f}M"
    if n >= 1_000:
        return f"{n / 1_000:.1f}K"
    return str(n)


# ==============================================================================
#  المرحلة 1: توليد الشعار + التحميل المسبق + التحويل الذاتي إلى EXE
# ==============================================================================

def ensure_pip_packages():
    required = [
        ("PIL", "pillow"),
        ("PyInstaller", "pyinstaller"),
        ("anthropic", "anthropic"),
        ("certifi", "certifi"),
    ]
    for mod_name, pip_name in required:
        try:
            __import__(mod_name)
            safe_print(f"  [OK] المكتبة متوفرة: {pip_name}")
        except ImportError:
            safe_print(f"  [..] جاري تحميل وتثبيت {pip_name} ...")
            subprocess.check_call(
                [sys.executable, "-m", "pip", "install", "--upgrade", pip_name],
            )
            safe_print(f"  [OK] تم تثبيت {pip_name}")


def generate_mascot_assets(out_dir: Path) -> tuple[Path, Path]:
    from PIL import Image

    out_dir.mkdir(parents=True, exist_ok=True)
    base = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = base.load()
    for y, row in enumerate(MASCOT_GRID):
        for x, ch in enumerate(row):
            if ch == "C":
                px[x, y] = FG_MASCOT_ICON

    resample = getattr(getattr(Image, "Resampling", Image), "NEAREST")
    img_256 = base.resize((256, 256), resample=resample)

    png_path = out_dir / "claude_mascot.png"
    ico_path = out_dir / "claude_mascot.ico"

    img_256.save(png_path, format="PNG")
    img_256.save(
        ico_path,
        format="ICO",
        sizes=[(16, 16), (32, 32), (64, 64), (128, 128), (256, 256)],
    )
    return ico_path, png_path


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while chunk := f.read(1024 * 1024):
            h.update(chunk)
    return h.hexdigest().lower()


def download_official_claude(payload_dir: Path, progress_cb=None) -> dict:
    payload_dir.mkdir(parents=True, exist_ok=True)
    info_path = payload_dir / "manifest_info.json"
    target_exe = payload_dir / "claude.exe"

    if os.name != "nt":
        info = {"bundled": False, "reason": "non-windows", "version": APP_VERSION}
        info_path.write_text(json.dumps(info, indent=2), encoding="utf-8")
        return info

    arch = "win32-arm64" if platform.machine().lower() in ("arm64", "aarch64") else "win32-x64"
    base_url = "https://downloads.claude.ai/claude-code-releases"
    ctx = get_ssl_context()
    headers = {"User-Agent": "ClaudeCode-Setup-Builder/2.1"}

    try:
        req = urllib.request.Request(f"{base_url}/latest", headers=headers)
        with urllib.request.urlopen(req, timeout=15, context=ctx) as resp:
            latest_ver = resp.read().decode("utf-8").strip()

        req_m = urllib.request.Request(f"{base_url}/{latest_ver}/manifest.json", headers=headers)
        with urllib.request.urlopen(req_m, timeout=15, context=ctx) as resp:
            manifest = json.loads(resp.read().decode("utf-8"))

        expected_sha = manifest["platforms"][arch]["checksum"].lower()

        if target_exe.exists() and sha256_file(target_exe) == expected_sha:
            safe_print(f"  [OK] المحرك الرسمي claude.exe (v{latest_ver}) محمل مسبقاً ومطابق للبصمة.")
            info = {"bundled": True, "version": latest_ver, "arch": arch, "sha256": expected_sha}
            info_path.write_text(json.dumps(info, indent=2), encoding="utf-8")
            return info

        dl_url = f"{base_url}/{latest_ver}/{arch}/claude.exe"
        safe_print(f"  [..] جاري تحميل claude.exe الرسمي (v{latest_ver} / {arch}) ...")

        tmp_exe = payload_dir / "claude.exe.part"
        req_bin = urllib.request.Request(dl_url, headers=headers)
        with urllib.request.urlopen(req_bin, timeout=60, context=ctx) as resp:
            total = int(resp.headers.get("Content-Length") or 0)
            downloaded = 0
            h = hashlib.sha256()
            with open(tmp_exe, "wb") as out_f:
                while True:
                    chunk = resp.read(1024 * 512)
                    if not chunk:
                        break
                    out_f.write(chunk)
                    h.update(chunk)
                    downloaded += len(chunk)
                    if total > 0:
                        pct = int(downloaded * 100 / total)
                        if progress_cb:
                            progress_cb(pct, downloaded, total)
                        elif sys.stdout is not None:
                            mb_d = downloaded / (1024 * 1024)
                            mb_t = total / (1024 * 1024)
                            print(f"\r      التقدم: {pct}% ({mb_d:.1f} / {mb_t:.1f} MB)", end="", flush=True)
            if sys.stdout is not None and total > 0:
                print()

        actual_sha = h.hexdigest().lower()
        if actual_sha != expected_sha:
            tmp_exe.unlink(missing_ok=True)
            raise ValueError(f"فشل التحقق من SHA-256: متوقع {expected_sha}, فعلي {actual_sha}")

        if target_exe.exists():
            target_exe.unlink()
        tmp_exe.rename(target_exe)

        safe_print(f"  [OK] تم تحميل وتوثيق claude.exe (v{latest_ver}) بنجاح!")
        info = {"bundled": True, "version": latest_ver, "arch": arch, "sha256": expected_sha}
        info_path.write_text(json.dumps(info, indent=2), encoding="utf-8")
        return info

    except Exception as exc:
        safe_print(f"  [!] تعذر التحميل المسبق لـ claude.exe ({exc}).")
        safe_print("      سيتم بناء التطبيق بالوكيل المدمج الجاهز فوراً مع OpenRouter و DeepSeek.")
        info = {"bundled": False, "reason": str(exc), "version": APP_VERSION}
        info_path.write_text(json.dumps(info, indent=2), encoding="utf-8")
        return info


def build_self_to_exe():
    configure_console_utf8()
    safe_print("=" * 66)
    safe_print("  Claude Code v2.1.70 — البناء والتحميل المسبق والتحويل إلى EXE")
    safe_print("=" * 66)

    project_root = Path(__file__).resolve().parent
    build_cache = project_root / "build_cache"
    payload_dir = build_cache / "payload"
    dist_dir = project_root / "dist"
    build_cache.mkdir(parents=True, exist_ok=True)
    payload_dir.mkdir(parents=True, exist_ok=True)
    dist_dir.mkdir(parents=True, exist_ok=True)

    safe_print("\n[1/5] فحص وتثبيت مكتبات بايثون المطلوبة...")
    ensure_pip_packages()

    safe_print("\n[2/5] إنشاء الشعار والأيقونة بدقة البكسل...")
    ico_path, png_path = generate_mascot_assets(payload_dir)
    safe_print(f"  [OK] تم إنشاء الشعار: {ico_path.name}")

    safe_print("\n[3/5] التحميل المسبق لمحرك Claude Code الرسمي...")
    download_official_claude(payload_dir)

    safe_print("\n[4/5] تجهيز وبناء تطبيق ClaudeCode.exe ...")
    core_copy = build_cache / "core_module.py"
    shutil.copy2(Path(__file__).resolve(), core_copy)

    app_entry = build_cache / "app_entry.py"
    app_entry.write_text(
        "import core_module\n"
        "if __name__ == '__main__':\n"
        "    core_module.run_app()\n",
        encoding="utf-8",
    )

    setup_entry = build_cache / "setup_entry.py"
    setup_entry.write_text(
        "import core_module\n"
        "if __name__ == '__main__':\n"
        "    core_module.run_setup()\n",
        encoding="utf-8",
    )

    sep = os.pathsep
    app_cmd = [
        sys.executable, "-m", "PyInstaller",
        "--noconfirm", "--clean", "--onefile", "--windowed",
        "--name", "ClaudeCode",
        "--icon", str(ico_path),
        "--distpath", str(payload_dir),
        "--workpath", str(build_cache / "work_app"),
        "--specpath", str(build_cache),
        "--paths", str(build_cache),
        "--add-data", f"{ico_path}{sep}assets",
        "--add-data", f"{png_path}{sep}assets",
        "--hidden-import", "anthropic",
        "--hidden-import", "httpx",
        "--hidden-import", "pydantic",
        "--hidden-import", "certifi",
        "--collect-submodules", "anthropic",
        "--collect-data", "certifi",
        str(app_entry),
    ]
    subprocess.check_call(app_cmd)
    safe_print(f"  [OK] تم بناء: {payload_dir / 'ClaudeCode.exe'}")

    safe_print("\n[5/5] تجميع حزمة التثبيت الشاملة ClaudeCode_Setup.exe ...")
    setup_cmd = [
        sys.executable, "-m", "PyInstaller",
        "--noconfirm", "--clean", "--onefile", "--windowed",
        "--name", "ClaudeCode_Setup",
        "--icon", str(ico_path),
        "--distpath", str(dist_dir),
        "--workpath", str(build_cache / "work_setup"),
        "--specpath", str(build_cache),
        "--paths", str(build_cache),
        "--add-data", f"{payload_dir}{sep}payload",
        "--exclude-module", "anthropic",
        str(setup_entry),
    ]
    subprocess.check_call(setup_cmd)

    final_setup_exe = dist_dir / ("ClaudeCode_Setup.exe" if os.name == "nt" else "ClaudeCode_Setup")
    safe_print("\n" + "=" * 66)
    safe_print(f"  تم التحويل بنجاح! ملف التثبيت الجاهز:\n  {final_setup_exe}")
    safe_print("  جاري تشغيل معالج التثبيت لاختيار مجلد التثبيت...")
    safe_print("=" * 66)

    if final_setup_exe.exists():
        subprocess.Popen([str(final_setup_exe)], cwd=str(dist_dir))


# ==============================================================================
#  المرحلة 2: معالج التثبيت (اختيار موقع التثبيت + الاختصارات + التشغيل التلقائي)
# ==============================================================================

def create_windows_shortcut(target_exe: Path, lnk_path: Path, icon_path: Path, work_dir: Path, desc: str):
    if os.name != "nt":
        return
    env = os.environ.copy()
    env["SC_TARGET"] = str(target_exe)
    env["SC_PATH"] = str(lnk_path)
    env["SC_ICON"] = str(icon_path)
    env["SC_WORKDIR"] = str(work_dir)
    env["SC_DESC"] = desc
    ps_cmd = (
        "$ws = New-Object -ComObject WScript.Shell; "
        "$s = $ws.CreateShortcut($env:SC_PATH); "
        "$s.TargetPath = $env:SC_TARGET; "
        "$s.WorkingDirectory = $env:SC_WORKDIR; "
        "$s.IconLocation = ($env:SC_ICON + ',0'); "
        "$s.Description = $env:SC_DESC; "
        "$s.Save()"
    )
    subprocess.run(
        ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", ps_cmd],
        env=env,
        creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
        check=False,
    )


def add_to_user_path(folder: Path):
    if os.name != "nt":
        return
    try:
        import winreg
        folder_str = str(folder.resolve())
        with winreg.OpenKey(
            winreg.HKEY_CURRENT_USER, r"Environment", 0, winreg.KEY_READ | winreg.KEY_WRITE
        ) as key:
            try:
                current_path, reg_type = winreg.QueryValueEx(key, "Path")
            except FileNotFoundError:
                current_path, reg_type = "", winreg.REG_EXPAND_SZ
            parts = [p.strip() for p in current_path.split(";") if p.strip()]
            if not any(os.path.normcase(p) == os.path.normcase(folder_str) for p in parts):
                new_path = ";".join(parts + [folder_str])
                winreg.SetValueEx(key, "Path", 0, reg_type, new_path)
                try:
                    import ctypes
                    ctypes.windll.user32.SendMessageTimeoutW(
                        0xFFFF, 0x001A, 0, "Environment", 0x0002, 1000, None
                    )
                except Exception:
                    pass
    except Exception:
        pass


def run_setup():
    multiprocessing.freeze_support()
    import tkinter as tk
    from tkinter import filedialog, messagebox, ttk

    root = tk.Tk()
    root.title(f"Claude Code Setup — معالج التثبيت — {APP_CREDIT}")
    root.configure(bg=BG_DARK)
    root.resizable(False, False)

    w, h = 630, 550
    sw, sh = root.winfo_screenwidth(), root.winfo_screenheight()
    root.geometry(f"{w}x{h}+{(sw - w) // 2}+{(sh - h) // 2}")
    enable_windows_dark_titlebar(root)
    set_window_icon(root)

    payload_dir = get_bundle_root() / "payload"
    if not payload_dir.exists():
        payload_dir = Path(__file__).resolve().parent / "build_cache" / "payload"

    manifest_info = {}
    mf_path = payload_dir / "manifest_info.json"
    if mf_path.exists():
        try:
            manifest_info = json.loads(mf_path.read_text(encoding="utf-8"))
        except Exception:
            pass

    default_dir = str(Path.home() / "AppData" / "Local" / "ClaudeCode")
    install_dir_var = tk.StringVar(value=default_dir)
    desktop_var = tk.BooleanVar(value=True)
    startmenu_var = tk.BooleanVar(value=True)
    path_var = tk.BooleanVar(value=True)
    launch_after_var = tk.BooleanVar(value=True)
    status_var = tk.StringVar(value="جاهز للتثبيت — جميع الملفات والمفاتيح مهيأة ومحمّلة مسبقاً.")

    header = tk.Frame(root, bg=BG_DARK, padx=26, pady=20)
    header.pack(fill="x")

    png_path = get_asset_path("claude_mascot.png")
    if png_path.exists():
        try:
            raw_logo = tk.PhotoImage(file=str(png_path))
            logo_small = raw_logo.subsample(4, 4)
            root._setup_logo = logo_small
            tk.Label(header, image=logo_small, bg=BG_DARK).pack(side="left", padx=(0, 16))
        except Exception:
            pass

    title_box = tk.Frame(header, bg=BG_DARK)
    title_box.pack(side="left", fill="x", expand=True)
    tk.Label(
        title_box, text="Claude Code Setup", font=("Segoe UI", 18, "bold"),
        fg=FG_CORAL, bg=BG_DARK, anchor="w"
    ).pack(fill="x")
    tk.Label(
        title_box,
        text=f"تثبيت واجهة وشعار Claude Code {APP_VERSION} + OpenRouter & DeepSeek",
        font=("Segoe UI", 10), fg=FG_GRAY, bg=BG_DARK, anchor="w"
    ).pack(fill="x", pady=(2, 0))

    card = tk.Frame(root, bg=BG_PANEL, highlightbackground=BORDER_DIM, highlightthickness=1, padx=18, pady=12)
    card.pack(fill="x", padx=26)

    bundled_cli = manifest_info.get("bundled", False) and (payload_dir / "claude.exe").exists()
    cli_ver = manifest_info.get("version", APP_VERSION)
    items = [
        ("✓ واجهة Claude Code الكاملة + الشعار الرسمي + شريط استهلاك التوكن", FG_GREEN),
        ("✓ مفاتيح OpenRouter (Claude Fable 5.2 / Opus 5.5) و DeepSeek جاهزة مباشرة", FG_GREEN),
        (
            f"✓ المحرك الرسمي claude.exe (الإصدار {cli_ver}) — محمّل مسبقاً"
            if bundled_cli
            else "• الوكيل البرمجي المدمج جاهز للعمل مباشرة (ويمكن تحميل CLI الرسمي لاحقاً)",
            FG_GREEN if bundled_cli else FG_WARN,
        ),
    ]
    for txt, col in items:
        tk.Label(card, text=txt, font=("Segoe UI", 9), fg=col, bg=BG_PANEL, anchor="w").pack(fill="x", pady=2)

    path_frame = tk.Frame(root, bg=BG_DARK, padx=26, pady=16)
    path_frame.pack(fill="x")

    tk.Label(
        path_frame, text="موقع التثبيت (Install Location):",
        font=("Segoe UI", 10, "bold"), fg=FG_WHITE, bg=BG_DARK, anchor="w"
    ).pack(fill="x", pady=(0, 6))

    row_entry = tk.Frame(path_frame, bg=BG_DARK)
    row_entry.pack(fill="x")

    tk.Entry(
        row_entry, textvariable=install_dir_var, font=("Consolas", 10),
        bg=BG_INPUT, fg=FG_WHITE, insertbackground=FG_WHITE,
        relief="flat", highlightbackground=BORDER_DIM, highlightthickness=1
    ).pack(side="left", fill="x", expand=True, ipady=7, padx=(0, 8))

    def browse_dir():
        chosen = filedialog.askdirectory(initialdir=install_dir_var.get(), title="اختر مجلد التثبيت")
        if chosen:
            chosen_path = Path(chosen)
            if chosen_path.name.lower() != "claudecode":
                chosen_path = chosen_path / "ClaudeCode"
            install_dir_var.set(str(chosen_path))

    browse_btn = tk.Button(
        row_entry, text="استعراض...", command=browse_dir,
        font=("Segoe UI", 9, "bold"), bg=BG_PANEL, fg=FG_WHITE,
        activebackground=BORDER_DIM, activeforeground=FG_WHITE,
        relief="flat", padx=14, pady=6, cursor="hand2"
    )
    browse_btn.pack(side="right")

    opts_frame = tk.Frame(root, bg=BG_DARK, padx=26)
    opts_frame.pack(fill="x")

    chk_style = dict(
        bg=BG_DARK, fg=FG_WHITE, selectcolor=BG_INPUT,
        activebackground=BG_DARK, activeforeground=FG_WHITE,
        font=("Segoe UI", 9), anchor="w"
    )
    tk.Checkbutton(opts_frame, text="إنشاء اختصار على سطح المكتب (Desktop)", variable=desktop_var, **chk_style).pack(fill="x", pady=2)
    tk.Checkbutton(opts_frame, text="إنشاء اختصار في قائمة ابدأ (Start Menu)", variable=startmenu_var, **chk_style).pack(fill="x", pady=2)
    tk.Checkbutton(opts_frame, text="إضافة مجلد التثبيت إلى PATH", variable=path_var, **chk_style).pack(fill="x", pady=2)
    tk.Checkbutton(opts_frame, text="تشغيل Claude Code تلقائياً بعد انتهاء التثبيت", variable=launch_after_var, **chk_style).pack(fill="x", pady=2)

    prog_frame = tk.Frame(root, bg=BG_DARK, padx=26, pady=14)
    prog_frame.pack(fill="x")

    style = ttk.Style()
    style.theme_use("clam")
    style.configure("Claude.Horizontal.TProgressbar", troughcolor=BG_INPUT, background=BTN_ACCENT, bordercolor=BORDER_DIM, thickness=10)
    progress = ttk.Progressbar(prog_frame, style="Claude.Horizontal.TProgressbar", mode="determinate", maximum=100)
    progress.pack(fill="x")

    tk.Label(prog_frame, textvariable=status_var, font=("Segoe UI", 9), fg=FG_GRAY, bg=BG_DARK, anchor="w").pack(fill="x", pady=(6, 0))

    footer = tk.Frame(root, bg=BG_DARK, padx=26, pady=10)
    footer.pack(fill="x", side="bottom")

    install_btn = tk.Button(
        footer, text="تثبيت وتشغيل الآن (Install)", font=("Segoe UI", 10, "bold"),
        bg=BTN_ACCENT, fg="#14111A", activebackground=BTN_ACCENT_HOVER, activeforeground="#14111A",
        relief="flat", padx=22, pady=9, cursor="hand2"
    )
    install_btn.pack(side="right")

    cancel_btn = tk.Button(
        footer, text="إلغاء", command=root.destroy, font=("Segoe UI", 10),
        bg=BG_PANEL, fg=FG_WHITE, activebackground=BORDER_DIM, activeforeground=FG_WHITE,
        relief="flat", padx=18, pady=9, cursor="hand2"
    )
    cancel_btn.pack(side="right", padx=(0, 10))

    def start_install():
        dest = Path(install_dir_var.get().strip())
        if not str(dest):
            messagebox.showwarning("تنبيه", "الرجاء اختيار مسار تثبيت صحيح.")
            return

        install_btn.config(state="disabled")
        browse_btn.config(state="disabled")
        cancel_btn.config(state="disabled")

        def worker():
            try:
                root.after(0, lambda: (progress.configure(value=15), status_var.set("جاري إنشاء مجلد التثبيت...")))
                dest.mkdir(parents=True, exist_ok=True)
                test_file = dest / ".write_test"
                test_file.write_text("ok", encoding="utf-8")
                test_file.unlink()

                files_to_copy = [f for f in payload_dir.iterdir() if f.is_file()]
                total_files = max(len(files_to_copy), 1)
                for idx, src in enumerate(files_to_copy, 1):
                    pct = 15 + int((idx / total_files) * 55)
                    root.after(0, lambda p=pct, n=src.name: (progress.configure(value=p), status_var.set(f"جاري نسخ: {n}...")))
                    shutil.copy2(src, dest / src.name)

                installed_app = dest / ("ClaudeCode.exe" if os.name == "nt" else "ClaudeCode")
                installed_ico = dest / "claude_mascot.ico"

                uninst = dest / "uninstall.bat"
                uninst.write_text(
                    f'@echo off\r\n'
                    f'echo Removing Claude Code from "{dest}"...\r\n'
                    f'del /q "%USERPROFILE%\\Desktop\\Claude Code.lnk" 2>nul\r\n'
                    f'rmdir /s /q "%APPDATA%\\Microsoft\\Windows\\Start Menu\\Programs\\Claude Code" 2>nul\r\n'
                    f'cd /d "%TEMP%"\r\n'
                    f'rmdir /s /q "{dest}"\r\n'
                    f'echo Done.\r\npause\r\n',
                    encoding="utf-8",
                )

                root.after(0, lambda: (progress.configure(value=80), status_var.set("جاري إنشاء الاختصارات...")))
                if os.name == "nt" and installed_app.exists():
                    if desktop_var.get():
                        desktop_lnk = Path.home() / "Desktop" / "Claude Code.lnk"
                        create_windows_shortcut(installed_app, desktop_lnk, installed_ico, dest, "Claude Code")
                    if startmenu_var.get():
                        sm_dir = Path(os.environ.get("APPDATA", "")) / "Microsoft" / "Windows" / "Start Menu" / "Programs" / "Claude Code"
                        sm_dir.mkdir(parents=True, exist_ok=True)
                        create_windows_shortcut(installed_app, sm_dir / "Claude Code.lnk", installed_ico, dest, "Claude Code")
                        cli_exe = dest / "claude.exe"
                        if cli_exe.exists():
                            create_windows_shortcut(cli_exe, sm_dir / "Claude Code CLI.lnk", installed_ico, Path.home(), "Claude Code Terminal")

                if path_var.get():
                    root.after(0, lambda: (progress.configure(value=92), status_var.set("جاري تحديث متغير البيئة PATH...")))
                    add_to_user_path(dest)

                root.after(0, lambda: (progress.configure(value=100), status_var.set("تم التثبيت بنجاح!")))

                def finish():
                    if launch_after_var.get() and installed_app.exists():
                        subprocess.Popen([str(installed_app)], cwd=str(dest))
                        root.after(500, root.destroy)
                    else:
                        messagebox.showinfo("تم التثبيت", f"تم تثبيت Claude Code بنجاح في:\n{dest}")
                        root.destroy()

                root.after(300, finish)

            except PermissionError:
                root.after(0, lambda: (
                    install_btn.config(state="normal"),
                    browse_btn.config(state="normal"),
                    cancel_btn.config(state="normal"),
                    progress.configure(value=0),
                    status_var.set("فشل التثبيت: لا توجد صلاحية للكتابة في هذا المسار."),
                    messagebox.showerror(
                        "صلاحيات غير كافية",
                        "لا توجد صلاحية للكتابة في المسار المختار.\nاختر مجلداً داخل مجلد المستخدم أو شغّل المثبت كمسؤول."
                    ),
                ))
            except Exception as exc:
                root.after(0, lambda: (
                    install_btn.config(state="normal"),
                    browse_btn.config(state="normal"),
                    cancel_btn.config(state="normal"),
                    progress.configure(value=0),
                    status_var.set(f"خطأ: {exc}"),
                    messagebox.showerror("خطأ في التثبيت", str(exc)),
                ))

        threading.Thread(target=worker, daemon=True).start()

    install_btn.config(command=start_install)
    root.mainloop()


# ==============================================================================
#  المرحلة 3: التطبيق الرئيسي (واجهة Claude Code + تبديل النماذج + استهلاك التوكن)
# ==============================================================================

def install_official_claude_statusline():
    """
    يكتب سكربت statusLine في ~/.claude/statusline_cc.ps1 ويربطه بـ ~/.claude/settings.json
    لكي يعرض claude.exe الرسمي استهلاك التوكن ونسبة السياق والتكلفة في مكانه الأصلي أسفل الشاشة.
    """
    if os.name != "nt":
        return
    try:
        claude_dir = Path.home() / ".claude"
        claude_dir.mkdir(parents=True, exist_ok=True)
        ps1_path = claude_dir / "statusline_cc.ps1"
        ps1_code = r'''[Console]::InputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$raw = [Console]::In.ReadToEnd()
if (-not $raw.Trim()) { exit 0 }
$j = $raw | ConvertFrom-Json
$e = [char]27
$model = if ($j.model.display_name) { $j.model.display_name } elseif ($j.model.id) { $j.model.id } else { "Claude" }
$cwd = if ($j.workspace.current_dir) { Split-Path -Leaf $j.workspace.current_dir } else { "workspace" }
$pct = [double]($j.context_window.used_percentage)
$cost = [double]($j.cost.total_cost_usd)
$filled = [Math]::Min(10, [Math]::Max(0, [Math]::Round($pct / 10)))
$bar = ("#" * $filled) + ("-" * (10 - $filled))
$clr = if ($pct -ge 80) { "$e[38;2;247;118;142m" } elseif ($pct -ge 50) { "$e[38;2;229;192;123m" } else { "$e[38;2;140;224;154m" }
$rst = "$e[0m"
$dim = "$e[38;2;124;121;135m"
$blu = "$e[38;2;122;162;247m"
$costStr = $cost.ToString("0.0000")
$pctStr = $pct.ToString("0.0")
Write-Output "$blu$model$rst $dim|$rst $cwd $dim|$rst ${clr}ctx [$bar] $pctStr%$rst $dim|$rst `$$costStr"
'''
        ps1_path.write_text(ps1_code, encoding="utf-8")

        settings_path = claude_dir / "settings.json"
        settings_data = {}
        if settings_path.exists():
            try:
                settings_data = json.loads(settings_path.read_text(encoding="utf-8"))
            except Exception:
                settings_data = {}

        settings_data["statusLine"] = {
            "type": "command",
            "command": f'powershell -NoProfile -ExecutionPolicy Bypass -File "{ps1_path}"',
            "padding": 0,
        }
        settings_path.write_text(json.dumps(settings_data, indent=2, ensure_ascii=False), encoding="utf-8")
    except Exception:
        pass


def run_app():
    multiprocessing.freeze_support()
    import tkinter as tk
    from tkinter import filedialog, messagebox, scrolledtext, ttk

    root = tk.Tk()
    root.title(f"Windows PowerShell — {APP_TITLE} {APP_VERSION} — تصميم {APP_AUTHOR}")
    root.configure(bg=BG_DARK)
    root.minsize(900, 620)

    w, h = 1020, 690
    sw, sh = root.winfo_screenwidth(), root.winfo_screenheight()
    root.geometry(f"{w}x{h}+{(sw - w) // 2}+{(sh - h) // 2}")
    enable_windows_dark_titlebar(root)
    set_window_icon(root)

    # ضبط ثيم القوائم المنسدلة الداكنة
    style = ttk.Style()
    style.theme_use("clam")
    style.configure(
        "Dark.TCombobox",
        fieldbackground=BG_INPUT,
        background=BG_PANEL,
        foreground=FG_WHITE,
        arrowcolor=FG_WHITE,
        bordercolor=BORDER_DIM,
        lightcolor=BORDER_DIM,
        darkcolor=BORDER_DIM,
    )
    style.map(
        "Dark.TCombobox",
        fieldbackground=[("readonly", BG_INPUT)],
        foreground=[("readonly", FG_WHITE)],
        selectbackground=[("readonly", BG_INPUT)],
        selectforeground=[("readonly", FG_WHITE)],
    )
    root.option_add("*TCombobox*Listbox.background", BG_PANEL)
    root.option_add("*TCombobox*Listbox.foreground", FG_WHITE)
    root.option_add("*TCombobox*Listbox.selectBackground", BTN_ACCENT)
    root.option_add("*TCombobox*Listbox.selectForeground", "#14111A")

    mono_font = "Cascadia Mono" if os.name == "nt" else "Courier"
    try:
        import tkinter.font as tkfont
        fams = {f.lower() for f in tkfont.families()}
        if "cascadia mono" in fams:
            mono_font = "Cascadia Mono"
        elif "consolas" in fams:
            mono_font = "Consolas"
        else:
            mono_font = "Courier New"
    except Exception:
        mono_font = "Consolas"

    cfg = load_config()
    state = {
        "selected_idx": 0,
        "view": "welcome",
        "provider": cfg.get("provider", "openrouter"),
        "model": cfg.get("model", "anthropic/claude-fable-5.2"),
        "openrouter_key": cfg.get("openrouter_key", DEFAULT_OPENROUTER_KEY),
        "deepseek_key": cfg.get("deepseek_key", DEFAULT_DEEPSEEK_KEY),
        "work_dir": cfg.get("work_dir", str(Path.home())),
        "history": [],
        "busy": False,
        "live_or_models": list(BUILTIN_MODELS["openrouter"]),
        "usage": {
            "total_in": 0,
            "total_out": 0,
            "total_cache": 0,
            "context_tokens": 0,
            "last_in": 0,
            "last_out": 0,
            "last_cache": 0,
            "last_cost": 0.0,
            "total_cost": 0.0,
            "turns": 0,
        },
    }

    # جلب قائمة النماذج وأسعارها المحدثة من OpenRouter في الخلفية ليتاح للمستخدم التبديل لأي نموذج
    def fetch_openrouter_models_bg():
        try:
            ctx = get_ssl_context()
            req = urllib.request.Request(
                "https://openrouter.ai/api/v1/models",
                headers={"User-Agent": "ClaudeCode-Desktop/2.1"},
            )
            with urllib.request.urlopen(req, timeout=12, context=ctx) as resp:
                data = json.loads(resp.read().decode("utf-8")).get("data", [])

            existing_ids = {m["id"] for m in BUILTIN_MODELS["openrouter"]}
            merged = list(BUILTIN_MODELS["openrouter"])

            # تحديث أسعار النماذج المثبتة بالأعلى إن توفرت
            price_lookup = {}
            for item in data:
                mid = item.get("id", "")
                pr = item.get("pricing") or {}
                try:
                    pin = float(pr.get("prompt") or 0) * 1_000_000
                    pout = float(pr.get("completion") or 0) * 1_000_000
                except Exception:
                    pin, pout = 0.0, 0.0
                ctx_len = int(item.get("context_length") or 200_000)
                price_lookup[mid] = (pin, pout, ctx_len)

            for m in merged:
                if m["id"] in price_lookup:
                    pin, pout, ctx_len = price_lookup[m["id"]]
                    if pin > 0 or pout > 0:
                        m["in_price"], m["out_price"] = pin, pout
                    m["context"] = ctx_len

            # إضافة أقوى النماذج الإضافية الداعمة للأدوات من OpenRouter
            priority_prefixes = ("anthropic/", "openai/", "google/", "deepseek/", "x-ai/", "qwen/")
            extras = []
            for item in data:
                mid = item.get("id", "")
                if mid in existing_ids:
                    continue
                if any(mid.startswith(pref) for pref in priority_prefixes):
                    sp = item.get("supported_parameters") or []
                    if not sp or "tools" in sp:
                        pin, pout, ctx_len = price_lookup.get(mid, (0.0, 0.0, 128_000))
                        extras.append({
                            "id": mid,
                            "label": item.get("name") or mid,
                            "in_price": pin,
                            "out_price": pout,
                            "cache_price": pin * 0.1,
                            "context": ctx_len,
                        })
            state["live_or_models"] = merged + extras[:120]
        except Exception:
            pass

    threading.Thread(target=fetch_openrouter_models_bg, daemon=True).start()

    def get_model_meta(provider: str, model_id: str) -> dict:
        pool = state["live_or_models"] if provider == "openrouter" else BUILTIN_MODELS["deepseek"]
        for m in pool:
            if m["id"] == model_id:
                return m
        return {
            "id": model_id,
            "label": model_id,
            "in_price": 4.0 if provider == "openrouter" else 0.14,
            "out_price": 20.0 if provider == "openrouter" else 0.28,
            "cache_price": 0.20 if provider == "openrouter" else 0.01,
            "context": 1_000_000,
        }

    main_container = tk.Frame(root, bg=BG_DARK)
    main_container.pack(fill="both", expand=True)

    def clear_view():
        for child in main_container.winfo_children():
            child.destroy()

    # --------------------------------------------------------------------------
    #  دالة رسم المشهد الليلي المطابق تماماً للصورة الأولى
    # --------------------------------------------------------------------------
    def draw_welcome_scene(canvas: tk.Canvas):
        line_w = 500
        left_x = 12
        top_y = 10
        bot_y = 188

        def draw_dotted_line(y):
            x = left_x
            while x < left_x + line_w:
                for dx in (0, 3, 6):
                    canvas.create_rectangle(x + dx, y, x + dx + 2, y + 2, fill=FG_GRAY, outline="")
                x += 11

        draw_dotted_line(top_y)
        draw_dotted_line(bot_y)

        def draw_halftone_rect(x0, y0, x1, y1, color, step=3):
            for yy in range(y0, y1, step):
                row_offset = (yy // step) % 2
                for xx in range(x0 + row_offset * (step // 2), x1, step):
                    canvas.create_rectangle(xx, yy, xx + 1, yy + 1, fill=color, outline="")

        draw_halftone_rect(36, 74, 198, 92, "#6E6B7B", step=3)
        draw_halftone_rect(76, 58, 178, 74, "#6E6B7B", step=3)
        draw_halftone_rect(108, 43, 166, 58, "#6E6B7B", step=3)

        draw_halftone_rect(268, 128, 405, 144, "#545160", step=3)
        draw_halftone_rect(290, 114, 370, 128, "#545160", step=3)
        draw_halftone_rect(302, 100, 344, 114, "#545160", step=3)

        stars = [
            (54, 28, "#EBE9F2"),
            (292, 42, "#EBE9F2"),
            (20, 112, "#6B6878"),
            (234, 84, "#EBE9F2"),
            (486, 154, "#6B6878"),
            (360, 165, "#EBE9F2"),
            (198, 174, "#EBE9F2"),
        ]
        for sx, sy, scol in stars:
            canvas.create_text(sx, sy, text="*", font=(mono_font, 13, "bold"), fill=scol)

        moon_x, moon_y, cell = 358, 22, 8
        for ry, row in enumerate(MOON_GRID):
            for cx, ch in enumerate(row):
                px0 = moon_x + cx * cell
                py0 = moon_y + ry * cell
                if ch == "W":
                    canvas.create_rectangle(px0, py0, px0 + cell, py0 + cell, fill="#F3F2F7", outline="")
                elif ch == "M":
                    draw_halftone_rect(px0, py0, px0 + cell, py0 + cell, "#A3A0B0", step=2)
                elif ch == "D":
                    draw_halftone_rect(px0, py0, px0 + cell, py0 + cell, "#5D5A6A", step=2)

        mascot_x = 64
        p_size = 5
        non_empty_rows = MASCOT_GRID[3:13]
        mascot_y = bot_y - (len(non_empty_rows) * p_size) + 2
        for ry, row in enumerate(non_empty_rows):
            for cx, ch in enumerate(row):
                if ch == "C":
                    px0 = mascot_x + cx * p_size
                    py0 = mascot_y + ry * p_size
                    canvas.create_rectangle(px0, py0, px0 + p_size, py0 + p_size, fill=FG_CORAL, outline="")

    # --------------------------------------------------------------------------
    #  إطلاق claude.exe الرسمي في PowerShell مع حقن OpenRouter أو DeepSeek
    # --------------------------------------------------------------------------
    def launch_official_cli(project_dir: str, provider_mode: str = "openrouter", model_id: str | None = None):
        cli_exe = find_official_claude_exe()
        if not cli_exe:
            if messagebox.askyesno(
                "تحميل المحرك الرسمي",
                "لم يتم العثور على claude.exe الرسمي محلياً.\nهل تريد فتح PowerShell لتحميل المثبت الرسمي لـ Anthropic الآن؟"
            ):
                subprocess.Popen(
                    ["powershell", "-NoExit", "-ExecutionPolicy", "Bypass", "-Command", "irm https://claude.ai/install.ps1 | iex; claude"],
                    creationflags=getattr(subprocess, "CREATE_NEW_CONSOLE", 0),
                )
            return

        install_official_claude_statusline()
        work_path = Path(project_dir) if project_dir and Path(project_dir).exists() else Path.home()
        launch_env = os.environ.copy()

        if provider_mode == "openrouter":
            m_use = model_id or state["model"] if state["provider"] == "openrouter" else "anthropic/claude-fable-5.2"
            launch_env["ANTHROPIC_BASE_URL"] = OPENROUTER_BASE_URL
            launch_env["ANTHROPIC_AUTH_TOKEN"] = state["openrouter_key"]
            launch_env["OPENROUTER_API_KEY"] = state["openrouter_key"]
            launch_env["ANTHROPIC_API_KEY"] = ""
            launch_env["ANTHROPIC_MODEL"] = m_use
            launch_env["ANTHROPIC_DEFAULT_OPUS_MODEL"] = "anthropic/claude-opus-5.5"
            launch_env["ANTHROPIC_DEFAULT_SONNET_MODEL"] = "anthropic/claude-sonnet-5.5"
            launch_env["ANTHROPIC_DEFAULT_HAIKU_MODEL"] = "anthropic/claude-haiku-5.5"
            launch_env["CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC"] = "1"
        elif provider_mode == "deepseek":
            m_use = model_id or state["model"] if state["provider"] == "deepseek" else "deepseek-flash"
            cli_model = f"{m_use}[1m]" if m_use in ("deepseek-flash", "deepseek-v4-pro") and "[1m]" not in m_use else m_use
            launch_env["ANTHROPIC_BASE_URL"] = DEEPSEEK_BASE_URL
            launch_env["ANTHROPIC_AUTH_TOKEN"] = state["deepseek_key"]
            launch_env["ANTHROPIC_API_KEY"] = ""
            launch_env["ANTHROPIC_MODEL"] = cli_model
            launch_env["ANTHROPIC_DEFAULT_OPUS_MODEL"] = cli_model
            launch_env["ANTHROPIC_DEFAULT_SONNET_MODEL"] = cli_model
            launch_env["ANTHROPIC_DEFAULT_HAIKU_MODEL"] = "deepseek-flash"
            launch_env["CLAUDE_CODE_SUBAGENT_MODEL"] = "deepseek-flash"
            launch_env["CLAUDE_CODE_EFFORT_LEVEL"] = "max"
            launch_env["CLAUDE_CODE_AUTO_COMPACT_WINDOW"] = "786432"
            launch_env["CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC"] = "1"

        launch_env["CC_DIR"] = str(work_path)
        launch_env["CC_EXE"] = str(cli_exe)
        launch_env["CC_BIN_DIR"] = str(cli_exe.parent)

        if os.name == "nt":
            ps_cmd = (
                "$env:PATH = $env:CC_BIN_DIR + ';' + $env:PATH; "
                "Set-Location -LiteralPath $env:CC_DIR; "
                "& $env:CC_EXE"
            )
            subprocess.Popen(
                ["powershell", "-NoExit", "-ExecutionPolicy", "Bypass", "-Command", ps_cmd],
                env=launch_env,
                creationflags=getattr(subprocess, "CREATE_NEW_CONSOLE", 0),
            )
        else:
            subprocess.Popen([str(cli_exe)], cwd=str(work_path), env=launch_env)

    # --------------------------------------------------------------------------
    #  شاشة الترحيب (مطابقة للصورة الأولى — تدخل مباشرة بضغطة Enter)
    # --------------------------------------------------------------------------
    def show_welcome():
        state["view"] = "welcome"
        clear_view()

        pad = tk.Frame(main_container, bg=BG_DARK, padx=26, pady=20)
        pad.pack(fill="both", expand=True)

        title_row = tk.Frame(pad, bg=BG_DARK)
        title_row.pack(fill="x", anchor="w")
        tk.Label(title_row, text="Welcome to Claude Code", font=(mono_font, 12), fg=FG_CORAL, bg=BG_DARK).pack(side="left")
        tk.Label(title_row, text=f" {APP_VERSION}", font=(mono_font, 12), fg=FG_GRAY, bg=BG_DARK).pack(side="left")
        tk.Label(title_row, text=f"   · {APP_CREDIT}", font=(mono_font, 10), fg=FG_MAGENTA, bg=BG_DARK).pack(side="left")

        scene_canvas = tk.Canvas(pad, width=540, height=202, bg=BG_DARK, highlightthickness=0, bd=0)
        scene_canvas.pack(anchor="w", pady=(2, 10))
        draw_welcome_scene(scene_canvas)

        tk.Label(
            pad,
            text="Claude Code is pre-configured with OpenRouter (Strongest Claude Models) and DeepSeek.\nChoose a mode or press Enter to start coding immediately with no extra setup:",
            font=(mono_font, 11), fg=FG_WHITE, bg=BG_DARK, justify="left", anchor="w"
        ).pack(fill="x", pady=(6, 14))

        tk.Label(
            pad, text="Select login method:", font=(mono_font, 11), fg=FG_WHITE, bg=BG_DARK, anchor="w"
        ).pack(fill="x", pady=(0, 8))

        options = [
            ("1. OpenRouter Direct (يعمل مباشرة)", " · Claude Fable 5.2 / Opus 5.5 / Sonnet 5.5 / All Models"),
            ("2. DeepSeek Direct (يعمل مباشرة)", " · DeepSeek-V4.1-Flash / V4-Pro / Reasoner / Chat"),
            ("3. Official Claude CLI (PowerShell)", " · Launch claude.exe (OpenRouter / DeepSeek / Subscription)"),
        ]

        option_widgets = []

        def refresh_selection():
            idx = state["selected_idx"]
            for i, (row_f, arrow_l, main_l, sub_l) in enumerate(option_widgets):
                if i == idx:
                    arrow_l.config(text="❯ ", fg=FG_BLUE)
                    main_l.config(fg=FG_BLUE)
                else:
                    arrow_l.config(text="  ", fg=FG_WHITE)
                    main_l.config(fg=FG_WHITE)

        def activate_option(idx):
            state["selected_idx"] = idx
            refresh_selection()
            if idx == 0:
                state["provider"] = "openrouter"
                if not state["model"].startswith(("anthropic/", "~anthropic/", "openai/", "google/", "deepseek/", "x-ai/", "qwen/")):
                    state["model"] = "anthropic/claude-fable-5.2"
                save_config({"provider": state["provider"], "model": state["model"]})
                show_terminal()
            elif idx == 1:
                state["provider"] = "deepseek"
                if state["model"] not in [m["id"] for m in BUILTIN_MODELS["deepseek"]]:
                    state["model"] = "deepseek-flash"
                save_config({"provider": state["provider"], "model": state["model"]})
                show_terminal()
            else:
                show_official_cli_launcher()

        for i, (main_txt, sub_txt) in enumerate(options):
            row_f = tk.Frame(pad, bg=BG_DARK, cursor="hand2")
            row_f.pack(fill="x", pady=3, anchor="w")

            arrow_l = tk.Label(row_f, text="  ", font=(mono_font, 11, "bold"), bg=BG_DARK, fg=FG_BLUE)
            arrow_l.pack(side="left")

            main_l = tk.Label(row_f, text=main_txt, font=(mono_font, 11), bg=BG_DARK, fg=FG_WHITE)
            main_l.pack(side="left")

            sub_l = tk.Label(row_f, text=sub_txt, font=(mono_font, 11), bg=BG_DARK, fg=FG_GRAY)
            sub_l.pack(side="left")

            for w_item in (row_f, arrow_l, main_l, sub_l):
                w_item.bind("<Enter>", lambda _e, ix=i: (state.__setitem__("selected_idx", ix), refresh_selection()))
                w_item.bind("<Button-1>", lambda _e, ix=i: activate_option(ix))

            option_widgets.append((row_f, arrow_l, main_l, sub_l))

        refresh_selection()

        # شريط الحالة في شاشة البداية مطابق لأسفل النسخة الأصلية
        bottom_bar = tk.Frame(pad, bg=BG_DARK)
        bottom_bar.pack(side="bottom", fill="x")
        tk.Frame(bottom_bar, bg=BORDER_DIM, height=1).pack(fill="x", pady=(0, 6))
        cli_bin = find_official_claude_exe()
        cli_txt = f"claude.exe ready ({cli_bin.name})" if cli_bin else "Built-in Agent Ready"
        tk.Label(
            bottom_bar,
            text=f"↑/↓ or 1-3 to select • Enter to launch directly • Esc to quit    |    {cli_txt}",
            font=(mono_font, 9), fg=FG_DIM, bg=BG_DARK, anchor="w"
        ).pack(fill="x")

        def on_key(event):
            if state["view"] != "welcome":
                return
            if event.keysym == "Up":
                state["selected_idx"] = (state["selected_idx"] - 1) % 3
                refresh_selection()
            elif event.keysym == "Down":
                state["selected_idx"] = (state["selected_idx"] + 1) % 3
                refresh_selection()
            elif event.keysym in ("Return", "KP_Enter"):
                activate_option(state["selected_idx"])
            elif event.char in ("1", "2", "3"):
                activate_option(int(event.char) - 1)
            elif event.keysym == "Escape":
                root.destroy()

        root.bind("<Key>", on_key)

    # --------------------------------------------------------------------------
    #  شاشة تشغيل claude.exe الرسمي في PowerShell بالاختيار الفوري
    # --------------------------------------------------------------------------
    def show_official_cli_launcher():
        state["view"] = "cli_launcher"
        clear_view()

        pad = tk.Frame(main_container, bg=BG_DARK, padx=26, pady=22)
        pad.pack(fill="both", expand=True)

        tk.Label(pad, text="3. Official Claude Code CLI (PowerShell)", font=(mono_font, 14, "bold"), fg=FG_BLUE, bg=BG_DARK, anchor="w").pack(fill="x")
        tk.Label(pad, text="تشغيل ملف claude.exe الرسمي مع ربط شريط استهلاك التوكن تلقائياً", font=(mono_font, 10), fg=FG_GRAY, bg=BG_DARK, anchor="w").pack(fill="x", pady=(2, 16))

        cli_exe = find_official_claude_exe()
        info_box = tk.Frame(pad, bg=BG_PANEL, highlightbackground=BORDER_DIM, highlightthickness=1, padx=16, pady=12)
        info_box.pack(fill="x", pady=(0, 14))
        if cli_exe:
            tk.Label(info_box, text=f"✓ المحرك الرسمي متوفر: {cli_exe}", font=(mono_font, 10), fg=FG_GREEN, bg=BG_PANEL, anchor="w").pack(fill="x")
        else:
            tk.Label(info_box, text="! claude.exe غير مثبت محلياً — سيتم تثبيته من خوادم Anthropic عند الضغط على التشغيل.", font=(mono_font, 10), fg=FG_WARN, bg=BG_PANEL, anchor="w").pack(fill="x")

        tk.Label(pad, text="Working Directory (مجلد العمل):", font=(mono_font, 10), fg=FG_WHITE, bg=BG_DARK, anchor="w").pack(fill="x", pady=(0, 4))
        wd_row = tk.Frame(pad, bg=BG_DARK)
        wd_row.pack(fill="x", pady=(0, 18))
        wd_var = tk.StringVar(value=state["work_dir"])
        tk.Entry(wd_row, textvariable=wd_var, font=(mono_font, 10), bg=BG_INPUT, fg=FG_WHITE, insertbackground=FG_WHITE, relief="flat", highlightbackground=BORDER_DIM, highlightthickness=1).pack(side="left", fill="x", expand=True, ipady=6, padx=(0, 8))

        def browse_wd():
            d = filedialog.askdirectory(initialdir=wd_var.get())
            if d:
                wd_var.set(d)
                state["work_dir"] = d
                save_config({"work_dir": d})

        tk.Button(wd_row, text="Browse...", command=browse_wd, font=(mono_font, 9), bg=BG_PANEL, fg=FG_WHITE, relief="flat", padx=12, pady=5, cursor="hand2").pack(side="right")

        btns = tk.Frame(pad, bg=BG_DARK)
        btns.pack(fill="x")

        tk.Button(
            btns, text="❯ Launch claude.exe (OpenRouter · Fable 5.2)",
            command=lambda: launch_official_cli(wd_var.get(), "openrouter", "anthropic/claude-fable-5.2"),
            font=(mono_font, 10, "bold"), bg=BTN_ACCENT, fg="#14111A", relief="flat", padx=14, pady=9, cursor="hand2"
        ).pack(side="left")

        tk.Button(
            btns, text="❯ Launch claude.exe (DeepSeek)",
            command=lambda: launch_official_cli(wd_var.get(), "deepseek", "deepseek-flash"),
            font=(mono_font, 10, "bold"), bg=BG_PANEL, fg=FG_GREEN, relief="flat", padx=14, pady=9, cursor="hand2"
        ).pack(side="left", padx=(10, 0))

        tk.Button(
            btns, text="Subscription",
            command=lambda: launch_official_cli(wd_var.get(), "subscription"),
            font=(mono_font, 10), bg=BG_PANEL, fg=FG_BLUE, relief="flat", padx=14, pady=9, cursor="hand2"
        ).pack(side="left", padx=(10, 0))

        tk.Button(
            btns, text="← Back", command=show_welcome,
            font=(mono_font, 10), bg=BG_PANEL, fg=FG_WHITE, relief="flat", padx=14, pady=9, cursor="hand2"
        ).pack(side="right")

        root.bind("<Escape>", lambda _e: show_welcome())

    # --------------------------------------------------------------------------
    #  الطرفية الرئيسية المطابقة لـ Claude Code + تبديل النماذج + شريط التوكن الأصلي
    # --------------------------------------------------------------------------
    def show_terminal():
        state["view"] = "terminal"
        clear_view()
        root.unbind("<Key>")

        # --- الشريط العلوي المختصر للتبديل الفوري بين المزودين والنماذج ---
        top = tk.Frame(main_container, bg=BG_PANEL, padx=14, pady=7)
        top.pack(fill="x")

        tk.Label(top, text=f"Claude Code {APP_VERSION}", font=(mono_font, 10, "bold"), fg=FG_CORAL, bg=BG_PANEL).pack(side="left")

        # محوّل المزود (OpenRouter ⇄ DeepSeek)
        tk.Label(top, text="  │  Provider:", font=(mono_font, 9), fg=FG_GRAY, bg=BG_PANEL).pack(side="left")
        prov_var = tk.StringVar(value="OpenRouter" if state["provider"] == "openrouter" else "DeepSeek")
        prov_combo = ttk.Combobox(
            top, textvariable=prov_var, values=["OpenRouter", "DeepSeek"],
            state="readonly", width=11, style="Dark.TCombobox", font=(mono_font, 9)
        )
        prov_combo.pack(side="left", padx=(4, 8))

        # محوّل النماذج
        tk.Label(top, text="Model:", font=(mono_font, 9), fg=FG_GRAY, bg=BG_PANEL).pack(side="left")
        model_var = tk.StringVar(value=state["model"])
        model_combo = ttk.Combobox(
            top, textvariable=model_var, width=32, style="Dark.TCombobox", font=(mono_font, 9)
        )
        model_combo.pack(side="left", padx=(4, 8))

        def refresh_model_dropdown():
            pool = state["live_or_models"] if state["provider"] == "openrouter" else BUILTIN_MODELS["deepseek"]
            model_combo["values"] = [m["id"] for m in pool]
            model_var.set(state["model"])

        refresh_model_dropdown()

        # أزرار سريعة على اليمين
        tk.Button(
            top, text="← Welcome", command=show_welcome,
            font=(mono_font, 9), bg=BG_DARK, fg=FG_WHITE, relief="flat", padx=8, pady=2, cursor="hand2"
        ).pack(side="right")

        tk.Button(
            top, text="Official CLI ↗",
            command=lambda: launch_official_cli(state["work_dir"], state["provider"], state["model"]),
            font=(mono_font, 9), bg=BG_DARK, fg=FG_BLUE, relief="flat", padx=8, pady=2, cursor="hand2"
        ).pack(side="right", padx=(0, 6))

        def pick_workspace():
            d = filedialog.askdirectory(initialdir=state["work_dir"])
            if d:
                state["work_dir"] = d
                save_config({"work_dir": d})
                update_status_line()
                append_term(f"● Workspace changed to: {d}\n\n", "green")

        tk.Button(
            top, text="📁 Folder", command=pick_workspace,
            font=(mono_font, 9), bg=BG_DARK, fg=FG_WHITE, relief="flat", padx=8, pady=2, cursor="hand2"
        ).pack(side="right", padx=(0, 6))

        def open_keys_dialog():
            dlg = tk.Toplevel(root)
            dlg.title("إعدادات المفاتيح (API Keys)")
            dlg.configure(bg=BG_DARK)
            dlg.geometry("540x240")
            dlg.resizable(False, False)
            enable_windows_dark_titlebar(dlg)
            set_window_icon(dlg)

            f = tk.Frame(dlg, bg=BG_DARK, padx=20, pady=18)
            f.pack(fill="both", expand=True)

            tk.Label(f, text="OpenRouter API Key:", font=(mono_font, 9), fg=FG_WHITE, bg=BG_DARK, anchor="w").pack(fill="x")
            or_v = tk.StringVar(value=state["openrouter_key"])
            tk.Entry(f, textvariable=or_v, font=(mono_font, 9), bg=BG_INPUT, fg=FG_WHITE, insertbackground=FG_WHITE, relief="flat").pack(fill="x", ipady=5, pady=(2, 10))

            tk.Label(f, text="DeepSeek API Key:", font=(mono_font, 9), fg=FG_WHITE, bg=BG_DARK, anchor="w").pack(fill="x")
            ds_v = tk.StringVar(value=state["deepseek_key"])
            tk.Entry(f, textvariable=ds_v, font=(mono_font, 9), bg=BG_INPUT, fg=FG_WHITE, insertbackground=FG_WHITE, relief="flat").pack(fill="x", ipady=5, pady=(2, 14))

            def save_keys():
                state["openrouter_key"] = or_v.get().strip() or DEFAULT_OPENROUTER_KEY
                state["deepseek_key"] = ds_v.get().strip() or DEFAULT_DEEPSEEK_KEY
                save_config({"openrouter_key": state["openrouter_key"], "deepseek_key": state["deepseek_key"]})
                dlg.destroy()
                append_term("✓ Keys updated.\n\n", "green")

            tk.Button(f, text="حفظ", command=save_keys, font=("Segoe UI", 9, "bold"), bg=BTN_ACCENT, fg="#14111A", relief="flat", padx=18, pady=6, cursor="hand2").pack(side="right")

        tk.Button(
            top, text="🔑 Keys", command=open_keys_dialog,
            font=(mono_font, 9), bg=BG_DARK, fg=FG_GRAY, relief="flat", padx=8, pady=2, cursor="hand2"
        ).pack(side="right", padx=(0, 6))

        # --- مساحة المحادثة الرئيسية ---
        term = scrolledtext.ScrolledText(
            main_container, bg=BG_DARK, fg=FG_WHITE, insertbackground=FG_WHITE,
            font=(mono_font, 10), relief="flat", padx=20, pady=12, wrap="word"
        )
        term.pack(fill="both", expand=True)
        term.tag_config("coral", foreground=FG_CORAL)
        term.tag_config("blue", foreground=FG_BLUE)
        term.tag_config("gray", foreground=FG_GRAY)
        term.tag_config("dim", foreground=FG_DIM)
        term.tag_config("green", foreground=FG_GREEN)
        term.tag_config("warn", foreground=FG_WARN)
        term.tag_config("err", foreground=FG_ERR)
        term.tag_config("magenta", foreground=FG_MAGENTA)
        term.config(state="disabled")

        def append_term(text: str, tag: str | None = None):
            term.config(state="normal")
            if tag:
                term.insert("end", text, tag)
            else:
                term.insert("end", text)
            term.see("end")
            term.config(state="disabled")

        # --- سطر الحالة النشط فوق مربع الإدخال (مثل نبض Claude Code أثناء التفكير) ---
        activity_var = tk.StringVar(value="")
        activity_lbl = tk.Label(
            main_container, textvariable=activity_var,
            font=(mono_font, 9), fg=FG_CORAL, bg=BG_DARK, anchor="w", padx=20
        )
        activity_lbl.pack(fill="x")

        # --- مربع الإدخال المطابق لإطار Claude Code الأصلي (خط علوي + ❯ + خط سفلي) ---
        tk.Frame(main_container, bg=BORDER_DIM, height=1).pack(fill="x", padx=16)

        prompt_box = tk.Frame(main_container, bg=BG_DARK, padx=18, pady=6)
        prompt_box.pack(fill="x")

        tk.Label(prompt_box, text="❯ ", font=(mono_font, 11, "bold"), fg=FG_BLUE, bg=BG_DARK).pack(side="left")
        prompt_var = tk.StringVar()
        prompt_ent = tk.Entry(
            prompt_box, textvariable=prompt_var, font=(mono_font, 11),
            bg=BG_DARK, fg=FG_WHITE, insertbackground=FG_WHITE,
            relief="flat", highlightthickness=0, bd=0
        )
        prompt_ent.pack(side="left", fill="x", expand=True, ipady=4)
        prompt_ent.focus_set()

        send_btn = tk.Button(
            prompt_box, text="Send ↵", font=(mono_font, 9, "bold"),
            bg=BG_PANEL, fg=FG_CORAL, activebackground=BORDER_DIM, activeforeground=FG_WHITE,
            relief="flat", padx=10, pady=3, cursor="hand2"
        )
        send_btn.pack(side="right")

        tk.Frame(main_container, bg=BORDER_DIM, height=1).pack(fill="x", padx=16)

        # ======================================================================
        #  شريط الحالة الأصلي لـ Claude Code (أسفل مربع الإدخال مباشرةً!)
        #  يعرض: النموذج | المسار | نسبة ومؤشر السياق | التوكن (In/Out/Cache) | التكلفة $
        # ======================================================================
        status_row = tk.Frame(main_container, bg=BG_DARK, padx=18, pady=4)
        status_row.pack(fill="x")

        sl_model = tk.Label(status_row, text="", font=(mono_font, 9, "bold"), fg=FG_MAGENTA, bg=BG_DARK, cursor="hand2")
        sl_model.pack(side="left")

        sl_sep1 = tk.Label(status_row, text=" │ ", font=(mono_font, 9), fg=FG_DIM, bg=BG_DARK)
        sl_sep1.pack(side="left")

        sl_dir = tk.Label(status_row, text="", font=(mono_font, 9), fg=FG_WARN, bg=BG_DARK)
        sl_dir.pack(side="left")

        # الجانب الأيمن: استهلاك التوكن + السياق + التكلفة تماماً كشريط Claude Code الأصلي
        sl_cost = tk.Label(status_row, text="", font=(mono_font, 9, "bold"), fg=FG_WARN, bg=BG_DARK)
        sl_cost.pack(side="right")

        sl_sep_r1 = tk.Label(status_row, text=" │ ", font=(mono_font, 9), fg=FG_DIM, bg=BG_DARK)
        sl_sep_r1.pack(side="right")

        sl_tokens = tk.Label(status_row, text="", font=(mono_font, 9), fg=FG_GRAY, bg=BG_DARK)
        sl_tokens.pack(side="right")

        sl_sep_r2 = tk.Label(status_row, text=" │ ", font=(mono_font, 9), fg=FG_DIM, bg=BG_DARK)
        sl_sep_r2.pack(side="right")

        sl_ctx = tk.Label(status_row, text="", font=(mono_font, 9, "bold"), fg=FG_GREEN, bg=BG_DARK)
        sl_ctx.pack(side="right")

        # سطر التلميحات أسفل شريط الحالة
        hints_row = tk.Frame(main_container, bg=BG_DARK, padx=18, pady=2)
        hints_row.pack(fill="x", pady=(0, 6))
        tk.Label(
            hints_row,
            text="? for help  ·  /model switch  ·  /usage detailed tokens  ·  /clear reset  ·  /official claude.exe",
            font=(mono_font, 8), fg=FG_DIM, bg=BG_DARK, anchor="w"
        ).pack(side="left")

        sl_last_turn = tk.Label(hints_row, text="", font=(mono_font, 8), fg=FG_DIM, bg=BG_DARK, anchor="e")
        sl_last_turn.pack(side="right")

        def update_status_line():
            meta = get_model_meta(state["provider"], state["model"])
            prov_tag = "OpenRouter" if state["provider"] == "openrouter" else "DeepSeek"
            ctx_max = max(int(meta.get("context", 1_000_000)), 1)
            u = state["usage"]

            ctx_used = u["context_tokens"]
            pct = min(100.0, (ctx_used / ctx_max) * 100.0)
            filled = min(10, max(0, round(pct / 10.0)))
            bar = ("█" * filled) + ("░" * (10 - filled))

            if pct >= 80:
                ctx_color = FG_ERR
            elif pct >= 50:
                ctx_color = FG_WARN
            else:
                ctx_color = FG_GREEN

            folder_name = Path(state["work_dir"]).name or state["work_dir"]
            short_model = state["model"].replace("anthropic/", "")

            sl_model.config(text=f"● {short_model} ({prov_tag})")
            sl_dir.config(text=f"📁 {folder_name}")
            sl_ctx.config(text=f"ctx [{bar}] {pct:.1f}% ({format_tokens(ctx_used)}/{format_tokens(ctx_max)})", fg=ctx_color)
            sl_tokens.config(text=f"In: {format_tokens(u['total_in'])}  Out: {format_tokens(u['total_out'])}  Cache: {format_tokens(u['total_cache'])}")
            sl_cost.config(text=f"${u['total_cost']:.4f}")

            if u["turns"] > 0:
                sl_last_turn.config(
                    text=f"last turn: ↑{format_tokens(u['last_in'])} ↓{format_tokens(u['last_out'])} (${u['last_cost']:.4f})"
                )
            else:
                sl_last_turn.config(text="Ready · 0 turns")

        update_status_line()

        # --- نافذة أو أمر تبديل النماذج السريع ---
        def switch_provider_and_model(new_provider: str, new_model: str, announce: bool = True):
            state["provider"] = new_provider
            state["model"] = new_model
            prov_var.set("OpenRouter" if new_provider == "openrouter" else "DeepSeek")
            refresh_model_dropdown()
            save_config({"provider": state["provider"], "model": state["model"]})
            update_status_line()
            if announce:
                meta = get_model_meta(new_provider, new_model)
                append_term(
                    f"✓ Switched to [{prov_var.get()}] {new_model} "
                    f"(ctx {format_tokens(meta['context'])} · ${meta['in_price']:.2f}/${meta['out_price']:.2f} per 1M)\n\n",
                    "green",
                )

        def on_prov_selected(_evt=None):
            chosen_p = "openrouter" if prov_var.get() == "OpenRouter" else "deepseek"
            if chosen_p != state["provider"]:
                default_m = "anthropic/claude-fable-5.2" if chosen_p == "openrouter" else "deepseek-flash"
                switch_provider_and_model(chosen_p, default_m)

        def on_model_selected(_evt=None):
            m_id = model_var.get().strip()
            if not m_id:
                return
            if m_id in [m["id"] for m in BUILTIN_MODELS["deepseek"]]:
                switch_provider_and_model("deepseek", m_id)
            else:
                switch_provider_and_model("openrouter", m_id)

        prov_combo.bind("<<ComboboxSelected>>", on_prov_selected)
        model_combo.bind("<<ComboboxSelected>>", on_model_selected)
        model_combo.bind("<Return>", on_model_selected)

        def open_model_picker_modal(_evt=None):
            dlg = tk.Toplevel(root)
            dlg.title("تبديل النموذج والمزود (/model)")
            dlg.configure(bg=BG_DARK)
            dlg.geometry("660x470")
            enable_windows_dark_titlebar(dlg)
            set_window_icon(dlg)

            tk.Label(dlg, text="اختر النموذج (OpenRouter + DeepSeek):", font=("Segoe UI", 11, "bold"), fg=FG_CORAL, bg=BG_DARK, anchor="w", padx=16, pady=10).pack(fill="x")

            lb_frame = tk.Frame(dlg, bg=BG_DARK, padx=16)
            lb_frame.pack(fill="both", expand=True)

            lb = tk.Listbox(
                lb_frame, bg=BG_PANEL, fg=FG_WHITE, selectbackground=BTN_ACCENT,
                selectforeground="#14111A", font=(mono_font, 10), relief="flat", activestyle="none"
            )
            sb = tk.Scrollbar(lb_frame, command=lb.yview)
            lb.config(yscrollcommand=sb.set)
            lb.pack(side="left", fill="both", expand=True)
            sb.pack(side="right", fill="y")

            choices = []
            for m in state["live_or_models"]:
                choices.append(("openrouter", m["id"]))
                lb.insert("end", f"[OpenRouter] {m['id']}  —  ${m['in_price']:.2f}/${m['out_price']:.2f}/1M")
            for m in BUILTIN_MODELS["deepseek"]:
                choices.append(("deepseek", m["id"]))
                lb.insert("end", f"[DeepSeek]   {m['id']}  —  ${m['in_price']:.2f}/${m['out_price']:.2f}/1M")

            def confirm_pick(_e=None):
                sel = lb.curselection()
                if sel:
                    p, mid = choices[sel[0]]
                    switch_provider_and_model(p, mid)
                dlg.destroy()

            lb.bind("<Double-Button-1>", confirm_pick)
            lb.bind("<Return>", confirm_pick)

            btn_f = tk.Frame(dlg, bg=BG_DARK, padx=16, pady=12)
            btn_f.pack(fill="x")
            tk.Button(btn_f, text="تفعيل النموذج المختار", command=confirm_pick, font=("Segoe UI", 9, "bold"), bg=BTN_ACCENT, fg="#14111A", relief="flat", padx=16, pady=6, cursor="hand2").pack(side="right")
            tk.Button(btn_f, text="إغلاق", command=dlg.destroy, font=("Segoe UI", 9), bg=BG_PANEL, fg=FG_WHITE, relief="flat", padx=14, pady=6, cursor="hand2").pack(side="right", padx=(0, 8))

        sl_model.bind("<Button-1>", open_model_picker_modal)

        def print_usage_card():
            u = state["usage"]
            meta = get_model_meta(state["provider"], state["model"])
            ctx_max = max(int(meta.get("context", 1_000_000)), 1)
            pct = min(100.0, (u["context_tokens"] / ctx_max) * 100.0)
            filled = min(20, max(0, round(pct / 5.0)))
            bar = ("█" * filled) + ("░" * (20 - filled))
            prov_name = "OpenRouter" if state["provider"] == "openrouter" else "DeepSeek"
            append_term("──────────────────────────────────────────────────────────────\n", "gray")
            append_term("  Session Token Usage & Cost (/usage)\n", "coral")
            append_term(f"  Provider / Model : {prov_name} · {state['model']}\n", "blue")
            append_term(f"  Context Window   : [{bar}] {pct:5.1f}% ({u['context_tokens']:,} / {ctx_max:,})\n", "green" if pct < 50 else "warn")
            append_term(f"  Input Tokens     : {u['total_in']:,}\n", "gray")
            append_term(f"  Output Tokens    : {u['total_out']:,}\n", "gray")
            append_term(f"  Cache Read       : {u['total_cache']:,}\n", "gray")
            append_term(f"  Total Tokens     : {u['total_in'] + u['total_out']:,}\n", "gray")
            append_term(f"  Last Turn        : ↑ {u['last_in']:,} in  ↓ {u['last_out']:,} out  (${u['last_cost']:.4f})\n", "gray")
            append_term(f"  Session Cost     : ${u['total_cost']:.4f} USD\n", "warn")
            append_term("──────────────────────────────────────────────────────────────\n\n", "gray")

        # رسالة الترحيب داخل الطرفية
        append_term(f"Welcome to Claude Code {APP_VERSION}\n", "coral")
        append_term(f"{APP_CREDIT}\n", "gray")
        append_term(f"Provider : {'OpenRouter (Strongest Claude Models)' if state['provider'] == 'openrouter' else 'DeepSeek Direct'}\n", "gray")
        append_term(f"Model    : {state['model']}\n", "blue")
        append_term(f"Workspace: {state['work_dir']}\n\n", "gray")

        tools_schema = [
            {
                "name": "list_dir",
                "description": "List files and folders inside a directory path relative to workspace.",
                "input_schema": {
                    "type": "object",
                    "properties": {"path": {"type": "string", "description": "Directory path (default '.')"}},
                },
            },
            {
                "name": "read_file",
                "description": "Read text contents of a file.",
                "input_schema": {
                    "type": "object",
                    "properties": {"path": {"type": "string"}},
                    "required": ["path"],
                },
            },
            {
                "name": "write_file",
                "description": "Create or overwrite a file with content.",
                "input_schema": {
                    "type": "object",
                    "properties": {
                        "path": {"type": "string"},
                        "content": {"type": "string"},
                    },
                    "required": ["path", "content"],
                },
            },
            {
                "name": "edit_file",
                "description": "Replace an exact text snippet inside an existing file.",
                "input_schema": {
                    "type": "object",
                    "properties": {
                        "path": {"type": "string"},
                        "old_text": {"type": "string"},
                        "new_text": {"type": "string"},
                    },
                    "required": ["path", "old_text", "new_text"],
                },
            },
            {
                "name": "run_command",
                "description": "Run a PowerShell/shell command in the workspace.",
                "input_schema": {
                    "type": "object",
                    "properties": {"command": {"type": "string"}},
                    "required": ["command"],
                },
            },
        ]

        def resolve_safe_path(rel_or_abs: str) -> Path:
            p = Path(rel_or_abs)
            return p if p.is_absolute() else (Path(state["work_dir"]) / p)

        def confirm_on_main_thread(title: str, msg: str) -> bool:
            ev = threading.Event()
            ans = {"ok": False}

            def ask():
                ans["ok"] = messagebox.askyesno(title, msg)
                ev.set()

            root.after(0, ask)
            ev.wait()
            return ans["ok"]

        def execute_tool(name: str, inp: dict) -> str:
            try:
                if name == "list_dir":
                    p = resolve_safe_path(inp.get("path", "."))
                    entries = sorted(p.iterdir(), key=lambda x: (not x.is_dir(), x.name.lower()))
                    return "\n".join(("[DIR]  " if e.is_dir() else "[FILE] ") + e.name for e in entries[:200]) or "(empty)"
                if name == "read_file":
                    p = resolve_safe_path(inp["path"])
                    return p.read_text(encoding="utf-8", errors="replace")[:35000]
                if name == "write_file":
                    p = resolve_safe_path(inp["path"])
                    if not confirm_on_main_thread("Claude Code — Permission", f"هل تسمح بكتابة الملف:\n{p}؟"):
                        return "User denied file write permission."
                    p.parent.mkdir(parents=True, exist_ok=True)
                    p.write_text(inp["content"], encoding="utf-8")
                    return f"Written successfully: {p}"
                if name == "edit_file":
                    p = resolve_safe_path(inp["path"])
                    content = p.read_text(encoding="utf-8")
                    if inp["old_text"] not in content:
                        return "Error: old_text not found in file."
                    if not confirm_on_main_thread("Claude Code — Edit File", f"هل تسمح بتعديل الملف:\n{p}؟"):
                        return "User denied file edit permission."
                    updated = content.replace(inp["old_text"], inp["new_text"], 1)
                    p.write_text(updated, encoding="utf-8")
                    return f"Edited successfully: {p}"
                if name == "run_command":
                    cmd = inp["command"]
                    if not confirm_on_main_thread("Claude Code — Run Command", f"هل تسمح بتنفيذ الأمر:\n{cmd}؟"):
                        return "User denied command execution."
                    shell_cmd = ["powershell", "-NoProfile", "-Command", cmd] if os.name == "nt" else ["/bin/sh", "-c", cmd]
                    res = subprocess.run(
                        shell_cmd, cwd=state["work_dir"], capture_output=True, text=True,
                        timeout=60, creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0)
                    )
                    out = (res.stdout or "") + (res.stderr or "")
                    return f"Exit code: {res.returncode}\n{out[:15000]}"
                return f"Unknown tool: {name}"
            except Exception as e:
                return f"Tool error: {e}"

        def record_usage(usage_obj, provider: str, model_id: str):
            if not usage_obj:
                return
            in_tok = int(getattr(usage_obj, "input_tokens", 0) or 0)
            out_tok = int(getattr(usage_obj, "output_tokens", 0) or 0)
            cache_read = int(getattr(usage_obj, "cache_read_input_tokens", 0) or 0)
            cache_create = int(getattr(usage_obj, "cache_creation_input_tokens", 0) or 0)

            meta = get_model_meta(provider, model_id)
            turn_cost = (
                (in_tok * meta.get("in_price", 0.0))
                + (out_tok * meta.get("out_price", 0.0))
                + (cache_read * meta.get("cache_price", 0.0))
            ) / 1_000_000.0

            u = state["usage"]
            u["total_in"] += in_tok
            u["total_out"] += out_tok
            u["total_cache"] += cache_read
            u["context_tokens"] = in_tok + cache_read + cache_create + out_tok
            u["last_in"] = in_tok + cache_read + cache_create
            u["last_out"] = out_tok
            u["last_cache"] = cache_read
            u["last_cost"] = turn_cost
            u["total_cost"] += turn_cost
            u["turns"] += 1
            root.after(0, update_status_line)

        def smart_match_model(query: str) -> tuple[str, str] | None:
            q = query.strip().lower()
            aliases = {
                "fable": ("openrouter", "anthropic/claude-fable-5.2"),
                "opus": ("openrouter", "anthropic/claude-opus-5.5"),
                "sonnet": ("openrouter", "anthropic/claude-sonnet-5.5"),
                "haiku": ("openrouter", "anthropic/claude-haiku-5.5"),
                "deepseek": ("deepseek", "deepseek-flash"),
                "flash": ("deepseek", "deepseek-flash"),
                "v4-pro": ("deepseek", "deepseek-v4-pro"),
                "reasoner": ("deepseek", "deepseek-reasoner"),
                "chat": ("deepseek", "deepseek-chat"),
            }
            if q in aliases:
                return aliases[q]
            for m in BUILTIN_MODELS["deepseek"]:
                if m["id"].lower() == q:
                    return ("deepseek", m["id"])
            for m in state["live_or_models"]:
                if m["id"].lower() == q:
                    return ("openrouter", m["id"])
            if "/" in query:
                return ("openrouter", query.strip())
            return None

        def send_prompt(_event=None):
            if state["busy"]:
                return
            user_msg = prompt_var.get().strip()
            if not user_msg:
                return
            prompt_var.set("")

            if user_msg.startswith("/") or user_msg == "?":
                parts = user_msg.split(maxsplit=1)
                cmd = parts[0].lower()
                arg = parts[1].strip() if len(parts) > 1 else ""
                if cmd in ("/help", "?"):
                    append_term(
                        "Commands:\n"
                        "  /model [name]  — تبديل النموذج (مثال: /model fable | opus | sonnet | deepseek-flash | deepseek-reasoner)\n"
                        "  /usage | /cost — عرض تفاصيل استهلاك التوكن والتكلفة\n"
                        "  /clear         — مسح المحادثة وتصفير السياق\n"
                        "  /cd <path>     — تغيير مجلد العمل\n"
                        "  /official      — فتح claude.exe الرسمي في PowerShell بنفس المفاتيح والنموذج\n"
                        "  /keys          — عرض أو تعديل مفاتيح OpenRouter و DeepSeek\n"
                        "  /phone         — Hamza Link: ربط الهاتف والتحكم بالحاسوب منه وبالعكس\n\n",
                        "gray",
                    )
                elif cmd in ("/usage", "/cost", "/context", "/stats"):
                    print_usage_card()
                elif cmd == "/model":
                    if not arg:
                        open_model_picker_modal()
                    else:
                        matched = smart_match_model(arg)
                        if matched:
                            switch_provider_and_model(matched[0], matched[1])
                        else:
                            append_term(f"! لم يتم التعرف على النموذج '{arg}'. اكتب /model لفتح القائمة.\n\n", "warn")
                elif cmd == "/clear":
                    state["history"].clear()
                    state["usage"]["context_tokens"] = 0
                    term.config(state="normal")
                    term.delete("1.0", "end")
                    term.config(state="disabled")
                    update_status_line()
                    append_term("Session cleared. Context reset to 0.\n\n", "gray")
                elif cmd == "/cd" and arg:
                    np = Path(arg)
                    if np.exists() and np.is_dir():
                        state["work_dir"] = str(np)
                        save_config({"work_dir": state["work_dir"]})
                        update_status_line()
                        append_term(f"Changed directory to: {state['work_dir']}\n\n", "green")
                    else:
                        append_term(f"Directory not found: {arg}\n\n", "err")
                elif cmd == "/official":
                    launch_official_cli(state["work_dir"], state["provider"], state["model"])
                elif cmd == "/keys":
                    open_keys_dialog()
                elif cmd in ("/phone", "/link", "/mobile"):
                    append_term(f"📱 {launch_hamza_link()}\n\n", "green")
                else:
                    append_term(f"Unknown command: {user_msg} (type /help)\n\n", "warn")
                return

            append_term(f"❯ {user_msg}\n", "blue")
            state["history"].append({"role": "user", "content": user_msg})
            state["busy"] = True
            activity_var.set(f"✻ Working with {state['model']}...")

            def agent_worker():
                try:
                    import anthropic

                    active_provider = state["provider"]
                    active_model = state["model"]
                    if active_provider == "openrouter":
                        client = anthropic.Anthropic(
                            api_key=state["openrouter_key"],
                            base_url=OPENROUTER_BASE_URL,
                            default_headers={
                                "HTTP-Referer": "https://claudecode.local",
                                "X-Title": "Claude Code Desktop",
                            },
                        )
                    else:
                        client = anthropic.Anthropic(
                            api_key=state["deepseek_key"],
                            base_url=DEEPSEEK_BASE_URL,
                        )

                    system_prompt = (
                        f"You are Claude Code running in workspace: {state['work_dir']} on {platform.system()}. "
                        "Use the provided tools concisely to inspect files, write/edit code, and run commands."
                    )

                    max_tok = 4096
                    while True:
                        try:
                            resp = client.messages.create(
                                model=active_model,
                                max_tokens=max_tok,
                                system=system_prompt,
                                tools=tools_schema,
                                messages=state["history"],
                            )
                        except Exception as api_err:
                            err_s = str(api_err)
                            # في حال كان الرصيد على OpenRouter يسمح بعدد توكن أقل من 4096 في الخطوة
                            m_afford = re.search(r"can only afford\s+(\d+)", err_s)
                            if m_afford and max_tok > 512:
                                affordable = max(256, min(2048, int(m_afford.group(1)) - 64))
                                max_tok = affordable
                                root.after(0, lambda mt=max_tok: append_term(f"  ! Adjusting max_tokens to {mt} based on available balance...\n", "warn"))
                                resp = client.messages.create(
                                    model=active_model,
                                    max_tokens=max_tok,
                                    system=system_prompt,
                                    tools=tools_schema,
                                    messages=state["history"],
                                )
                            else:
                                raise

                        record_usage(getattr(resp, "usage", None), active_provider, active_model)
                        assistant_blocks = []
                        tool_results = []

                        for block in resp.content:
                            btype = getattr(block, "type", "")
                            if btype == "thinking":
                                th_item = {"type": "thinking", "thinking": getattr(block, "thinking", "")}
                                sig = getattr(block, "signature", None)
                                if sig:
                                    th_item["signature"] = sig
                                assistant_blocks.append(th_item)
                                th_text = getattr(block, "thinking", "").strip()
                                if th_text:
                                    first_line = th_text.splitlines()[0][:105]
                                    root.after(0, lambda p=first_line: append_term(f"  ∴ {p}...\n", "gray"))
                            elif btype == "redacted_thinking":
                                assistant_blocks.append({"type": "redacted_thinking", "data": getattr(block, "data", "")})
                            elif btype == "text":
                                assistant_blocks.append({"type": "text", "text": block.text})
                                txt = block.text
                                root.after(0, lambda t=txt: append_term(t + "\n"))
                            elif btype == "tool_use":
                                assistant_blocks.append({
                                    "type": "tool_use",
                                    "id": block.id,
                                    "name": block.name,
                                    "input": block.input,
                                })
                                tname = block.name
                                root.after(0, lambda n=tname: (
                                    activity_var.set(f"● Running {n}..."),
                                    append_term(f"  ● {n}\n", "warn"),
                                ))
                                result_str = execute_tool(block.name, block.input)
                                preview_r = result_str.strip().splitlines()[0][:100] if result_str.strip() else "done"
                                root.after(0, lambda pr=preview_r: append_term(f"    ⎿ {pr}\n", "gray"))
                                tool_results.append({
                                    "type": "tool_result",
                                    "tool_use_id": block.id,
                                    "content": result_str,
                                })

                        state["history"].append({"role": "assistant", "content": assistant_blocks})
                        if tool_results:
                            root.after(0, lambda: activity_var.set(f"✻ Thinking ({active_model})..."))
                            state["history"].append({"role": "user", "content": tool_results})
                        else:
                            break

                    root.after(0, lambda: append_term("\n"))
                except Exception as exc:
                    root.after(0, lambda e=exc: append_term(f"[Error] {e}\n  Tip: جرّب التبديل لنموذج آخر من القائمة بالأعلى أو عبر /model opus أو /model deepseek-flash\n\n", "err"))
                finally:
                    state["busy"] = False
                    root.after(0, lambda: activity_var.set(""))

            threading.Thread(target=agent_worker, daemon=True).start()

        prompt_ent.bind("<Return>", send_prompt)
        send_btn.config(command=send_prompt)

    show_welcome()
    root.mainloop()


# ==============================================================================
#  نقطة الدخول الرئيسية
# ==============================================================================

if __name__ == "__main__":
    if "--app" in sys.argv:
        run_app()
    elif "--phone" in sys.argv:
        configure_console_utf8()
        safe_print(launch_hamza_link())
    elif "--setup" in sys.argv:
        run_setup()
    else:
        build_self_to_exe()