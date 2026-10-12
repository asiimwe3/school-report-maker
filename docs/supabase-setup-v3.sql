-- ═══════════════════════════════════════════════════════════════════════════
-- DeryCode School Report Maker — Cloud backend v3 (teacher self-registration)
-- Run ONCE in: Supabase Dashboard → SQL Editor → New query
-- Safe to re-run (everything is IF EXISTS / IF NOT EXISTS / ON CONFLICT).
-- Works on top of v1 + v2 (run those first on a new project).
--
-- WHAT v3 ADDS
--  * Full teacher profile on the school link: phone, contacts, passport photo,
--    classes taught, subjects taught, active status.
--  * New RPC srs_register_teacher — the invite code now registers a teacher
--    with their complete profile (photo, contacts, classes, subjects).
--  * Policies: teachers edit their own profile; the school owner (admin
--    console) can edit or disconnect any linked teacher at any time.
--  * Drops the old 2-parameter srs_link_teacher overload that made PostgREST
--    return "Could not choose the best candidate function" (PGRST203).
-- ═══════════════════════════════════════════════════════════════════════════

-- ── 1. Teacher profile columns on the school link ───────────────────────────
alter table srs_teacher_schools add column if not exists phone    text default '';     -- teacher's phone
alter table srs_teacher_schools add column if not exists contacts text default '';     -- other contacts (email/WhatsApp)
alter table srs_teacher_schools add column if not exists photo    text default '';     -- passport photo, base64 JPEG (~small)
alter table srs_teacher_schools add column if not exists classes  text default '';    -- classes they teach, comma separated
alter table srs_teacher_schools add column if not exists subjects text default '';    -- subjects they teach, comma separated
alter table srs_teacher_schools add column if not exists active   boolean default true; -- connected until the school disconnects them

-- ── 2. Policies ────────────────────────────────────────────────────────────
-- teacher can update their own profile row (photo, phone, classes, subjects)
drop policy if exists srs_ts_update_own on srs_teacher_schools;
create policy srs_ts_update_own on srs_teacher_schools
  for update to authenticated
  using (teacher_uid = auth.uid())
  with check (teacher_uid = auth.uid());

-- school owner can edit (update) linked teacher rows from the admin console
drop policy if exists srs_ts_update_owner on srs_teacher_schools;
create policy srs_ts_update_owner on srs_teacher_schools
  for update to authenticated
  using (school_id in (select id from srs_schools where owner_uid = auth.uid()))
  with check (school_id in (select id from srs_schools where owner_uid = auth.uid()));

-- ── 3. Fix the RPC overload ambiguity (PGRST203 in production) ─────────────
drop function if exists srs_link_teacher(text, text);

-- ── 4. Teacher self-registration v3 ─────────────────────────────────────────
-- Called AFTER the teacher has created their cloud account (sign-up gives the
-- token). Accepts the personal invite code OR the school-wide code. Records
-- the complete profile: name, role, phone, contacts, classes, subjects, photo.
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
  v_school  uuid;
  v_invite  srs_teacher_invites%rowtype;
  v_clean   text := upper(trim(coalesce(p_code, '')));
  v_name    text := coalesce(p_name, '');
  v_role    text := coalesce(p_role, 'TEACHER');
  v_phone   text := coalesce(p_phone, '');
  v_contacts text := coalesce(p_contacts, '');
  v_classes text := coalesce(p_classes, '');
  v_subjects text := coalesce(p_subjects, '');
  v_photo   text := coalesce(p_photo, '');
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
