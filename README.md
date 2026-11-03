# School Report Maker

Offline-first school report making system, in one repo:

- **desktop-admin** — a fast, fully-offline Compose Desktop (JVM) console for the school administrator. No internet, no server, no accounts. One JSON file is the whole database.
- **desktop-teacher** — the same teacher app for a laptop (Compose Desktop). Marks entry with a keyboard and Enter to move down the class, an attendance register, my classes, the timetable sheet, and the same bundle files as the phone. Installer: `SchoolReportMakerTeacher-<version>.msi`. Data lives in `%USERPROFILE%\SchoolReportMakerTeacher`, so a laptop and a phone can both feed the console.
- **teacher-app** — a Kotlin Android app for class teachers to enter marks on their phones. Works fully offline; the only network use is optional school-join/sync and in-app updates, which is why it requests INTERNET, CAMERA (barcode/photo capture) and REQUEST_INSTALL_PACKAGES (self-update). See docs/PERMISSIONS.md.
- **core** — shared Kotlin module used by both: data models, Ugandan A–E/aggregate grading, and the HTML report-card engine (BOT/MID/EOT per subject, totals, averages, class positions, division, auto remarks).

Read the roadmap: [PLAN.md](PLAN.md)

## Quick start (desktop)

```
gradle :desktop-admin:run
```

Data is saved to `~/SchoolReportMaker/school-data.json`. Generate a whole class's report cards from the Reports screen — they open/print straight to PDF from any browser.

## Build the teacher app (laptop)

```bash
gradle :desktop-teacher:packageMsi   # Windows installer
gradle :desktop-teacher:run          # run it while developing
```

## Build the teacher APK

```
gradle :teacher-app:assembleDebug
```

CI builds both on every push to `main` and uploads the JAR + APK as artifacts.

## How the two sides sync (offline)

The teacher app exports a small **bundle file** (USB / Bluetooth / any file share). The admin console imports it and merges the marks. No cloud, no accounts, nothing to pay for.

## Relationship to other projects

- This is the dedicated **report making** system. The older `school-sync-manager` (web) and `school-report-system` (Replit) repos are untouched.
- Grading logic follows the same Uganda PLE-style scheme used across DeryCode education products.
