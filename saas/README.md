# School Report Maker — SaaS platform

Multi-tenant cloud school management & report-making SaaS for Ugandan schools.
This directory is the foundation of the web platform that replaces (and later
absorbs) the Kotlin desktop-admin console and teacher Android app.

## Status: FOUNDATION PHASE (not production-ready)

Delivered and verified in this phase:

* Full multi-tenant PostgreSQL schema — 30 tables, tenant isolation by
  `schoolId` on every school-owned record, unique mark key
  (student+subject+term+component), unique memberships/invitations, indexes
  on all hot paths. `npx prisma validate` ✅
* Shared grading engine (`src/grading/`) — single source of truth ported from
  the Kotlin core: PLE (grades 1-9, best-4 aggregate, division table), UCE
  competency-based (configurable CA/exam weights, A-E, Result 1/2/3,
  compulsory gating, missing-component detection), UACE (best-3 principals,
  A=1..E=5 lower-is-better, subsidiary threshold credit), competition ranking
  (1,2,2,4) with class + stream positions, explicit academic-year roster
  scoping (previous-year enrollments never leak).
* Safe import engine (`src/migration/`) — non-destructive bundle merge keyed
  on the unique mark key with ADD/UPDATE/CONFLICT/REJECT/DUPLICATE previews;
  SchoolData JSON v2 migration parser with dry-run report (duplicate students,
  duplicate marks, invalid references, invalid payments).
* Test suite: 28 tests, all passing (`npx vitest run`), typecheck clean
  (`npx tsc --noEmit`).
* DB CHECK constraints (prisma/sql/checks.sql): non-negative payments,
  mark score bounds + special-mark invariants, term numbers, attendance.

Explicitly NOT done yet (next phases — do not deploy as-is):

1. API layer (Next.js app router + server-side auth + RBAC + rate limiting).
2. Web UI (onboarding, dashboards, mark-entry workspace, approval queue…).
3. Stripe billing + webhooks.
4. Report PDF/DOCX rendering from engine snapshots.
5. Background jobs (imports, report generation, backups) + object storage.
6. Staging/production deployment, Playwright E2E, CI for this package.
7. Key rotation completion (docs/KEY-ROTATION.md) — old keystore removed
   from HEAD; rotation + history purge pending.

## Architecture

Next.js (React, TypeScript) monolith: server-side API routes, Prisma ORM,
PostgreSQL. The grading engine is a pure TypeScript package shared by every
consumer (reports, dashboards, merit lists, exports) — no renderer ever
recalculates averages.

Multi-tenancy: every school-owned row carries `schoolId`; the API layer's
query helpers inject `schoolId` from the authenticated membership —
roles are read server-side only. Invite codes are single-use, expiring,
server-role-assigned. Sessions are HTTP-only Secure SameSite cookies with
CSRF secrets; refresh tokens rotate by family.

## Required environment variables (values never in git)

DATABASE_URL — PostgreSQL connection string
STRIPE_SECRET_KEY — billing
STRIPE_WEBHOOK_SECRET — webhook verification
SESSION_SECRET / APP_KEY — session & token signing
OBJECT_STORAGE_* (S3-compatible bucket credentials)
SMTP_URL — email notifications

## Local development

cd saas && npm install
npm test          # 28 tests
npm run typecheck
DATABASE_URL=... npx prisma migrate deploy   # after prisma migrate dev

## Safety rules encoded here

* Importing one teacher bundle can never delete other marks (regression
  tested in tests/migration.test.ts).
* Backups: insert → validate → prune; never delete-then-insert.
* ABS/MISSING/EXEMPT/NA are real states, never silent zeros (DB CHECK).
* Current-term/current-year are explicit per school, never inferred.
