# 📱 Claude Code (Mobile) — Native Android

> **تصميم: Hamza Alfarai** — Designed by Hamza Alfarai

تطبيق أندرويد **أصلي** (Kotlin + Jetpack Compose + Material 3 — بدون WebView) يحاكي واجهة **Claude Code** في التيرمينال، ويعمل عبر **OpenRouter API** بالنماذج **المجانية**.

## ⬇️ التحميل

- من صفحة **[Releases](https://github.com/hamza756-pory/free-clude-code/releases)** حمّل `ClaudeCodeMobile.apk` وثبّته (فعّل "التثبيت من مصادر غير معروفة").
- أو من تبويب **Actions → Android APK → Artifacts** لآخر بناء.

## 🔑 أول تشغيل

1. تظهر شاشة الترحيب (Pixel Art + "Select login method").
2. اختر **1. OpenRouter API key** والصق مفتاحك (`sk-or-v1-…`) أو **2. Paste from clipboard**.
3. يتم التحقق من المفتاح عبر `GET /api/v1/key` ثم حفظه **مشفّراً** بـ `EncryptedSharedPreferences` (AES-256, Android Keystore).

> لا يوجد أي مفتاح مضمّن في الكود أو في الـ APK. يمكنك تغيير المفتاح أو حذفه من ⚙ Settings.

## ✨ الميزات

| الميزة | التفاصيل |
|---|---|
| واجهة تيرمينال | خط JetBrains Mono، خلفية `#1A1921`، كورال `#D97775`، أزرق `#7AA2F7`، فواصل منقطة |
| بث مباشر (SSE) | `stream: true` مع تأثير كتابة حرفاً بحرف + مؤشر وامض + زر ■ للمقاطعة |
| نماذج مجانية | جلب تلقائي من `/api/v1/models` وتصفية `:free` أو `pricing.prompt == 0` مع ترتيب نماذج البرمجة أولاً |
| تمييز الأكواد | Syntax Highlighting لأغلب اللغات + زر `[copy]` لكل كتلة كود |
| أوامر Slash | `/help` `/clear` `/model` `/export [md\|txt]` `/attach` `/new` `/history` `/settings` `/status` |
| إرفاق ملفات | Storage Access Framework — يُقرأ محتوى الملف ويُرسل في السياق (حتى 200KB) |
| Shortcut Bar | `Tab` `/` `{` `}` `(` `)` `<` `>` `[` `]` `;` `=` … فوق لوحة المفاتيح (Tab يُكمل الأوامر) |
| السجل | Room Database — جلسات متعددة، فتح/حذف، تصدير |
| الأخطاء | رسائل تيرمينال واضحة لـ 401 / 402 / 429 / 502 / 503 / انقطاع الشبكة مع تلميح للحل |

## 🛠️ البناء محلياً

المتطلبات: Android Studio (Ladybug أو أحدث) + JDK 17.

```bash
cd android
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

## 🗂️ هيكل المشروع

```
android/
├── build.gradle.kts                 # Project-level
├── settings.gradle.kts
├── gradle/libs.versions.toml        # Version catalog
└── app/
    ├── build.gradle.kts             # App-level
    └── src/main/
        ├── AndroidManifest.xml      # INTERNET permission
        ├── res/ (font: JetBrains Mono, adaptive pixel-mascot icon, themes)
        └── java/com/hamza/claudecode/mobile/
            ├── ClaudeCodeApp.kt / MainActivity.kt
            ├── data/
            │   ├── db/          AppDatabase, ChatDao, ChatEntities (Room)
            │   ├── network/     OpenRouterClient (OkHttp + SSE), DTOs, OpenRouterException
            │   ├── repository/  ChatRepository, ModelCatalog
            │   └── security/    SecureKeyStore (EncryptedSharedPreferences)
            ├── domain/          SlashCommand
            ├── util/            AttachmentReader, ConversationExporter, MarkdownParser
            └── ui/
                ├── AppNavHost.kt
                ├── theme/       Color, Type (monospace), Theme
                ├── components/  PixelScene, CodeBlock, SyntaxHighlighter, MessageItem,
                │                ShortcutBar, ModelPickerSheet, TerminalWidgets
                ├── screens/     WelcomeScreen, ChatScreen, HistoryScreen, SettingsScreen
                └── viewmodel/   ChatViewModel, KeyViewModel, AppViewModelProvider
```

Font: JetBrains Mono (SIL Open Font License, see `FONT_LICENSE_OFL.txt`).
