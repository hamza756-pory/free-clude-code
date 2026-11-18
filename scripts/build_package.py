# -*- coding: utf-8 -*-
"""
Build release/ClaudeCode-HamzaAlfarai.zip
تصميم: Hamza Alfarai

- يكتشف أحدث إصدار مستقر من Python على python.org ويحمّل مثبّت ويندوز الرسمي (amd64)
- يحمّل أحدث Python Install Manager (.msix) من python.org
- يضغط ملفات البرنامج + بايثون في ملف ZIP واحد
"""

import hashlib
import json
import os
import re
import shutil
import sys
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PKG_NAME = "ClaudeCode-HamzaAlfarai"
OUT_DIR = ROOT / "release"
STAGE = ROOT / "_stage" / PKG_NAME
CACHE = ROOT / "_stage" / "downloads"
FTP = "https://www.python.org/ftp/python/"
UA = {"User-Agent": "ClaudeCode-HamzaAlfarai-builder"}

PACKAGE_FILES = [
    "README.md",
    "claude_code_suite.py",
    "install_python.bat",
    "run_claude_code.bat",
    "tools/install_python.ps1",
    "run_hamza_link.bat",
    "hamza_link",  # تكامل الحاسوب والهاتف (مجلد كامل)
]


def http_get(url: str, headers=None) -> bytes:
    req = urllib.request.Request(url, headers={**UA, **(headers or {})})
    with urllib.request.urlopen(req, timeout=120) as r:
        return r.read()


def exists(url: str) -> bool:
    try:
        req = urllib.request.Request(url, method="HEAD", headers=UA)
        with urllib.request.urlopen(req, timeout=60) as r:
            return r.status == 200
    except Exception:
        return False


def download(url: str, dest: Path) -> Path:
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists() and dest.stat().st_size > 0:
        return dest
    print(f"  -> {url}")
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=600) as r, open(dest, "wb") as f:
        shutil.copyfileobj(r, f)
    print(f"     {dest.name}: {dest.stat().st_size / 1e6:.1f} MB")
    return dest


def sha256(p: Path) -> str:
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def latest_python():
    """أحدث إصدار مستقر (X.Y.Z) له مثبّت ويندوز amd64 على python.org."""
    listing = http_get(FTP).decode("utf-8", "replace")
    versions = sorted(
        {tuple(map(int, m)) for m in re.findall(r'href="(\d+)\.(\d+)\.(\d+)/"', listing)},
        reverse=True,
    )
    for v in versions[:15]:
        ver = ".".join(map(str, v))
        exe = f"{FTP}{ver}/python-{ver}-amd64.exe"
        if exists(exe):
            return ver, exe
        emb = f"{FTP}{ver}/python-{ver}-embed-amd64.zip"
        if exists(emb):
            return ver, emb
    raise SystemExit("Could not find a stable Python release on python.org")


def latest_pymanager():
    """أحدث إصدار مستقر من Python Install Manager (من GitHub python/pymanager)."""
    headers = {"Accept": "application/vnd.github+json"}
    tok = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    if tok:
        headers["Authorization"] = f"Bearer {tok}"
    try:
        data = json.loads(http_get("https://api.github.com/repos/python/pymanager/releases/latest", headers))
        ver = data["tag_name"].lstrip("v")
    except Exception as e:
        print(f"  ! GitHub API failed ({e}); falling back to 26.4")
        ver = "26.4"
    url = f"{FTP}pymanager/python-manager-{ver}.msix"
    return (ver, url) if exists(url) else (None, None)


def main():
    print("=" * 60)
    print(f"  Building {PKG_NAME}.zip  —  تصميم Hamza Alfarai")
    print("=" * 60)

    if STAGE.exists():
        shutil.rmtree(STAGE)
    STAGE.mkdir(parents=True)

    for rel in PACKAGE_FILES:
        src = ROOT / rel
        dst = STAGE / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        if src.is_dir():
            shutil.copytree(src, dst, ignore=shutil.ignore_patterns("__pycache__", "*.pyc"))
        else:
            shutil.copy2(src, dst)

    print("\n[1/3] Latest Python for Windows...")
    py_ver, py_url = latest_python()
    py_file = download(py_url, CACHE / Path(py_url).name)
    shutil.copy2(py_file, STAGE / py_file.name)

    print("\n[2/3] Latest Python Install Manager...")
    pm_ver, pm_url = latest_pymanager()
    pm_file = None
    if pm_url:
        pm_file = download(pm_url, CACHE / Path(pm_url).name)
        shutil.copy2(pm_file, STAGE / pm_file.name)

    sums = [f"{sha256(STAGE / p.name)}  {p.name}" for p in (py_file, pm_file) if p]
    (STAGE / "SHA256SUMS.txt").write_text("\n".join(sums) + "\n", encoding="utf-8")

    print("\n[3/3] Creating ZIP...")
    OUT_DIR.mkdir(exist_ok=True)
    out_zip = OUT_DIR / f"{PKG_NAME}.zip"
    if out_zip.exists():
        out_zip.unlink()
    with zipfile.ZipFile(out_zip, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for f in sorted(STAGE.rglob("*")):
            if f.is_file():
                z.write(f, f"{PKG_NAME}/{f.relative_to(STAGE).as_posix()}")

    info = [
        f"Python {py_ver}  ({py_file.name})",
        f"Python Install Manager {pm_ver}  ({pm_file.name})" if pm_file else "Python Install Manager: not available",
        *sums,
    ]
    (OUT_DIR / "PYTHON_VERSION.txt").write_text("\n".join(info) + "\n", encoding="utf-8")

    print("\n".join(info))
    print(f"\nOK: {out_zip}  ({out_zip.stat().st_size / 1e6:.1f} MB)")


if __name__ == "__main__":
    sys.exit(main())
