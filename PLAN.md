# DeryCode School Report Maker — Product & Engineering Plan

**Owner:** DeryCode Tech (asiimwe3)
**Repo:** `asiimwe3/school-report-maker`
**Updated:** 2026-09-09
**Build approach:** ONE continuous delivery — all modules are built together, not in phases. The build order below (Section 63) defines dependency order, not time-boxed phases.

## 1. Product Overview

DeryCode School Report Maker is an offline-first school academic reporting system designed for schools that need to:

- Register and manage students
- Organize students by level, class and stream
- Manage subjects and combinations
- Enter continuous assessment and examination marks
- Calculate grades, points, aggregates and positions
- Capture teacher and head-teacher comments
- Capture digital signatures
- Generate professional student report cards
- Work without a permanent internet connection
- Synchronize and back up school data when connectivity becomes available
- Export/import school data through USB or file bundles

The system consists of:

1. Core Academic Engine
2. Admin Console
3. Teacher App
4. Report Generation Engine
5. Offline Database
6. Sync & Backup Engine
7. Authentication & Permissions
8. Audit & Security System

## 2. Product Principles

**Offline First** — The system must remain fully usable without internet access. Internet is an enhancement, not a requirement. Teachers must be able to open classes, view students, enter marks, enter comments, draw signatures, preview reports, generate reports and export data while completely offline.

**Local First** — Important academic data must be stored locally on the device. The application must not depend on a live server for basic academic operations.

**Safe by Default** — Marks and academic records are sensitive. The system must validate data, prevent accidental deletion, maintain audit records, support backups and restore, and restrict access by role.

**Configurable** — Different schools use different grading schemes, subjects, report designs, assessment structures, classes, streams and academic calendars. These must be configurable rather than hard-coded.

## 3. System Architecture

```text
┌─────────────────────────────────────┐
│           ADMIN CONSOLE             │
├─────────────────────────────────────┤
│             TEACHER APP             │
├─────────────────────────────────────┤
│          APPLICATION CORE           │
│  Students · Classes/Streams ·        │
│  Subjects · Teachers · Marks ·      │
│  Grading · Results · Comments ·     │
│  Reports                            │
├─────────────────────────────────────┤
│       OFFLINE DATA LAYER            │
├─────────────────────────────────────┤
│      SYNC / BACKUP ENGINE           │
├─────────────────────────────────────┤
│        EXPORT / IMPORT              │
└─────────────────────────────────────┘
```

## 4. Core Academic Engine

The Core Engine is the foundation of the entire application. The UI must not contain independent academic calculation logic — all calculations must happen through reusable core services.

**School Engine** — school name, logo, motto, address, telephone, email, registration info, school type, colors, head teacher, school stamp, report configuration.

**Academic Year Engine** — academic year, terms, current term, term start/end dates, next term date, term status. A school must be able to create a new academic year without destroying previous records; historical academic records must remain accessible.

## 5. Student Management

Each student must have a unique internal ID. Student profile: admission number, first/middle/last name, gender, date of birth, photo, parent/guardian + telephone, address, status. Possible statuses: Active, Transferred, Withdrawn, Graduated, Suspended, Deceased. Student records must never be permanently deleted through normal UI actions — use archive/soft-delete.

## 6. Class & Stream Management

Level → Class → Stream → Students:

```text
O-Level
 ├── Senior 1
 │    ├── S1 East
 │    └── S1 West
A-Level
 ├── Senior 5
      ├── PCB · PCM · HEG
```

## 7. Student Promotion

End-of-year promotion (S1→S2 etc.): select academic year → source class → review students → destination → confirm → create new enrollment records. Students who leave are marked appropriately rather than promoted. Promotion must be transactional: it either completes fully or makes no changes.

## 8. Subject Management

Subjects support: name, code, level, category, compulsory/optional, maximum marks, active/inactive, teacher assignment.

## 9. A-Level Combinations

Combinations (PCM, PCB, HEG…) contain principal subjects, subsidiary subjects, General Paper and custom school subjects.

## 10. Teacher Management

Teacher profile: ID, name, photo, username, PIN/password, subjects, classes, streams, role, signature. Assignments link a teacher to subject(s), class(es) and stream(s).

## 11. User Roles

