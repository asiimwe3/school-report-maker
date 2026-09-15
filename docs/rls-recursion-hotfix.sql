-- ═══════════════════════════════════════════════════════════════════════════
-- HOTFIX: infinite recursion (42P17) between srs_schools <-> srs_teacher_schools
-- Cause: srs_schools_linked_read (on srs_schools) subqueries srs_teacher_schools,
--        and srs_ts_read_owner (on srs_teacher_schools, added in v2) subqueries
--        srs_schools right back -> circular RLS evaluation.
-- Fix: replace the cross-table subqueries with SECURITY DEFINER helper
--      functions. A definer function runs with the function owner's
--      privileges, so its internal query does NOT re-trigger RLS on the
--      table it reads -> breaks the cycle. Safe to re-run.
-- Run this ONCE in: Supabase Dashboard -> SQL Editor -> New query
-- ═══════════════════════════════════════════════════════════════════════════

-- ── Helper functions (bypass RLS internally, so no recursive policy calls) ──
create or replace function public.srs_is_school_owner(_school_id uuid)
returns boolean
language sql
security definer
set search_path = public
stable
as $$
  select exists (
    select 1 from srs_schools
    where id = _school_id and owner_uid = auth.uid()
  );
$$;

create or replace function public.srs_is_linked_teacher(_school_id uuid)
returns boolean
language sql
security definer
set search_path = public
stable
as $$
  select exists (
    select 1 from srs_teacher_schools
    where school_id = _school_id and teacher_uid = auth.uid()
  );
$$;

grant execute on function public.srs_is_school_owner(uuid) to authenticated;
grant execute on function public.srs_is_linked_teacher(uuid) to authenticated;

-- ── srs_schools: rewrite to use the helper instead of a direct subquery ────
drop policy if exists srs_schools_linked_read on srs_schools;
create policy srs_schools_linked_read on srs_schools
  for select to authenticated
  using (public.srs_is_linked_teacher(id));

-- (srs_schools_owner stays as-is: auth.uid() = owner_uid, no cross-table ref)

-- ── srs_teacher_schools: rewrite the v2 owner-read policy the same way ─────
drop policy if exists srs_ts_read_owner on srs_teacher_schools;
create policy srs_ts_read_owner on srs_teacher_schools
  for select to authenticated
  using (public.srs_is_school_owner(school_id));

-- (srs_ts_read_own stays as-is: teacher_uid = auth.uid(), no cross-table ref)

-- ── Everything else that subqueries srs_schools (backups, marks, invites) ──
-- These only reference srs_schools, not srs_teacher_schools -> not part of
-- the cycle, but switched to the helper too for consistency & safety.
drop policy if exists srs_backups_owner on srs_backups;
create policy srs_backups_owner on srs_backups
  for all to authenticated
  using (public.srs_is_school_owner(school_id))
  with check (public.srs_is_school_owner(school_id));

drop policy if exists srs_marks_read_owner on srs_teacher_marks;
create policy srs_marks_read_owner on srs_teacher_marks
  for select to authenticated
  using (public.srs_is_school_owner(school_id));

drop policy if exists srs_marks_delete_owner on srs_teacher_marks;
create policy srs_marks_delete_owner on srs_teacher_marks
  for delete to authenticated
  using (public.srs_is_school_owner(school_id));

drop policy if exists srs_invites_owner on srs_teacher_invites;
create policy srs_invites_owner on srs_teacher_invites
  for all to authenticated
  using (public.srs_is_school_owner(school_id))
  with check (public.srs_is_school_owner(school_id));

