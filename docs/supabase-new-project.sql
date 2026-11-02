-- ═══════════════════════════════════════════════════════════════════════════
-- DeryCode School Report Maker — COMPLETE cloud setup for a NEW Supabase project
-- One file, one paste. Replaces the old v1 → v2 → v3 → hotfix chain.
--
-- HOW TO USE
--   1. supabase.com → New project (any name, region closest to you, save the DB password)
--   2. SQL Editor → New query → paste this whole file → Run
--   3. Authentication → Sign In / Providers → Email → turn OFF "Confirm email"
--   4. Project Settings → API → copy "Project URL" and the publishable (anon) key
--   5. SRS Console → Cloud & Online → paste URL + key → create the school account
--
-- Safe to re-run: every object uses IF NOT EXISTS / DROP POLICY IF EXISTS.
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
  id         uuid primary key default gen_random_uuid(),
  school_id  uuid not null references srs_schools(id) on delete cascade,
  payload    text not null,
  size       int  default 0,
  version    bigint default extract(epoch from now()) * 1000,
  created_at timestamptz default now()
);
create index if not exists srs_backups_school on srs_backups(school_id);

create table if not exists srs_teacher_schools (
  teacher_uid  uuid not null references auth.users(id) on delete cascade,
  school_id    uuid not null references srs_schools(id) on delete cascade,
  added_at     timestamptz default now(),
  teacher_name text default '',
  role         text default 'TEACHER',
  invite_code  text default '',
  phone        text default '',
  contacts     text default '',
  photo        text default '',
  classes      text default '',
  subjects     text default '',
  active       boolean default true,
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

-- ── 2. RLS helpers (SECURITY DEFINER → no recursive policy evaluation) ─────
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

-- ── 3. Row Level Security ──────────────────────────────────────────────────
alter table srs_schools         enable row level security;
alter table srs_backups         enable row level security;
alter table srs_teacher_schools enable row level security;
alter table srs_teacher_marks   enable row level security;
alter table srs_teacher_invites enable row level security;

-- schools: owner full control, linked teachers read
drop policy if exists srs_schools_owner on srs_schools;
create policy srs_schools_owner on srs_schools
  for all to authenticated
  using (auth.uid() = owner_uid)
  with check (auth.uid() = owner_uid);

drop policy if exists srs_schools_linked_read on srs_schools;
create policy srs_schools_linked_read on srs_schools
  for select to authenticated
  using (public.srs_is_linked_teacher(id));

-- backups: owner full control, linked teachers read (roster + school setup)
drop policy if exists srs_backups_owner on srs_backups;
create policy srs_backups_owner on srs_backups
  for all to authenticated
  using (public.srs_is_school_owner(school_id))
  with check (public.srs_is_school_owner(school_id));

drop policy if exists srs_backups_linked_read on srs_backups;
create policy srs_backups_linked_read on srs_backups
  for select to authenticated
  using (public.srs_is_linked_teacher(school_id));

-- teacher links: teacher reads/edits their own row, owner manages all rows
drop policy if exists srs_ts_read_own on srs_teacher_schools;
create policy srs_ts_read_own on srs_teacher_schools
  for select to authenticated
  using (teacher_uid = auth.uid());

drop policy if exists srs_ts_read_owner on srs_teacher_schools;
create policy srs_ts_read_owner on srs_teacher_schools
  for select to authenticated
  using (public.srs_is_school_owner(school_id));

drop policy if exists srs_ts_update_own on srs_teacher_schools;
create policy srs_ts_update_own on srs_teacher_schools
  for update to authenticated
  using (teacher_uid = auth.uid())
  with check (teacher_uid = auth.uid());

drop policy if exists srs_ts_update_owner on srs_teacher_schools;
create policy srs_ts_update_owner on srs_teacher_schools
  for update to authenticated
  using (public.srs_is_school_owner(school_id))
  with check (public.srs_is_school_owner(school_id));

drop policy if exists srs_ts_delete_owner on srs_teacher_schools;
create policy srs_ts_delete_owner on srs_teacher_schools
  for delete to authenticated
  using (public.srs_is_school_owner(school_id));

-- marks: linked teachers submit, teacher + owner read, owner deletes
drop policy if exists srs_marks_insert on srs_teacher_marks;
create policy srs_marks_insert on srs_teacher_marks
  for insert to authenticated
  with check (public.srs_is_linked_teacher(school_id) and teacher_uid = auth.uid());

drop policy if exists srs_marks_read_teacher on srs_teacher_marks;
create policy srs_marks_read_teacher on srs_teacher_marks
  for select to authenticated
  using (teacher_uid = auth.uid());

drop policy if exists srs_marks_read_owner on srs_teacher_marks;
create policy srs_marks_read_owner on srs_teacher_marks
  for select to authenticated
  using (public.srs_is_school_owner(school_id));

drop policy if exists srs_marks_delete_owner on srs_teacher_marks;
create policy srs_marks_delete_owner on srs_teacher_marks
  for delete to authenticated
  using (public.srs_is_school_owner(school_id));

-- per-teacher invite codes
drop policy if exists srs_invites_owner on srs_teacher_invites;
create policy srs_invites_owner on srs_teacher_invites
  for all to authenticated
  using (public.srs_is_school_owner(school_id))
  with check (public.srs_is_school_owner(school_id));

drop policy if exists srs_invites_linked_read on srs_teacher_invites;
create policy srs_invites_linked_read on srs_teacher_invites
  for select to authenticated
  using (public.srs_is_linked_teacher(school_id));

-- ── 4. Teacher self-onboarding ─────────────────────────────────────────────
-- Removes the old 2-argument overload that made PostgREST answer PGRST203
-- ("could not choose the best candidate function").
drop function if exists srs_link_teacher(text, text);

-- srs_link_teacher: accepts a personal invite code OR the school-wide code.
create or replace function srs_link_teacher(p_code text, p_name text default '', p_role text default 'TEACHER')
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_school uuid;
  v_invite srs_teacher_invites%rowtype;
  v_clean  text := upper(trim(coalesce(p_code, '')));
  v_name   text := coalesce(p_name, '');
  v_role   text := coalesce(p_role, 'TEACHER');
begin
  if auth.uid() is null then
    raise exception 'Sign in first';
  end if;
  if v_clean ~ '[^A-Z0-9]' then
    v_clean := regexp_replace(v_clean, '[^A-Z0-9]', '', 'g');
  end if;
  if v_role not in ('TEACHER', 'HEAD_TEACHER', 'SCHOOL_ADMIN', 'DATA_ENTRY') then
    v_role := 'TEACHER';
  end if;

  select * into v_invite from srs_teacher_invites
   where code = v_clean and used_by_uid is null;

  if v_invite.id is not null then
    v_school := v_invite.school_id;
    if v_name = '' then v_name := v_invite.teacher_name; end if;
    update srs_teacher_invites
       set used_by_uid = auth.uid(), used_at = now()
     where id = v_invite.id;
  else
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
        role         = excluded.role,
        active       = true;

  return v_school;
end $$;

-- srs_register_teacher: same, plus the teacher's full profile.
create or replace function srs_register_teacher(
  p_code     text,
  p_name     text default '',
  p_role     text default 'TEACHER',
  p_phone    text default '',
  p_contacts text default '',
  p_classes  text default '',
  p_subjects text default '',
  p_photo    text default ''
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_school   uuid;
  v_invite   srs_teacher_invites%rowtype;
  v_clean    text := upper(trim(coalesce(p_code, '')));
  v_name     text := coalesce(p_name, '');
  v_role     text := coalesce(p_role, 'TEACHER');
  v_phone    text := coalesce(p_phone, '');
  v_contacts text := coalesce(p_contacts, '');
  v_classes  text := coalesce(p_classes, '');
  v_subjects text := coalesce(p_subjects, '');
  v_photo    text := coalesce(p_photo, '');
begin
  if auth.uid() is null then
    raise exception 'Sign in first';
  end if;
  if v_clean ~ '[^A-Z0-9]' then
    v_clean := regexp_replace(v_clean, '[^A-Z0-9]', '', 'g');
  end if;
  if v_role not in ('TEACHER', 'HEAD_TEACHER', 'SCHOOL_ADMIN', 'DATA_ENTRY') then
    v_role := 'TEACHER';
  end if;

  select * into v_invite from srs_teacher_invites
   where code = v_clean and used_by_uid is null;

  if v_invite.id is not null then
    v_school := v_invite.school_id;
    if v_name = '' then v_name := v_invite.teacher_name; end if;
    update srs_teacher_invites
       set used_by_uid = auth.uid(), used_at = now()
     where id = v_invite.id;
  else
    select id into v_school from srs_schools where invite_code = v_clean;
    if v_school is null then
      if exists (select 1 from srs_teacher_invites where code = v_clean) then
        raise exception 'This code was already used by another teacher — ask the head teacher for a new one';
      end if;
      raise exception 'Invalid school code — ask the head teacher for the correct code';
    end if;
  end if;

  insert into srs_teacher_schools (teacher_uid, school_id, teacher_name, role, invite_code,
                                   phone, contacts, classes, subjects, photo, active)
  values (auth.uid(), v_school, v_name, v_role, v_clean,
          v_phone, v_contacts, v_classes, v_subjects, v_photo, true)
  on conflict (teacher_uid, school_id) do update
    set teacher_name = excluded.teacher_name,
        role         = excluded.role,
        phone        = excluded.phone,
        contacts     = excluded.contacts,
        classes      = excluded.classes,
        subjects     = excluded.subjects,
        photo        = case when excluded.photo = '' then srs_teacher_schools.photo else excluded.photo end,
        active       = true;

  return v_school;
end $$;

grant execute on function srs_link_teacher(text, text, text) to authenticated;
grant execute on function srs_register_teacher(text, text, text, text, text, text, text, text) to authenticated;

-- ── 5. Table privileges ────────────────────────────────────────────────────
grant select, insert, update, delete on srs_schools         to authenticated;
grant select, insert, update, delete on srs_backups         to authenticated;
grant select, insert, update, delete on srs_teacher_schools to authenticated;
grant select, insert, update, delete on srs_teacher_marks   to authenticated;
grant select, insert, update, delete on srs_teacher_invites to authenticated;

-- ── 6. Verify (should return five rows, all "ok") ──────────────────────────
select 'srs_schools' as object, count(*)::text as rows from srs_schools
union all select 'srs_backups', count(*)::text from srs_backups
union all select 'srs_teacher_schools', count(*)::text from srs_teacher_schools
union all select 'srs_teacher_marks', count(*)::text from srs_teacher_marks
union all select 'srs_teacher_invites', count(*)::text from srs_teacher_invites;
