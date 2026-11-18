# 🚀 دليل تشغيل وتثبيت Claude Code Desktop (الإصدار v2.1.70)

> **تصميم: Hamza Alfarai** — Designed by Hamza Alfarai

مرحباً بك في الحزمة المتكاملة لتشغيل واجهة وشعار **Claude Code** على نظام **Windows 10 / 11**، مع دعم التثبيت التلقائي، والتبديل الفوري بين النماذج (OpenRouter & DeepSeek)، ومراقبة استهلاك التوكن لحظياً في نفس موقعها بالنسخة الأصلية.

---

## ⬇️ التحميل

حمّل الحزمة كاملة (البرنامج + **أحدث إصدار من Python**) في ملف مضغوط واحد من صفحة **[Releases — v2.1.70](https://github.com/hamza756-pory/free-clude-code/releases/tag/v2.1.70)**:

### 👉 [ClaudeCode-HamzaAlfarai.zip](https://github.com/hamza756-pory/free-clude-code/releases/download/v2.1.70/ClaudeCode-HamzaAlfarai.zip)

فك الضغط في أي مجلد، ثم اتبع الخطوات بالأسفل.

---

## 📦 محتويات الملف المضغوط

- **`python-3.x.y-amd64.exe`** — **أحدث إصدار مستقر من Python** (المثبّت الرسمي من python.org، مرفق داخل الحزمة — يعمل بدون إنترنت)
- **`python-manager-26.4.msix`** — Python Install Manager (أحدث إصدار رسمي) كخيار بديل
- **`SHA256SUMS.txt`** — بصمات SHA-256 للتحقق من سلامة ملفات بايثون
- **`claude_code_suite.py`** — الكود الرئيسي للبرنامج ومُنشئ ملف التثبيت (`EXE`)
- **`install_python.bat`** + **`tools/install_python.ps1`** — تثبيت بايثون المرفق بضغطة واحدة
- **`run_claude_code.bat`** — تشغيل البرنامج / بناء ملف التثبيت بضغطة واحدة
- **`README.md`** — هذا الدليل التعليمي

> رقم إصدار بايثون المرفق مكتوب في [`release/PYTHON_VERSION.txt`](release/PYTHON_VERSION.txt).
> يُبنى الملف المضغوط تلقائياً عبر GitHub Actions (`.github/workflows/build-package.yml`) الذي يجلب أحدث إصدار من python.org في كل مرة.

---

## ⚠️ الخطوة 1 (إلزامية): تثبيت بايثون

### الطريقة التلقائية (موصى بها — بدون إنترنت):
1. اضغط **نقرتين مزدوجتين** على **`install_python.bat`**
2. سيتم تثبيت **أحدث إصدار من Python** المرفق داخل الحزمة مع إضافته إلى `PATH` وتثبيت `pip` و`py` launcher.

### الطريقة البديلة: Python Install Manager
```powershell
install_python.bat -UseManager
```
أو اضغط نقرتين على **`python-manager-26.4.msix`** ← **Install**، ثم في PowerShell:
```powershell
py install default
py list
```

---

## ▶️ الخطوة 2: بناء وتشغيل البرنامج

اضغط نقرتين على **`run_claude_code.bat`** أو اكتب في PowerShell داخل المجلد:

```powershell
py claude_code_suite.py
```

سيقوم البرنامج تلقائياً بـ:
1. تثبيت المكتبات المطلوبة (`anthropic`, `pyinstaller`, `pillow`, ...)
2. إنشاء الشعار والأيقونة
3. تحميل `claude.exe` الرسمي مسبقاً
4. بناء `ClaudeCode.exe`
5. تجميع `dist\ClaudeCode_Setup.exe` وتشغيل معالج التثبيت (اختيار مجلد التثبيت + الاختصارات + PATH)

أوامر إضافية:

```powershell
py claude_code_suite.py --app     # تشغيل الواجهة مباشرة بدون بناء
py claude_code_suite.py --setup   # تشغيل معالج التثبيت فقط
```

---

## 🔑 الخطوة 3: مفاتيح API (OpenRouter / DeepSeek)

لأسباب أمنية **لا توجد أي مفاتيح داخل الكود المرفوع على GitHub**. أضف مفاتيحك بإحدى الطريقتين:

- **من داخل البرنامج:** اكتب الأمر `/keys` (تُحفظ محلياً على جهازك فقط في `~/.claude_code_gui_config.json`)
- **أو عبر متغيرات البيئة:**
  ```powershell
  setx OPENROUTER_API_KEY "sk-or-v1-..."
  setx DEEPSEEK_API_KEY  "sk-..."
  ```

---

## ⌨️ الأوامر داخل البرنامج

| الأمر | الوظيفة |
|------|---------|
| `/model [name]` | تبديل النموذج (`fable` / `opus` / `sonnet` / `deepseek-flash` / `deepseek-reasoner`) |
| `/usage` أو `/cost` | عرض استهلاك التوكن والتكلفة |
| `/clear` | مسح المحادثة وتصفير السياق |
| `/cd <path>` | تغيير مجلد العمل |
| `/official` | فتح `claude.exe` الرسمي في PowerShell |
| `/keys` | عرض أو تعديل مفاتيح OpenRouter و DeepSeek |
| `/help` | عرض المساعدة |

---

<p align="center"><b>تصميم Hamza Alfarai</b> · Designed by Hamza Alfarai</p>
