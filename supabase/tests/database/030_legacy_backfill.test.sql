-- Old app versions and the backfill (006_got_this.sql, spec "Server" steps 13
-- and 14). Old-app writes are simulated, as A, by item writes that leave
-- picked_up_at alone. A creates list L; B joined it ten days ago.
--
-- now() is fixed for the whole transaction, so a spurious legacy tick written
-- now would share its id with any other written now. The items that must NOT
-- get one (I2, R) are therefore seeded with older tick times.
begin;
create extension if not exists pgtap with schema extensions;
select plan(30);

-- An event as the phone sends it, with the id computed the way GotThisIds does.
create function tests.ev(
  p_item     uuid,
  p_list     uuid,
  p_at       timestamptz,
  p_name     text default 'Milk',
  p_category text default 'dairy',
  p_undone   timestamptz default null
)
returns jsonb
language sql
stable
as $$
  select jsonb_build_object(
    'id', md5(p_item::text || ':' || (floor(extract(epoch from p_at) * 1000))::bigint::text)::uuid,
    'item_id', p_item,
    'list_id', p_list,
    'ticked_at', p_at,
    'name', p_name,
    'category_id', p_category,
    'undone_at', p_undone
  );
$$;

-- now() minus p_ago, to the millisecond, as the phone and the trigger store it.
create function tests.ms_ago(p_ago interval)
returns timestamptz
language sql
stable
as $$
  select date_trunc('milliseconds', now() - p_ago);
$$;

