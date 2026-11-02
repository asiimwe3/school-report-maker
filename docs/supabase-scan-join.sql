-- ─────────────────────────────────────────────────────────────────────────────
-- SRS · one-scan school join
--
-- The phone app used to need three things before a teacher saw any data:
--   1. an account (sign up, confirm, sign in),
--   2. a lookup in srs_schools,
--   3. a second lookup in srs_backups,
-- and the middle one was blocked by row-level security, so the scan "worked"
-- but the teacher saw an empty app.
--
-- This installs ONE public function that returns everything the phone needs in
-- a single round trip, using the school's invite code alone:
--   { school: {...}, payload: "<full school setup JSON>", updated_at, ... }
--
-- Safe to re-run. No table access is granted: the function is SECURITY DEFINER
-- and only ever answers for a valid invite code.
-- ─────────────────────────────────────────────────────────────────────────────

create or replace function public.srs_join_by_code(_code text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_clean    text;
  v_school   srs_schools%rowtype;
  v_payload  text;
  v_updated  timestamptz;
  v_bytes    integer;
begin
  -- Codes are typed or scanned; accept spaces, dashes and lower case.
  v_clean := upper(regexp_replace(coalesce(_code, ''), '[^A-Za-z0-9]', '', 'g'));

  if length(v_clean) < 4 then
    raise exception 'Enter the school code from your head teacher';
  end if;

  select * into v_school from srs_schools where invite_code = v_clean limit 1;

  if v_school.id is null then
    raise exception 'That code was not found. Ask the head teacher to check the code on the console.';
  end if;

  select payload, created_at, pg_column_size(payload)
    into v_payload, v_updated, v_bytes
    from srs_backups
   where school_id = v_school.id
   order by created_at desc
   limit 1;

  return jsonb_build_object(
    'ok', true,
    'school', jsonb_build_object(
      'id',          v_school.id,
      'name',        coalesce(v_school.name, ''),
      'district',    coalesce(v_school.district, ''),
      'address',     coalesce(v_school.address, ''),
      'head_teacher', coalesce(v_school.head_teacher, ''),
      'invite_code', coalesce(v_school.invite_code, ''),
      'plan',        coalesce(v_school.plan, ''),
      'student_count', coalesce(v_school.student_count, 0)
    ),
    'payload',    v_payload,
    'payload_bytes', coalesce(v_bytes, 0),
    'updated_at', v_updated,
    'has_setup',  v_payload is not null
  );
end $$;

revoke all on function public.srs_join_by_code(text) from public;
grant execute on function public.srs_join_by_code(text) to anon, authenticated;

-- Verification: returns a row per school code so the console can self-check.
create or replace function public.srs_join_selfcheck(_code text)
returns jsonb
language sql
security definer
set search_path = public
as $$
  select jsonb_build_object(
    'school',  s.name,
    'code',    s.invite_code,
    'backups', (select count(*) from srs_backups b where b.school_id = s.id),
    'bytes',   coalesce((select pg_column_size(b.payload) from srs_backups b where b.school_id = s.id order by created_at desc limit 1), 0)
  )
  from srs_schools s
  where s.invite_code = upper(regexp_replace(coalesce(_code, ''), '[^A-Za-z0-9]', '', 'g'))
  limit 1;
$$;

revoke all on function public.srs_join_selfcheck(text) from public;
grant execute on function public.srs_join_selfcheck(text) to anon, authenticated;