Minimum roles: Super Admin (full access), School Admin (school configuration), Head Teacher (review/approve results, comments, sign reports), Teacher (assigned classes/marks/comments), Data Entry User (marks data entry with limited admin). Permissions must be configurable.

## 12. Authentication

Username/password, PIN, device authentication, logout, session timeout, account activation/deactivation. Offline credentials must be securely stored locally; passwords/PINs must never be stored as plain text.

## 13. Marks Engine

Configurable assessment components (default: BOT, MID, EOT, CA, AoI, Project), configurable by school/level/subject with per-subject weights.

## 14. Marks Entry

Teacher selects Class → Stream → Subject → Term → Assessment, then receives a grid of students × components. Keyboard-friendly data entry.

## 15. Marks Validation

Validate numeric values, min/max, decimals where allowed, missing values, duplicates, invalid components. Mark > maximum → ERROR; blank → allowed only when configured.

## 16. Absent / Missing Marks

The system must distinguish 0, ABS, MISSING, EXEMPT and NOT APPLICABLE — these must not be treated as the same thing.

## 17. Marks Locking

Marks workflow: Draft → Submitted → Reviewed → Approved → Locked. Once locked, ordinary teachers cannot change marks; an authorized administrator can unlock. Every unlock/change must be audited.

## 18–19. Grading Scheme Engine

Grading schemes are fully configurable: name, level, assessment rules, grade boundaries, labels, points, remarks. Core ships starter schemes for PLE (1–9 grades, aggregates, divisions), the new competency-based UCE (A–E, 20% CA + 80% exam, Result 1/2/3 indicators) and UACE (A–E principal points + subsidiary rules). Each school selects which scheme applies to level/class/subject/academic year. Boundaries must never be hard-coded into calculation logic.

## 20. Result Calculation Engine

Deterministic and testable pipeline:

```text
Raw Marks → Assessment Calculation → Subject Total → Grade
        → Grade Point → Aggregate → Overall Result
```

## 21. Ranking

Configurable ranking: overall class position, stream position, subject position, level position. Ties handled according to the configured ranking method (1, 2, 2, 4).

## 22. Attendance

Optional module: total school days, days present, days absent, attendance percentage.

## 23. Student Comments

Subject teacher, class teacher and head teacher comments; manually entered, selected from predefined comments, or auto-generated based on performance.

## 24. Digital Signatures

Signatures for subject teacher, class teacher and head teacher — drawn or uploaded, stored securely, reusable across reports.

## 25–27. Report Template Engine

Template-driven reports controlling page size, margins, logo position, school/student info, subject table, grading table, comments, signatures, attendance, promotion info and footer. Template library with create/duplicate/edit/archive/default/preview, assignable by level, class, term or school. Preview must match the printed output: logo, marks, grades, points, aggregate, position, comments, signatures, grading table, school stamp.

## 28–29. PDF & Printing

Individual, class, stream, level and whole-school batch PDF generation; A4; layout, fonts, tables, logo and signatures preserved.

## 30. Report Approval

Workflow: teacher enters marks → submits → admin reviews → results calculated → head teacher approves → report generated. Approved reports are versioned.

## 31–33. Offline Database & Data Model

Local database (single JSON file, atomic writes) storing school, academic years, terms, levels, classes, streams, students, enrollments, parents, teachers, assignments, subjects, combinations, assessment components, marks, grading schemes, results, comments, signatures, templates, reports, users, roles, permissions, audit logs, sync queue and backups. Relationships use stable IDs. Students are not permanently tied to one class — enrollment records (Student → Enrollment → Academic Year → Term → Class → Stream) preserve history.

## 34–36. Sync Engine

Asynchronous synchronization with a local sync queue: local change → sync queue → upload when internet available → server confirmation. No academic operation may fail because sync is unavailable. Conflicts (e.g. two different marks for one entry) are never silently overwritten — both versions are preserved and an authorized resolution is required, audited. Sync status shows last sync, pending, failed, offline and conflicts (e.g. "✓ All changes saved", "↑ 4 changes waiting to sync", "⚠ Sync failed").

## 37–39. Backup, Restore & USB Transfer

