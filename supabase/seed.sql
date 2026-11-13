-- Helpers for the pgTAP tests in supabase/tests/database. Local only: this
-- file runs after the migrations on `supabase db reset` / `supabase start`
-- and is never applied to a hosted project.
--
-- Each test file runs in one transaction and rolls back, so users made here
-- and every setting changed by act_as disappear with it. Go back to the
-- superuser with a plain `reset role;`.

create schema if not exists tests;

-- The smallest auth.users row the local auth schema accepts. The email only
-- has to be unique, so it is derived from the id.
create or replace function tests.make_user(p_id uuid)
returns void
language plpgsql
as $$
begin
  insert into auth.users (id, instance_id, aud, role, email)
  values (
    p_id,
    '00000000-0000-0000-0000-000000000000',
    'authenticated',
    'authenticated',
    p_id::text || '@test.local'
  );
end;
$$;

-- Act as a signed-in user for the rest of the transaction: auth.uid() returns
-- p_user and RLS applies as it does to the app.
create or replace function tests.act_as(p_user uuid)
returns void
language plpgsql
as $$
begin
  perform set_config(
    'request.jwt.claims',
    json_build_object('sub', p_user, 'role', 'authenticated')::text,
    true
  );
  perform set_config('role', 'authenticated', true);
end;
$$;

-- Act as a caller with no session: auth.uid() is null.
create or replace function tests.act_as_anon()
returns void
language plpgsql
as $$
begin
  perform set_config('request.jwt.claims', json_build_object('role', 'anon')::text, true);
  perform set_config('role', 'anon', true);
end;
$$;

-- Tests switch users while already acting as one, so both roles need these.
grant usage on schema tests to anon, authenticated;
grant execute on all functions in schema tests to anon, authenticated;
