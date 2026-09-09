# School Report Maker — Project Plan

**Owner:** DeryCode Tech (asiimwe3)
**Repo:** `asiimwe3/school-report-maker`
**Updated:** 2026-09-09
**Status:** Phase 0 complete (scaffold) — still in planning/revision

## 1. Vision

A school report making system that works **100% offline** and serves **every Ugandan school level** — primary (PLE), O-level (new competency-based UCE) and A-level (UACE):

- The **administrator** uses a fast desktop application (Compose Desktop, JVM) — instant startup, no browser, no server, no internet. Generates professional, print-ready report cards for an entire school in seconds.
- **Class teachers** enter marks on a Kotlin Android app on their phones — no data bundles, no accounts.
- When there is a connection, the teacher app **automatically sends** new marks to the admin console (cloud relay). When there is no connection, the same data moves with a small **bundle file** (USB, Bluetooth). Works offline, syncs itself online.

Built for many schools: each school creates its own offline account (name, level, logo, motto, colors, signatures), picks its own report card design from a template library, and keeps its own data in its own file.

Target market: Ugandan primary and secondary schools, starting with Kyenjojo and western Uganda.

## 2. Grading: three UNEB-aligned schemes, all admin-adjustable

Every school can tune its own boundaries in Settings; defaults follow current UNEB practice (as of the 2025 UCE release):

**PRIMARY (PLE):**
- Grades 1-9 per subject (Distinction 1-2, Credit 3-5, Pass 6-7, Fail 8-9)
- Aggregate = best 4 subjects; Divisions 1-4 / U

**O-LEVEL (new competency-based UCE, per UNEB 2025):**
- Letter grades A-E; divisions and the old 1-9 scale are gone
- A: 80-100% Exceptional | B: 70-79% Outstanding | C: 60-69% Satisfactory | D: 50-59% Basic (minimum competency) | E: below 50% Elementary
- Final grade = 20% continuous assessment + 80% end-of-term exam
- Result indicators: Result 1 (qualified — at least one D), Result 2 (missing compulsory subjects/project work), Result 3 (all E)

**A-LEVEL (UACE):**
- Principal subjects A-E with points (A=6 B=5 C=4 D=3 E=2 O=1 F=0); the new NCDC proposal (A=5..E=1) ships as a selectable preset once adopted
- 3 principal subjects + subsidiary GP; subsidiary earns its point if the grade is better than P7

**ADMIN CONTROL:** every boundary, point value and label lives in an editable **GradingScheme** stored in the data file. Admins can adjust boundaries, rename labels, create custom schemes, and attach a scheme to each level/class. Old report cards keep the scheme they were printed with (grade history), so past reports never re-render differently.

## 3. Why this architecture

- Admin app: Compose Desktop (JVM) — native speed, fully offline, no Electron bloat, same Kotlin code as the app.
- Teacher app: Kotlin + Jetpack Compose (native) — fast on cheap phones, real offline, no WebView.
- Shared logic: `core` Kotlin JVM module — one grading + report engine for both platforms; a rule change happens once.
- Storage: single JSON file with atomic writes — whole database fits on a USB stick, trivially backed up, no DB admin.
- Reports: HTML template, print to PDF from any machine; template fully customizable.
- Sync: automatic cloud relay (Firebase) whenever online + bundle file export/import as the offline fallback.
- CI: GitHub Actions builds the desktop JAR and Android APK on every push to main.

Non-goals for v1: multi-user logins, fees/payroll (that's School Sync Manager's territory), public online portals. Automatic online sync IS a goal (Phase 3).

## 4. Module layout

core/            shared Kotlin module: models (with Level + GradingScheme), the three
                 UNEB grading schemes, ReportEngine (HTML report cards), JsonStore.
desktop-admin/   Compose Desktop admin console: Dashboard, Students, Classes, Subjects,
                 Marks, Grading Schemes, Reports, Backup.
teacher-app/     Android app: Home, Marks entry per subject/assessment, Export bundle,
                 auto-send when online (Phase 3).

## 5. Data model (v1)

- School: name, address, motto, head teacher, emblem.
- Level: Primary | O-Level | A-Level (with attached GradingScheme).
- SchoolClass: name, stream, level, class teacher.
- Student: names, sex, student number, guardian name + phone, level, active flag.
- Subject: code + name, principal/subsidiary flag (A-level), compulsory flag.
- MarkEntry: student + subject + term (1-3) + assessment type + score. Assessment types, covering secondary continuous assessment and integration activities: EXAM (BOT / MID / EOT), CA (Continuous Assessment, 20% of the UCE final grade), AOI (Activity of Integration), PROJECT (Project Work — certificate completion requirement). Primary uses EXAM only; the type list per level is admin-editable.
- SchoolAccount: offline-created school profile — name, level, logo image, motto, colors, head-teacher + class-teacher signature images, grading scheme, chosen report template, school data file. Many schools per install, each fully independent.
- ReportTemplate: library of card designs per level (PLE / UCE / UACE x multiple layouts: Classic, Modern, Compact, Plain). Each school picks one and customizes (logo size, colors, header layout, comments position).
- Comments & Signatures: class-teacher and head-teacher comment boxes per student per term (typed or auto-suggested), plus signature images uploaded or drawn in-app; rendered on the report card.
- GradingScheme: per-level boundaries, labels, points, aggregate rules, result rules — fully editable by admin.

