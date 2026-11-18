# 🚀 دليل تشغيل وتثبيت Claude Code Desktop (الإصدار v2.1.70)

> **تصميم: Hamza Alfarai** — Designed by Hamza Alfarai

مرحباً بك في الحزمة المتكاملة لتشغيل واجهة وشعار **Claude Code** على نظام **Windows 10 / 11**، مع دعم التثبيت التلقائي، والتبديل الفوري بين النماذج (OpenRouter & DeepSeek)، ومراقبة استهلاك التوكن لحظياً في نفس موقعها بالنسخة الأصلية.

---

## ⬇️ التحميل

حمّل الحزمة كاملة في ملف مضغوط واحد:

- **[`ClaudeCode-HamzaAlfarai.zip`](release/ClaudeCode-HamzaAlfarai.zip)** (داخل المستودع)
- أو من صفحة **Releases** في GitHub.

فك الضغط في أي مجلد، ثم اتبع الخطوات بالأسفل.

---

## 📱 جديد: تطبيق أندرويد أصلي — Claude Code (Mobile)

تطبيق Kotlin + Jetpack Compose بنفس واجهة التيرمينال، يعمل بالنماذج المجانية من OpenRouter مع بث مباشر، أوامر Slash، إرفاق ملفات، وسجل محادثات.
التفاصيل والتحميل: **[`android/README.md`](android/README.md)** · ملف `ClaudeCodeMobile.apk` في صفحة **Releases**.

---

## 📦 محتويات المجلد

- **`claude_code_suite.py`** — الكود الرئيسي للبرنامج ومُنشئ ملف التثبيت (`EXE`)
- **`install_python.bat`** — يثبّت **Python Install Manager 26.4** (أحدث إصدار رسمي) ثم **أحدث إصدار من Python** تلقائياً
- **`tools/install_python.ps1`** — السكربت الذي يستدعيه الملف السابق
- **`run_claude_code.bat`** — تشغيل البرنامج / بناء ملف التثبيت بضغطة واحدة
- **`README.md`** — هذا الدليل التعليمي

> ملف `python-manager-26.4.msix` يُحمَّل تلقائياً من موقع **python.org** الرسمي عند تشغيل `install_python.bat`.
> ويمكنك تحميله يدوياً من: https://www.python.org/ftp/python/pymanager/python-manager-26.4.msix ووضعه في نفس المجلد.

---

## ⚠️ الخطوة 1 (إلزامية): تثبيت بايثون عبر `python-manager-26.4.msix`

لكي يعمل التطبيق وتتم عملية البناء والتحويل إلى `EXE` بنجاح، يجب أولاً تثبيت **Python Install Manager 26.4** (الأداة الرسمية لإدارة وتشغيل بيئات بايثون على ويندوز 10 و11).

### الطريقة التلقائية (موصى بها):
1. اضغط **نقرتين مزدوجتين** على **`install_python.bat`**
2. سيقوم السكربت بـ:
   - تحميل `python-manager-26.4.msix` من python.org (إن لم يكن موجوداً في المجلد)
   - تثبيته
   - تثبيت **أحدث إصدار مستقر من Python** عبر الأمر `py install default`

### الطريقة اليدوية:
1. اضغط **نقرتين مزدوجتين** على الملف:
   ```text
   python-manager-26.4.msix
   ```
2. اضغط **Install / تثبيت**.
3. افتح PowerShell واكتب:
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
