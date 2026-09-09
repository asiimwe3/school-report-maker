# School Report Maker — Project Plan

**Owner:** DeryCode Tech (asiimwe3)
**Repo:** `asiimwe3/school-report-maker`
**Created:** 2026-09-09
**Status:** Phase 0 complete (scaffold)

## 1. Vision

A school report making system that works **100% offline**:

- The **administrator** uses a fast desktop application (Compose Desktop, JVM) — instant startup, no browser, no server, no internet. Generates professional, print-ready report cards for an entire school in seconds.
- **Class teachers** enter marks on a Kotlin Android app on their phones — no data bundles, no internet permission, no accounts.
- The two sides exchange data with a small **bundle file** (USB, Bluetooth, file share). No cloud. No subscriptions. Nothing to break.

Target market: Ugandan primary and secondary schools (PLE/UEB grading), starting with Kyenjojo and western Uganda.

## 2. Why this architecture

- Admin app: Compose Desktop (JVM) — native speed, fully offline, no Electron bloat, same Kotlin code as the app.
- Teacher app: Kotlin + Jetpack Compose (native) — fast on cheap phones, real offline, no WebView.
- Shared logic: `core` Kotlin JVM module — one grading + report engine for both platforms; a rule change happens once.
- Storage: single JSON file with atomic writes — whole database fits on a USB stick, trivially backed up, no DB admin.
- Reports: HTML template → print to PDF from any machine; template fully customizable.
- Sync: bundle file export/import — no internet, no server cost, school controls the data.
- CI: GitHub Actions builds the desktop JAR and Android APK on every push to main.

Non-goals for v1: cloud sync, multi-user logins, fees/payroll (that's School Sync Manager's territory), online portals.

## 3. Module layout

core/            shared Kotlin module: models, Uganda PLE-style grading (A-E, aggregates,
                 divisions, positions), ReportEngine (HTML report cards with BOT/MID/EOT,
                 totals, averages, remarks), JsonStore (atomic single-file database).
desktop-admin/   Compose Desktop admin console: Dashboard, Students, Classes, Subjects,
                 Marks, Reports, Backup.
teacher-app/     Android app, no INTERNET permission: Home, Marks entry per subject and
                 assessment, bundle export.

## 4. Data model (v1)

- School: name, address, motto, head teacher, emblem.
- SchoolClass: name, stream, class teacher.
- Student: names, sex, student number, guardian name + phone, active flag.
- Subject: code + name (MTC, ENG, SCI, SST...), compulsory.
- MarkEntry: student + subject + term (1-3) + assessment (BOT / MID / EOT) + score 0-100.
- Grading: A-E grades, 1-7 aggregate points, Division 1-U, auto remarks, class positions.

Everything serializes into one JSON file (school-data.json), atomic-write protected.

## 5. Roadmap

### Phase 0 — Scaffold (DONE, 2026-09-09)
- Monorepo with core, desktop-admin, teacher-app, CI building both.
- Working desktop app: dashboard, add students, generate real report cards to HTML.
- Working teacher APK: marks entry (BOT/MID/EOT per subject), bundle export.
- Grading + report engine done and shared.

### Phase 1 — Admin console to full strength (target: 1-2 weeks)
- Classes, Subjects, Teachers CRUD screens
- Marks entry grid in desktop (bulk, keyboard-first, paste-from-spreadsheet)
- Import teacher bundle, merge marks (newest wins, everything logged)
- Backup screen: save/restore data file + auto-rotating backups
- School setup: name, motto, emblem, signature images

### Phase 2 — Report polish (target: week 3)
- Template options: emblem, head-teacher signature, term dates
- Print directly to PDF from desktop, no browser step
- Batch ZIP export for a whole class
- Class summary sheets per term (top performers, subject analysis)
- SMS-ready short summary text for guardians

### Phase 3 — Teacher app full strength (target: week 4)
- Class/subject picker wired to bundle import from admin
- Report preview on phone (same HTML template in WebView)
- Auto-save drafts; fully offline forever
- Versioned import so old bundles never corrupt newer data

### Phase 4 — Distribution (target: week 5)
- Desktop installers: msi/exe (Windows), deb (Linux) via Compose packaging
- Signed release APK via GitHub Actions (Property Masters signing pattern)
- One-page school onboarding guide (English + Runyoro/Luganda summaries)
- Demo school pre-loaded for a live first open

### Phase 5 — Beyond v1 (backlog)
- Multiple schools in one install (switcher)
- End-of-year summary card (Terms 1+2+3 combined)
- Optional encrypted data file
- LAN sync (desktop + phone on same Wi-Fi, still no internet)
- Per-teacher PIN lock on the phone app

## 6. Performance targets

- Desktop cold start: under 2 s
- Full class (60 students x 8 subjects) reports: under 3 s
- Whole school (1000 students) batch: under 30 s
- Teacher APK cold start: under 1.5 s on low-end Android

## 7. Security and privacy

- Teacher APK has no INTERNET permission — data physically cannot leave the phone.
- Desktop stores everything locally in the user's home folder.
- School can move/encrypt its own data; we hold nothing.
- No student data ever sent to any third party.

## 8. CI pipeline (live)

- desktop-admin job: builds JAR, uploads artifact.
- teacher-app job: builds debug APK, uploads artifact.
- Both run on every push to main; release tags come in Phase 4.
