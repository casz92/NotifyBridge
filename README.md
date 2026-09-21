# NotifyBridge

NotifyBridge is a native Android application designed to intercept and forward push notifications and SMS messages to an external HTTP(S) server based on customizable conditions and RegEx filters.

## Key Features

1. **Rules Dashboard (Create, Edit, Delete)**:
   - Define criteria by source (**SMS**, **Installed Apps**, or **IMAP / Gmail**).
   - **Targeted Regex Filtering (`regexMatchFields`)**: Target regular expressions to specific fields (e.g., Title/Text in Apps, Sender/Recipient/Body in SMS, and From/To/Subject/Body in IMAP).
   - Application picker with **asynchronous background loading**.
   - Organized tabs: **General**, **Headers** (with autocomplete suggestions), and **Body** (with JSON / Text support).
   - Dynamic template variables: `{not_type}`, `{not_title}`, `{sms_sender}`, `{not_text}`, `{sms_text}`, `{package_name}`, `{timestamp}`, `{device_uuid}`, `{imap_subject}`, `{imap_body}`, and `{global_NAME}`.
2. **Rule Import & Export (JSON)**:
   - Export and import rule backups in `.json` files natively with integrated **version control** (version 1).
3. **Organized Dispatch Queue (Active & History Tabs)**:
   - **Active**: Dispatches that are pending (`PENDING`), in progress (`PROCESSING`), or failed with retries (`FAILED`), featuring a **"Cancel All"** button.
   - **History**: Completed (`SUCCESS`) or cancelled (`CANCELLED`) dispatches, with single-item deletion and **"Clear History"** support.
4. **Battery Optimization & Auto-Start**:
   - Dedicated card in Settings to check battery state and request exemption from battery optimizations (`Doze mode`).
   - `BootReceiver` listener for auto-start upon device boot or reboot (`BOOT_COMPLETED`).
5. **Preset Rules & Payload Specification**:
   - Includes default preset templates for detecting payments from **Nequi** and **Bancolombia** (App push and SMS). See [PAYLOADS.md](PAYLOADS.md) for field dictionaries, JSON schemas, and real-world payload examples.

## Tech Stack

- **Language**: Kotlin (1.9.24)
- **UI Framework**: Jetpack Compose (Material 3)
- **Background Tasks**: `WorkManager` & `NotificationListenerService` & `BootReceiver`
- **Local Database**: `Room`
- **Global Preferences**: `DataStore` (Preferences)
- **Network Client**: `OkHttp`

---

## Quick Build & Run

### Option 1: Direct Run on Device/Emulator (`run.bat`)
Run the script in Windows Command Prompt:
```cmd
run.bat
```
This script compiles the debug APK, detects any connected device/emulator via `adb`, installs the APK, and launches the application automatically.

### Option 2: Build APK (`build.bat`)
Run the interactive script:
```cmd
build.bat
```
Allows you to choose between building a **Debug** or **Release** APK and copies the generated binary directly to the project root directory.