Everything serializes into one JSON file (school-data.json), atomic-write protected.

## 6. Sync design — offline first, auto-send when online

Both sides keep their local file as the primary database. The cloud is only a relay:

1. Teacher app: marks save instantly to local storage, then queue for sending.
2. When the phone detects a connection (WorkManager + connectivity check), it auto-uploads queued bundles to the school's Firebase space in the background. No teacher action needed.
3. Admin console: on launch and every few minutes while online, it pulls waiting bundles and merges them (newest-wins conflict rule, every merge logged).
4. If no internet ever: the manual bundle file (USB/Bluetooth) still works exactly the same.
5. Sync is opt-in per school; each school's space is separate.

## 7. Roadmap

### Phase 0 — Scaffold (DONE, 2026-09-09)
- Monorepo with core, desktop-admin, teacher-app, CI building both.
- Working desktop app: dashboard, add students, generate report cards to HTML.
- Working teacher APK: marks entry, bundle export.
- CI builds desktop JAR + debug APK on every push to main.

### Phase 1 — Grading engine + admin console full strength (target: 1-2 weeks)
- GRADING SCHEMES: implement the three UNEB schemes (PLE 1-9/divisions; UCE A-E competency-based with 20/80 CA/exam weighting and Result 1-3 indicators; UACE points) as data, not code
- Grading scheme editor screen — admin adjusts boundaries, labels, points; scheme history kept
- Levels: classes/students attached to Primary / O-Level / A-Level; reports render per scheme
- Classes, Subjects, Teachers CRUD screens
- Marks entry grid in desktop (bulk, keyboard-first, paste-from-spreadsheet)
- Import teacher bundle, merge marks (newest wins, everything logged)
- Backup/restore with auto-rotating backups
- School setup: OFFLINE school account creation — name, level, logo upload, motto, colors, signature images; multiple schools per install with a school switcher
- Class-teacher + head-teacher comments and signature capture (upload or draw), printed on every report card

### Phase 2 — Report polish (target: week 3)
- Template LIBRARY: multiple designs per level (PLE / UCE / UACE x Classic, Modern, Compact, Plain) — each school selects and customizes its own report design, so every school's cards look different
- Reports show: logo, comments, signatures, CA/AoI/Project marks, and the level's grading summary
- Emblem + head-teacher signature on cards, term dates
- Print directly to PDF from desktop, no browser step
- Batch ZIP export for a whole class
- Class summary sheets per term (top performers, subject analysis)
- SMS-ready short summary text for guardians

### Phase 3 — Teacher app + automatic sync (target: week 4)
- Class/subject picker wired to bundle import from admin
- Secondary assessment entry: Exams (BOT/MID/EOT) + Continuous Assessment (20%) + Activity of Integration + Project Work — all four recorded per student per subject
- AUTO-SEND: when online, queued marks upload to the cloud relay automatically (WorkManager, retry on failure)
- Admin console AUTO-PULL: desktop fetches waiting teacher bundles on launch + periodic refresh, merges newest-wins
- Report preview on phone (same HTML templates in WebView)
- Auto-save drafts; everything still works with zero internet
- Versioned import so old bundles never corrupt newer data
- Teacher APK gains INTERNET permission at this phase (offline-only builds stay available for schools that refuse any internet)

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
- UCE new-curriculum project work (CW) marks entry

## 8. Performance targets

- Desktop cold start: under 2 s
- Full class (60 students x 8 subjects) reports: under 3 s
- Whole school (1000 students) batch: under 30 s
- Teacher APK cold start: under 1.5 s on low-end Android

## 9. Security and privacy

- v1 teacher APK ships without the INTERNET permission — data physically cannot leave the phone. Phase 3 auto-sync builds add internet strictly for the school's own relay space; offline-only builds remain.
- Local files remain the primary database on both sides; the cloud relay holds copies of bundles only, per school space.
- Desktop stores everything locally in the user's home folder.
- No student data ever sent to any third party; no ads, no analytics in either app.

## 10. CI pipeline (live)

- desktop-admin job: builds JAR, uploads artifact.
- teacher-app job: builds debug APK, uploads artifact.
- Both run on every push to main; release tags come in Phase 4.

## 11. Screen inventory (mockups in design/)

ADMIN CONSOLE (design/screens-admin.html):
1. School Setup — offline account creation with logo, level, motto, colors, signatures
2. Dashboard
3. Students
4. Marks Grid — Exams (BOT/MID/EOT) + CA + AoI + Project columns
5. Grading Scheme Editor — admin-adjustable boundaries, labels, points
6. Report Template Library — multiple designs, per-school customization
7. Report Preview — logo, grading table, comments, signature lines
8. Sync & Backup — auto-pull status, bundle import, backups

TEACHER APP (design/screens-teacher.html):
1. Home — class summary + auto-send status
2. Marks Entry — Exams | CA | AoI | Project tabs
3. Comments & Signature — per-student term comments, drawn signatures
4. Report Preview & Send — card preview, auto-send status, USB export
