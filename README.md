# Handwritten Diary AI

An Android app that scans handwritten diary pages, runs on-device OCR and Gemma 4 AI, extracts tasks and knowledge, syncs to Notion Free, and backs up to Google Drive.

## Features

- **On-device OCR** — ML Kit Text Recognition, fully offline
- **Gemma 4 AI** — On-device LLM for OCR correction, task extraction, knowledge extraction
- **Notion Free Sync** — Tasks + knowledge entries synced as Notion pages; attachments >5MB skipped gracefully
- **Google Drive Backup** — Encrypted archive stored in private `appDataFolder`; versioned and restorable
- **Local-first** — All scans stay on-device; sync is text-only by default
- **WorkManager jobs** — Auto-sync every 6h on Wi-Fi, auto-backup daily on Wi-Fi+charging

## Screens

| Screen | Description |
|---|---|
| Onboarding | 5-slide intro |
| Home | Session list + stats dashboard |
| OCR Review | Per-page text edit + confidence badge |
| AI Results | Approve extracted tasks and knowledge |
| Tasks | Full task list with status filter |
| Knowledge Base | Tag-filtered knowledge entries |
| Archive Search | Full-text search across all data |
| Sync Center | Notion sync status + manual trigger |
| Backup & Restore | Drive backup history + restore |
| Settings | Notion token, Drive sign-in, automation toggles |

## Tech Stack

- Kotlin + Jetpack Compose + Material3
- Room (SQLite) + Hilt DI + WorkManager
- ML Kit Text Recognition
- Retrofit + OkHttp (Notion API)
- Google API Client (Drive)
- EncryptedSharedPreferences (Keystore)

## Getting Started

1. Clone repo and open in Android Studio
2. Build: `./gradlew assembleDebug`
3. Install APK on Android 8+ device
4. Settings → paste Notion integration token + database IDs
5. Settings → Sign in with Google for Drive backup

## Gemma 4 Integration

Place a Gemma 4 `.task` model file (MediaPipe LLM Inference format) in `app/src/main/assets/` or configure the path in Settings.  
Without the model file, the app uses a rule-based fallback for task/knowledge extraction.

## Releases

See [Releases](../../releases) for pre-built APKs.