select tests.make_user('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
select tests.make_user('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb');

insert into public.lists (id, name, created_by)
values ('0000000a-0000-0000-0000-000000000021', 'L', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
insert into public.list_members (list_id, user_id, joined_at)
values ('0000000a-0000-0000-0000-000000000021', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', now() - interval '10 days');

-- I1: not ticked. I2 and R: ticked by a current app 2 and 3 hours ago (an
-- insert that carries picked_up_at writes no tick), with their app ticks.
insert into public.items (id, list_id, name) values
  ('0000000e-0000-0000-0000-000000000101', '0000000a-0000-0000-0000-000000000021', 'Milk'),
  ('0000000e-0000-0000-0000-000000000106', '0000000a-0000-0000-0000-000000000021', 'Tea');
insert into public.items (id, list_id, name, category_id, is_picked_up, picked_up_at) values
  ('0000000e-0000-0000-0000-000000000102', '0000000a-0000-0000-0000-000000000021', 'Bread', 'bakery', true, tests.ms_ago('2 hours')),
  ('0000000e-0000-0000-0000-000000000104', '0000000a-0000-0000-0000-000000000021', 'Eggs', 'dairy', true, tests.ms_ago('3 hours'));
select got_this_private.record_tick_rows(
  got_this_private.tick_id('0000000e-0000-0000-0000-000000000102', tests.ms_ago('2 hours')),
  '0000000e-0000-0000-0000-000000000102', '0000000a-0000-0000-0000-000000000021',
  tests.ms_ago('2 hours'), 'Bread', 'bakery', null, 'app');
select got_this_private.record_tick_rows(
  got_this_private.tick_id('0000000e-0000-0000-0000-000000000104', tests.ms_ago('3 hours')),
  '0000000e-0000-0000-0000-000000000104', '0000000a-0000-0000-0000-000000000021',
  tests.ms_ago('3 hours'), 'Eggs', 'dairy', null, 'app');

-- 1. Old-app tick.
select tests.act_as('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
update public.items set is_picked_up = true where id = '0000000e-0000-0000-0000-000000000101';
reset role;
select is(
  (select picked_up_at from public.items where id = '0000000e-0000-0000-0000-000000000101'),
  date_trunc('milliseconds', now()),
  '1. an old-app tick sets picked_up_at to the server time'
);
select results_eq(
  $q$ select id, source, ticked_at from public.ticks
      where item_id = '0000000e-0000-0000-0000-000000000101' $q$,
  $q$ select got_this_private.tick_id('0000000e-0000-0000-0000-000000000101', date_trunc('milliseconds', now())),
             'legacy'::text, date_trunc('milliseconds', now()) $q$,
  '1. an old-app tick writes exactly one legacy tick, with the id of that time'
);
select results_eq(
  $q$ select user_id from public.purchases
      where source_item_id = '0000000e-0000-0000-0000-000000000101' order by user_id $q$,
  $q$ values ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid), ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'::uuid) $q$,
  '1. the legacy tick gives A and B a purchase each'
);

-- 2. Old-app rename of a ticked item.
select tests.act_as('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
update public.items set name = 'Rye bread' where id = '0000000e-0000-0000-0000-000000000102';
reset role;
select results_eq(
  $q$ select ticked_at, source from public.ticks
      where item_id = '0000000e-0000-0000-0000-000000000102' $q$,
  $q$ values (tests.ms_ago('2 hours'), 'app'::text) $q$,
  '2. renaming a ticked item writes no tick'
);
select results_eq(
  $q$ select user_id, name, deleted_at is null from public.purchases
      where source_item_id = '0000000e-0000-0000-0000-000000000102' order by user_id $q$,
  $q$ values ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid, 'Bread'::text, true),
             ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'::uuid, 'Bread'::text, true) $q$,
  '2. renaming a ticked item leaves its purchases unchanged'
);

-- 3. Old-app untick.
select tests.act_as('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
update public.items set is_picked_up = false where id = '0000000e-0000-0000-0000-000000000102';
reset role;
select is(
  (select picked_up_at from public.items where id = '0000000e-0000-0000-0000-000000000102'),
  tests.ms_ago('2 hours'),
  '3. an old-app untick leaves picked_up_at unchanged'
);
select is(
  (select count(*)::int from public.purchases
   where source_item_id = '0000000e-0000-0000-0000-000000000102' and deleted_at is null),
  2,
  '3. an old-app untick leaves the purchases live'
);

-- 4. A null picked_up_at never clears the stored one.
select tests.act_as('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
update public.items set picked_up_at = null where id = '0000000e-0000-0000-0000-000000000102';
reset role;
select is(
  (select picked_up_at from public.items where id = '0000000e-0000-0000-0000-000000000102'),
  tests.ms_ago('2 hours'),
  '4. an update setting picked_up_at to null leaves the stored value'
);

-- 5. New-app tick: the item push carries the new time, the event follows.
select tests.act_as('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
update public.items set is_picked_up = true, picked_up_at = tests.ms_ago('30 minutes')
where id = '0000000e-0000-0000-0000-000000000102';
select public.record_ticks(jsonb_build_array(
  tests.ev('0000000e-0000-0000-0000-000000000102', '0000000a-0000-0000-0000-000000000021',
           tests.ms_ago('30 minutes'), 'Rye bread', 'bakery')
));
reset role;
select results_eq(
  $q$ select ticked_at, source from public.ticks
      where item_id = '0000000e-0000-0000-0000-000000000102' order by ticked_at $q$,
  $q$ values (tests.ms_ago('2 hours'), 'app'::text), (tests.ms_ago('30 minutes'), 'app'::text) $q$,
  '5. a new-app tick is recorded once, from its event, as source app'
);

-- 6. New-app untick (same time), then a re-tick with a clock behind the server's.
select tests.act_as('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
update public.items set is_picked_up = false, picked_up_at = tests.ms_ago('30 minutes')
where id = '0000000e-0000-0000-0000-000000000102';
update public.items set is_picked_up = true, picked_up_at = tests.ms_ago('90 minutes')
where id = '0000000e-0000-0000-0000-000000000102';
reset role;
select is(
  (select picked_up_at from public.items where id = '0000000e-0000-0000-0000-000000000102'),
  tests.ms_ago('30 minutes'),
  '6. an older picked_up_at from a new app leaves the stored value'
);
select is(
  (select count(*)::int from public.ticks
   where item_id = '0000000e-0000-0000-0000-000000000102' and source = 'legacy'),
  0,
  '6. an older picked_up_at from a new app writes no legacy tick'
);

-- 7. A claimed guest item arrives ticked, with its time; its purchase comes
--    through upload_own_purchases.
select tests.act_as('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
insert into public.items (id, list_id, name, is_picked_up, picked_up_at)
values ('0000000e-0000-0000-0000-000000000103', '0000000a-0000-0000-0000-000000000021', 'Jam', true, tests.ms_ago('1 day'));
select public.upload_own_purchases(jsonb_build_array(jsonb_build_object(
  'tick_id', tests.ev('0000000e-0000-0000-0000-000000000103', '0000000a-0000-0000-0000-000000000021', tests.ms_ago('1 day')) ->> 'id',
  'source_item_id', '0000000e-0000-0000-0000-000000000103',
  'name', 'Jam', 'category_id', null, 'bought_at', tests.ms_ago('1 day'))));
reset role;
select is(
  (select count(*)::int from public.ticks where item_id = '0000000e-0000-0000-0000-000000000103'),
  0,
  '7. inserting a ticked item that carries picked_up_at writes no tick'
);
select is(
  (select count(*)::int from public.purchases
   where tick_id = got_this_private.tick_id('0000000e-0000-0000-0000-000000000103', tests.ms_ago('1 day'))),
  1,
  '7. the guest tick has exactly one purchase'
);

-- R1. Old-app upsert of an item already ticked on the server, without
--     picked_up_at in the payload (BEFORE INSERT fires for the proposed row).
select tests.act_as('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
insert into public.items (id, list_id, name, is_picked_up)
values ('0000000e-0000-0000-0000-000000000104', '0000000a-0000-0000-0000-000000000021', 'Eggs renamed', true)
on conflict (id) do update set name = excluded.name, is_picked_up = excluded.is_picked_up;
reset role;
select results_eq(
  $q$ select ticked_at, source from public.ticks
      where item_id = '0000000e-0000-0000-0000-000000000104' $q$,
  $q$ values (tests.ms_ago('3 hours'), 'app'::text) $q$,
  'R1. an upsert of an already-ticked item without picked_up_at writes no tick'
);
select results_eq(
  $q$ select user_id, name, deleted_at is null from public.purchases
      where source_item_id = '0000000e-0000-0000-0000-000000000104' order by user_id $q$,
  $q$ values ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid, 'Eggs'::text, true),
             ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'::uuid, 'Eggs'::text, true) $q$,
  'R1. ... and leaves the purchases unchanged'
);
select is(
  (select picked_up_at from public.items where id = '0000000e-0000-0000-0000-000000000104'),
  tests.ms_ago('3 hours'),
  'R1. ... and leaves picked_up_at unchanged'
);

-- R2. The same upsert sending picked_up_at = null explicitly.
select tests.act_as('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
insert into public.items (id, list_id, name, is_picked_up, picked_up_at)
values ('0000000e-0000-0000-0000-000000000104', '0000000a-0000-0000-0000-000000000021', 'Eggs again', true, null)
on conflict (id) do update
  set name = excluded.name, is_picked_up = excluded.is_picked_up, picked_up_at = excluded.picked_up_at;
reset role;
select results_eq(
  $q$ select ticked_at, source from public.ticks
      where item_id = '0000000e-0000-0000-0000-000000000104' $q$,
  $q$ values (tests.ms_ago('3 hours'), 'app'::text) $q$,
  'R2. an upsert of an already-ticked item with picked_up_at null writes no tick'
);
select results_eq(
  $q$ select user_id, name, deleted_at is null from public.purchases
      where source_item_id = '0000000e-0000-0000-0000-000000000104' order by user_id $q$,
  $q$ values ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid, 'Eggs'::text, true),
             ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'::uuid, 'Eggs'::text, true) $q$,
  'R2. ... and leaves the purchases unchanged'
);
select is(
  (select picked_up_at from public.items where id = '0000000e-0000-0000-0000-000000000104'),
  tests.ms_ago('3 hours'),
  'R2. ... and leaves picked_up_at unchanged'
);

-- R3. A real insert of a ticked item without a tick time.
select tests.act_as('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
insert into public.items (id, list_id, name, is_picked_up)
values ('0000000e-0000-0000-0000-000000000105', '0000000a-0000-0000-0000-000000000021', 'Butter', true);
reset role;
select results_eq(
  $q$ select id, source, ticked_at from public.ticks
      where item_id = '0000000e-0000-0000-0000-000000000105' $q$,
  $q$ select got_this_private.tick_id('0000000e-0000-0000-0000-000000000105', date_trunc('milliseconds', now())),
             'legacy'::text, date_trunc('milliseconds', now()) $q$,
  'R3. inserting a ticked item without picked_up_at writes exactly one legacy tick'
);
select is(
  (select picked_up_at from public.items where id = '0000000e-0000-0000-0000-000000000105'),
  date_trunc('milliseconds', now()),
  'R3. ... and stamps picked_up_at with its time'
);

-- R4. The main old-app path: an upsert (no picked_up_at in the payload) that
--     ticks an existing unticked item.
select tests.act_as('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
insert into public.items (id, list_id, name, is_picked_up)
values ('0000000e-0000-0000-0000-000000000106', '0000000a-0000-0000-0000-000000000021', 'Tea', true)
on conflict (id) do update set name = excluded.name, is_picked_up = excluded.is_picked_up;
reset role;
select results_eq(
  $q$ select id, source, ticked_at from public.ticks
      where item_id = '0000000e-0000-0000-0000-000000000106' $q$,
  $q$ select got_this_private.tick_id('0000000e-0000-0000-0000-000000000106', date_trunc('milliseconds', now())),
             'legacy'::text, date_trunc('milliseconds', now()) $q$,
  'R4. an old-app upsert that ticks an unticked item writes exactly one legacy tick'
);
select is(
  (select picked_up_at from public.items where id = '0000000e-0000-0000-0000-000000000106'),
  date_trunc('milliseconds', now()),
  'R4. ... and sets picked_up_at to its time'
);
select results_eq(
  $q$ select user_id from public.purchases
      where source_item_id = '0000000e-0000-0000-0000-000000000106' order by user_id $q$,
  $q$ values ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid), ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'::uuid) $q$,
  'R4. ... and gives A and B one purchase each'
);

-- 8. Backfill. Items ticked before 006 have no picked_up_at; seed them with
--    both items triggers off, so updated_at and picked_up_at stay as given.
alter table public.items disable trigger items_set_updated_at;
alter table public.items disable trigger items_track_picked_up_at;
insert into public.items (id, list_id, name, is_picked_up, updated_at) values
  ('0000000e-0000-0000-0000-000000000111', '0000000a-0000-0000-0000-000000000021', 'Old 1', true, tests.ms_ago('20 days')),
  ('0000000e-0000-0000-0000-000000000112', '0000000a-0000-0000-0000-000000000021', 'Old 2', true, tests.ms_ago('5 days'));
insert into public.items (id, list_id, name, is_picked_up, updated_at, deleted_at) values
  ('0000000e-0000-0000-0000-000000000113', '0000000a-0000-0000-0000-000000000021', 'Old 3', true, tests.ms_ago('3 days'), now() - interval '3 days');
alter table public.items enable trigger items_set_updated_at;
alter table public.items enable trigger items_track_picked_up_at;

select is(got_this_private.backfill_ticked_items(), 2, '8. backfill reports the two live ticked items');
select results_eq(
  $q$ select item_id, ticked_at, source from public.ticks
      where item_id in ('0000000e-0000-0000-0000-000000000111', '0000000e-0000-0000-0000-000000000112',
                        '0000000e-0000-0000-0000-000000000113')
      order by ticked_at $q$,
  $q$ values ('0000000e-0000-0000-0000-000000000111'::uuid, tests.ms_ago('20 days'), 'backfill'::text),
             ('0000000e-0000-0000-0000-000000000112'::uuid, tests.ms_ago('5 days'), 'backfill'::text) $q$,
  '8. each live ticked item gets a backfill tick at its updated_at; the deleted one none'
);
select results_eq(
  $q$ select id, picked_up_at, updated_at from public.items
      where id in ('0000000e-0000-0000-0000-000000000111', '0000000e-0000-0000-0000-000000000112')
      order by id $q$,
  $q$ values ('0000000e-0000-0000-0000-000000000111'::uuid, tests.ms_ago('20 days'), tests.ms_ago('20 days')),
             ('0000000e-0000-0000-0000-000000000112'::uuid, tests.ms_ago('5 days'), tests.ms_ago('5 days')) $q$,
  '8. picked_up_at is set to updated_at, and updated_at does not move'
);
select results_eq(
  $q$ select source_item_id from public.purchases
      where user_id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'
        and source_item_id in ('0000000e-0000-0000-0000-000000000111', '0000000e-0000-0000-0000-000000000112',
                               '0000000e-0000-0000-0000-000000000113') $q$,
  $q$ values ('0000000e-0000-0000-0000-000000000112'::uuid) $q$,
  '8. B is credited only with the item ticked after B joined'
);
select is(
  (select string_agg(tgname::text || ':' || tgenabled::text, ',' order by tgname::text)
     from pg_catalog.pg_trigger
    where tgrelid = 'public.items'::regclass
      and tgname in ('items_set_updated_at', 'items_track_picked_up_at')),
  'items_set_updated_at:O,items_track_picked_up_at:O',
  '8. backfill leaves both items triggers enabled'
);
select is(got_this_private.backfill_ticked_items(), 0, '8. a second backfill finds nothing to do');

select * from finish();
rollback;