Automatic periodic local backups, manual backups, full school bundle export (configuration, students, classes, subjects, teachers, marks, results, templates, signatures, settings). Restore takes an automatic safety backup first and warns that it replaces current data. Bundles are versioned, validated, integrity-checked and optionally encrypted.

## 40–41. Import / Export

CSV import (students, teachers, subjects, marks) with preview → validate → show errors → confirm; no partial corruption. Export: student/marks/results CSV, report PDFs, full backups.

## 42–43. Audit Log & Security

Every sensitive operation (mark entered/changed, unlock, transfer, scheme change, report approved, restore) records user, action, entity, old/new value, timestamp, device. Security: secure authentication, role-based permissions, encrypted sensitive local data where practical, secure backups, no plaintext passwords, safe file validation, protection against unauthorized import.

## 44. Admin Console (design/screens-admin.html) — 20 screens total

1. School Setup — offline school account: name, logo, levels, motto, colors, address, contacts, signatures, stamp, academic year, current term
2. Dashboard — year/term, totals by class/stream, teachers, subjects, marks completion, reports ready, pending sync, backup status
3. Students — list, filters (level/class/stream), search, add/edit/archive, import/export, enrollment history
4. Classes & Streams — levels, classes, streams, A-level combinations, subject/student/teacher assignment
4b. Subjects — add/edit, code, level, class, teacher, compulsory flag, assessment components
4c. Teachers — list, add/edit, assign subjects/classes/streams/role, signatures, activation
5. Marks Grid — year/term/level/class/stream/subject selection, assessment columns, bulk entry, validation, missing marks, submission, locking
6. Grading Scheme Editor — create schemes, edit boundaries/labels/points/remarks, preview, assign to level/class/subject
7. Report Template Library — designs, create, duplicate, edit, preview, default, assign
8. Report Preview — pick one student/term/template, preview before batch generation
9. Sync & Backup — online/offline status, last sync, pending, conflicts, create/restore backup, export/import bundle
10. Settings — language, theme, currency, notifications, auto-sync, auto-backup frequency, backups to keep
11. Results Review — class/student results, missing marks, calculation review, approve, lock, reopen with permission
12. Report Generation — batch-generate report cards for a class/term/template, tracks frozen scheme version per report
13. Import Data — bring in students (CSV) without leaving the admin console
14. Export Data — export students, marks or results (CSV) for external use
15. Audit Log — full filterable history of every mutation (who/what/when/old→new)
16. Reports — browse previously generated reports (student, term, template, scheme version, approval state)
17. Backup History — list of local rotating backups, restore any of them (auto safety-backup before restore)
18. User Management — accounts/roles for everyone who can log into the admin console or teacher app (uses the Teacher/Role model — a "user" IS a teacher/admin account)
19. Notifications — in-app alerts (missing marks, sync failures, backup reminders) — surfaced from settings.notificationsEnabled
20. School Calendar — term dates, exams, holidays, meetings, deadlines

## 45. Teacher App (design/screens-teacher.html)

1. Home — teacher name, assigned classes/subjects, current term, mark completion, pending work, sync status
2. Marks Entry — Class → Stream → Subject → Assessment → grid; Exams | CA | AoI | Project tabs generated dynamically from school configuration
3. Comments & Signature — per-student comments, class teacher comments, draw/reuse signature
4. Report Preview & Send — student/class preview, PDF, auto-send, USB export

## 46. Teacher Restrictions

Teachers only access authorized classes, streams, subjects and terms; they cannot modify grading schemes, school configuration, other teachers' assignments or locked marks.

## 47. Auto-Send

Completed data transmits automatically when sync is available: marks entered → saved locally → sync queue → sent when online. Auto-send never deletes the local copy.

## 48. Error Handling

Understandable errors for every module ("Unable to save mark. Please check the value." / "Report cannot be generated. 3 students have missing marks." / "Sync failed. Your data is safely saved on this device."). Never show raw technical errors to normal users.

## 49–50. Performance & Integrity

Responsive with 10,000+ students, hundreds of teachers/subjects, multiple academic years, large marks datasets; efficient grid rendering. Multi-step operations use transactions: promotion either completes fully or makes no changes.

## 51–52. Versioning & Reproducibility

Templates, grading schemes and reports are version-aware. A generated report retains references to academic year, term, student, enrollment, grading scheme version, template version and result version — changing a current scheme never alters previously approved reports.

