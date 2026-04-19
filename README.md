# Ad Cleaner (Android)

Lightweight Android app targeting **API 35+** that scans local storage artifacts for advertisement-related cache/tracker residue and allows **manual, user-confirmed cleanup only**.

## Implemented highlights
- Scan tab for local smart scanning across installed apps.
- Ad Sources tab that fetches EasyList/EasyPrivacy/AdGuard/custom-host style lists.
- Results tab with per-item details (app, file path, size, type, risk), filters, and explicit selection.
- Safe deletion workflow with confirmation dialog and post-cleanup summary.
- Settings tab with transparent session logs.

## Tech stack
- Kotlin + MVVM
- Jetpack Compose Material 3 UI
- Retrofit + OkHttp
- Coroutines

## Build
```bash
./gradlew assembleDebug
```

APK output:
`app/build/outputs/apk/debug/app-debug.apk`

## Permissions
- `READ_EXTERNAL_STORAGE` (legacy read on older versions)
- `MANAGE_EXTERNAL_STORAGE` (advanced/manual broad file analysis when granted)

The app performs no background deletion and no telemetry collection.
