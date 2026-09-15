-- ═══════════════════════════════════════════════════════════════════════════
-- DeryCode School Report Maker — Cloud backend v2 (per-teacher invite codes)
-- Run ONCE in: Supabase Dashboard → SQL Editor → New query
-- Safe to re-run (everything is IF EXISTS / IF NOT EXISTS).
-- Works on top of v1 (docs/supabase-setup.sql) — run v1 first on a new project.
-- ═══════════════════════════════════════════════════════════════════════════

-- ── 1. Per-teacher invite codes ────────────────────────────────────────────
-- Each teacher gets a personal code generated on the Admin Console
-- (Teachers screen). One code = one teacher; it dies after first use.
create table if not exists srs_teacher_invites (
  id           uuid primary key default gen_random_uuid(),
  school_id    uuid not null references srs_schools(id) on delete cascade,
  code         text not null unique,
  teacher_name text default '',
  created_at   timestamptz default now(),
  used_by_uid  uuid references auth.users(id) on delete set null,
  used_at      timestamptz
);
create index if not exists srs_invites_school on srs_teacher_invites(school_id);

alter table srs_teacher_schools add column if not exists teacher_name text default '';
alter table srs_teacher_schools add column if not exists role        text default 'TEACHER';
alter table srs_teacher_schools add column if not exists invite_code text default '';

alter table srs_teacher_invites enable row level security;

-- school owner manages the invites
create policy srs_invites_owner on srs_teacher_invites
  for all to authenticated
  using (school_id in (select id from srs_schools where owner_uid = auth.uid()))
  with check (school_id in (select id from srs_schools where owner_uid = auth.uid()));

-- linked teachers can see the invite they were given (so the join can show
-- a friendly "you were invited as X" message)
create policy srs_invites_linked_read on srs_teacher_invites
  for select to authenticated
  using (exists (select 1 from srs_teacher_schools ts
                 where ts.teacher_uid = auth.uid() and ts.school_id = srs_teacher_invites.school_id));

-- owner can read the linked-teacher list (names + self-selected roles)
create policy srs_ts_read_owner on srs_teacher_schools
  for select to authenticated
  using (school_id in (select id from srs_schools where owner_uid = auth.uid()));

-- ── 2. Teacher self-onboarding v2 ──────────────────────────────────────────
-- p_code accepts BOTH:
--   * a personal per-teacher invite code (srs_teacher_invites) — preferred
--   * the old school-wide invite code (srs_schools) — kept so old shares work
-- The teacher's self-chosen role is stored on the link, so the console's
-- "Pull teacher list" can add them to the staff roster with the right role.
create or replace function srs_link_teacher(p_code text, p_name text default '', p_role text default 'TEACHER')
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_school  uuid;
  v_invite  srs_teacher_invites%rowtype;
  v_clean   text := upper(trim(coalesce(p_code, '')));
  v_name    text := coalesce(p_name, '');
  v_role    text := coalesce(p_role, 'TEACHER');
begin
  if v_clean ~ '[^A-Z0-9]' then
    v_clean := regexp_replace(v_clean, '[^A-Z0-9]', '', 'g');
  end if;
  if auth.uid() is null then
    raise exception 'Sign in first';
  end if;
  if v_role not in ('TEACHER', 'HEAD_TEACHER', 'SCHOOL_ADMIN', 'DATA_ENTRY') then
    v_role := 'TEACHER';
  end if;

  -- 1) personal invite code (single use)
  select * into v_invite from srs_teacher_invites
   where code = v_clean and used_by_uid is null;
  if v_invite.id is not null then
    v_school := v_invite.school_id;
    if v_name = '' then v_name := v_invite.teacher_name; end if;
    update srs_teacher_invites
       set used_by_uid = auth.uid(), used_at = now()
     where id = v_invite.id;
  else
    -- 2) fallback: school-wide code (old shares still work)
    select id into v_school from srs_schools where invite_code = v_clean;
    if v_school is null then
      if exists (select 1 from srs_teacher_invites where code = v_clean) then
        raise exception 'This code was already used by another teacher — ask the head teacher for a new one';
      end if;
      raise exception 'Invalid school code — ask the head teacher for the correct code';
    end if;
  end if;

  insert into srs_teacher_schools (teacher_uid, school_id, teacher_name, role, invite_code)
  values (auth.uid(), v_school, v_name, v_role, v_clean)
  on conflict (teacher_uid, school_id) do update
    set teacher_name = excluded.teacher_name,
        role         = excluded.role;
  return v_school;
end $$;