## 53–54. Testing Strategy

Unit tests (grade/aggregate/position/assessment calculation, validation), integration tests (student → enrollment → marks → result; marks → report; backup → restore; export → import; sync), UI tests (admin/teacher/offline workflows), regression tests for every calculation-engine change. Deterministic fixtures: normal student, missing marks, absent, zero, maximum, boundary grades, ties, combinations, incomplete results. Do not rely on visual inspection to validate calculations.

## 55–58. UI Principles, Navigation, Status & Notifications

UI must be simple, fast, mobile/tablet/desktop/touch/keyboard friendly with obvious primary actions and no unnecessary animations. Admin navigation: Dashboard, Students, Teachers, Classes, Subjects, Marks, Results, Grading, Reports, Templates, Sync & Backup, Settings. Teacher navigation: Home, My Classes, Marks, Comments, Reports, Sync, Profile. Consistent status terminology: Draft, Submitted, Reviewed, Approved, Locked, Synced, Pending, Failed, Conflict. Notifications for missing marks, validation failures, submission, approvals, sync/backup events — never interrupting data entry.

## 59. Initial Seed Data

Starter grading schemes are configurations only; schools can edit or replace them. No hard-coded assumptions in the calculation engine.

## 60. Build Plan — one continuous delivery

All modules below are built together as a single delivery (dependency order, not phases):

Database → Core Models → Academic Engine → Grading Engine → Result Engine → Admin Console → Teacher App → Report Engine → Backup/Import/Export → Sync → Security/Audit → Production Hardening.

Do not start by building every screen independently — build the underlying core first.

## 61. Definition of Done

A feature is complete only when: UI exists, core logic exists, local storage works, validation works, permissions work, offline operation works, errors are handled, audit requirements are implemented where applicable, tests exist, data survives restart, backup/restore works where applicable, and sync works where applicable.

## 62. Critical Engineering Rules

1. Never put grading logic directly inside UI components.
2. Never permanently delete academic records through ordinary user actions.
3. Never require internet for basic marks entry.
4. Never silently overwrite conflicting marks.
5. Never allow locked results to be changed without authorization.
6. Never let a grading scheme change historical approved reports unexpectedly.
7. Never store passwords as plaintext.
8. Every important academic calculation must have automated tests.
9. Every important data operation must be recoverable through backup/restore.
10. The Core Engine is the source of truth; Admin and Teacher interfaces are clients of the Core.

## 63. Recommended Build Order

```text
DATABASE → CORE MODELS → ACADEMIC ENGINE → GRADING ENGINE → RESULT ENGINE
→ ADMIN CONSOLE → TEACHER APP → REPORT ENGINE → BACKUP/IMPORT/EXPORT
→ SYNC → SECURITY/AUDIT → PRODUCTION HARDENING
```

## 64. Final Product Workflow

SCHOOL SETUP → ACADEMIC YEAR → TERMS → LEVELS → CLASSES/STREAMS → SUBJECTS → TEACHERS → STUDENTS → ENROLLMENT → TEACHER ASSIGNMENT → MARKS ENTRY → VALIDATION → MARKS SUBMISSION → RESULT CALCULATION → RESULT REVIEW → TEACHER COMMENTS → HEAD TEACHER APPROVAL → REPORT GENERATION → PDF/PRINT/EXPORT → BACKUP → SYNC → NEXT TERM/NEXT YEAR.

## 65. Starting Point

Start with the Core Engine, not UI screens: School, AcademicYear, Term, Level, Class, Stream, Student, Enrollment, Subject, Teacher, TeacherAssignment, AssessmentComponent, GradingScheme, GradeBoundary — then the three starter grading scheme configurations (PLE, competency-based UCE, UACE). All Admin and Teacher screens consume these core services rather than implementing their own versions of the data or calculations.

## 66. Success Criteria

A school can: set up its profile offline; create years/terms/levels/classes/streams; add/import students and teachers; assign teachers; configure subjects and grading; enter marks without internet; calculate results accurately; review and approve; add comments and signatures; generate, print and export professional report cards; back up and restore the database; transfer data by bundle/USB; synchronize when internet becomes available; maintain an audit trail; preserve historical records across academic years.

END OF PLAN
