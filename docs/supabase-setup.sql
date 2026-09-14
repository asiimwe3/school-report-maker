-- ═══════════════════════════════════════════════════════════════════════════
-- DeryCode School Report Maker — Cloud backend (Supabase)
-- v2.2.0 · Run this ONCE in: Supabase Dashboard → SQL Editor → New query
-- Safe to re-run (drops are IF EXISTS, policies use IF NOT EXISTS via DO blocks).
-- ═══════════════════════════════════════════════════════════════════════════

-- ── 1. Tables ──────────────────────────────────────────────────────────────
create table if not exists srs_schools (
  id            uuid primary key default gen_random_uuid(),
  owner_uid     uuid not null default auth.uid() references auth.users(id) on delete cascade,
  name          text not null,
  district      text default '',
  address       text default '',
  head_teacher  text default '',
  invite_code   text not null unique,
  plan          text default 'Trial',
  license_key   text default '',
  student_count int  default 0,
  created_at    timestamptz default now()
);

create table if not exists srs_backups (
  id        uuid primary key default gen_random_uuid(),
  school_id uuid not null references srs_schools(id) on delete cascade,
  payload   text not null,
  size      int  default 0,
  version   bigint default extract(epoch from now()) * 1000,
  created_at timestamptz default now()
);
create index if not exists srs_backups_school on srs_backups(school_id);

create table if not exists srs_teacher_schools (
  teacher_uid uuid not null references auth.users(id) on delete cascade,
  school_id   uuid not null references srs_schools(id) on delete cascade,
  added_at    timestamptz default now(),
  primary key (teacher_uid, school_id)
);

create table if not exists srs_teacher_marks (
  id           uuid primary key default gen_random_uuid(),
  school_id    uuid not null references srs_schools(id) on delete cascade,
  teacher_uid  uuid not null references auth.users(id) on delete cascade,
  teacher_name text default '',
  payload      text not null,
  created_at   timestamptz default now()
);
create index if not exists srs_marks_school on srs_teacher_marks(school_id);

-- ── 2. Row Level Security ─────────────────────────────────────────────────
alter table srs_schools         enable row level security;
alter table srs_backups         enable row level security;
alter table srs_teacher_schools enable row level security;
alter table srs_teacher_marks    enable row level security;

-- owner can do everything with own school
create policy srs_schools_owner on srs_schools
  for all to authenticated
  using (auth.uid() = owner_uid)
  with check (auth.uid() = owner_uid);

-- linked teachers can read the school (name, invite code, etc.)
create policy srs_schools_linked_read on srs_schools
  for select to authenticated
  using (exists (select 1 from srs_teacher_schools ts
                 where ts.teacher_uid = auth.uid() and ts.school_id = srs_schools.id));

-- owner full control of backups; linked teachers can read (roster + marks)
create policy srs_backups_owner on srs_backups
  for all to authenticated
  using (school_id in (select id from srs_schools where owner_uid = auth.uid()))
  with check (school_id in (select id from srs_schools where owner_uid = auth.uid()));

create policy srs_backups_linked_read on srs_backups
  for select to authenticated
  using (exists (select 1 from srs_teacher_schools ts
                 where ts.teacher_uid = auth.uid() and ts.school_id = srs_backups.school_id));

-- teachers see their own links; rows are created by the RPC (definer)
create policy srs_ts_read_own on srs_teacher_schools
  for select to authenticated
  using (teacher_uid = auth.uid());

-- teachers submit marks for linked schools; only they and the school owner read them
create policy srs_marks_insert on srs_teacher_marks
  for insert to authenticated
  with check (exists (select 1 from srs_teacher_schools ts
                 where ts.teacher_uid = auth.uid() and ts.school_id = srs_teacher_marks.school_id));

create policy srs_marks_read_teacher on srs_teacher_marks
  for select to authenticated
  using (teacher_uid = auth.uid());

create policy srs_marks_read_owner on srs_teacher_marks
  for select to authenticated
  using (school_id in (select id from srs_schools where owner_uid = auth.uid()));

create policy srs_marks_delete_owner on srs_teacher_marks
  for delete to authenticated
  using (school_id in (select id from srs_schools where owner_uid = auth.uid()));

-- ── 3. Teacher self-onboarding ─────────────────────────────────────────────
-- Teacher app calls rpc/srs_link_teacher with the school's invite code.
create or replace function srs_link_teacher(p_code text, p_name text default '')
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_school uuid;
begin
  if auth.uid() is null then
    raise exception 'Sign in first';
  end if;
  select id into v_school from srs_schools where invite_code = upper(trim(p_code));
  if v_school is null then
    raise exception 'Invalid school code — ask the head teacher for the correct code';
  end if;
  insert into srs_teacher_schools (teacher_uid, school_id)
  values (auth.uid(), v_school)
  on conflict (teacher_uid, school_id) do nothing;
  return v_school;
end $$;
