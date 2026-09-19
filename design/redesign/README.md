# School Report Maker — screen redesign

This release contains the regenerated admin and teacher screen system for the offline-first School Report Maker.

## What is included

- Admin overview with reporting readiness, class status, term trend, and offline save state.
- Academic setup organized by Primary, O-Level, and A-Level with classes, streams, subjects, and teachers.
- Marks and performance analysis with readable term and subject graphs.
- Report and template workspace with a clean print-oriented report card, school details, student photo, comments, signatures, and export actions.
- Teacher home and marks-entry flows designed for quick, low-error use with offline save and push status.

## Source

The React mockup source in this folder is intentionally self-contained so the layout, copy, charts, and report template can be applied to the existing Compose Desktop and Kotlin Android screens without changing the shared grading or offline sync rules.

## Report template rules

Every printed report keeps the school identity, contact line, student photo, class and stream, term, subject marks, totals, average, position, performance graph, comments, and signature areas together on a clean page.

## Asset

`assets/mary-aine.jpg` is the student photo used in the report preview. Replace it with the school-approved student image during integration.
