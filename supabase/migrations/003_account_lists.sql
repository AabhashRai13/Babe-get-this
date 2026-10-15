-- Account-owned lists (docs/technical-decisions/008, spec 2026-10-14).
-- Every signed-in user's list is now stored server-side; only SHARED lists
-- carry a code. Run AFTER 002, staging first. Compatible with old app
-- versions: they always send a code.

-- 1. Unshared lists have no code. UNIQUE already permits many NULLs.
alter table public.lists
  alter column share_code drop not null;

-- 2. Write-once code: NULL -> code is allowed exactly once (sharing a list
--    that already syncs); a set code can never change or be cleared, even by
--    a client pushing a stale row with share_code = null.
create or replace function public.protect_list_immutables()
returns trigger
language plpgsql
as $$
begin
  if new.created_by is not null then
    new.created_by := old.created_by;
  end if;
  new.share_code := coalesce(old.share_code, new.share_code);
  return new;
end;
$$;

-- 3. Negative tests (run manually in the SQL editor):
--   As user A: insert into lists (id, name, created_by) values (gen_random_uuid(), 'solo', auth.uid());
--     -> succeeds, share_code null.
--   As user B (not a member): select * from lists where id = '<A's solo list>';  -> zero rows.
--   As A: update lists set share_code = 'ABCDEF' where id = '<solo>';  -> code set.
--   As A: update lists set share_code = null where id = '<solo>';      -> code still 'ABCDEF'.
--   As A: update lists set share_code = 'ZZZZZZ' where id = '<solo>';  -> code still 'ABCDEF'.
