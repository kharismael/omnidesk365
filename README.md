# OmniDesk 365

**OmniDesk 365** is an Android client designed specifically for **Android Tablets** to bring the **full PC-like Desktop Microsoft 365 (Office)** experience. 

While the official mobile Office apps on Android tablets often feel stripped-down and limited compared to a PC, **OmniDesk 365** uses the **Mozilla GeckoView** desktop engine to deliver the complete desktop web workspace for Word, Excel, PowerPoint, and SharePoint—complete with full desktop toolbars and features.

Created by **Lujie**.

---

## ✨ Key Features

- **Desktop-Class Rendering Engine**: Powered by Mozilla `GeckoView` with desktop User-Agent spoofing, delivering the full desktop interface of Microsoft 365 (`m365.cloud.microsoft`).
- **Editor Zone Radar (Smart Pull-to-Refresh)**: Includes a `SwipeRefreshLayout` that automatically detects when you enter a document editor (`doc.aspx` or `docid`) and disables pull-to-refresh to prevent accidental page reloads while editing documents.
- **Smart Protocol & App Router**:
  - Intercepts `ms-word:`, `ms-excel:`, and `ms-powerpoint:` URI schemes and routes them to the local Microsoft 365 app if installed.
  - Intercepts Microsoft Teams links (`teams.microsoft.com` / `msteams:`) and opens them directly in the native Microsoft Teams app (with Play Store fallback).
- **Direct Stream Download Hijacker**: Captures file downloads directly from the GeckoView response stream to bypass cookie/authentication walls, automatically detects the proper document extension (`.docx`, `.xlsx`, `.pptx`, `.pdf`), and saves files straight to the device's `Downloads` folder via `MediaStore`.
- **Deep Link Support**: Directly opens `office.com`, `microsoft365.com`, and `*.sharepoint.com` links from external apps.
- **Offline Fallback**: Detects network connectivity on startup and provides a quick shortcut to launch the local Microsoft 365 Copilot app when offline.
- **Immersive Fullscreen UI**: Automatically hides system bars for maximum workspace visibility and includes double-back-to-exit protection.

---

## 🛠️ Tech Stack & Requirements

- **Language**: Kotlin
- **Minimum SDK**: Android 8.1 (API 27)
- **Target / Compile SDK**: API 36
- **Core Engine**: `org.mozilla.geckoview:geckoview-omni`
- **Build System**: Gradle (Kotlin DSL)

---

## 🚀 Getting Started (Build & Run)

1. **Clone this repository**:
   ```bash
   git clone https://github.com/kharismael/omnidesk365.git
   ```
2. **Open in Android Studio**:
   - Launch Android Studio and select **Open**.
   - Select the cloned project directory.
3. **Sync Gradle**:
   - Wait for Android Studio to download dependencies (including the GeckoView engine from Mozilla's Maven repository).
4. **Run the App**:
   - Connect an Android device (with USB Debugging enabled) or start an Android Emulator.
   - Click **Run** (`Shift + F10`).

---

## ⚠️ Disclaimer

**OmniDesk 365** is an independent, unofficial open-source project and is **not** affiliated with, endorsed by, or sponsored by Microsoft Corporation. Microsoft, Microsoft 365, Office, Word, Excel, PowerPoint, SharePoint, and Microsoft Teams are registered trademarks of Microsoft Corporation.

---

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.
