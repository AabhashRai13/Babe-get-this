-- Clearing history, own purchases, the weekly claim and the purge
-- (006_got_this.sql, spec "Server" steps 10-12 and "Permissions and cleanup").
-- Times of day are yesterday's (UTC). A owns solo list SA and list L, which B
-- joined ten days ago.
begin;
create extension if not exists pgtap with schema extensions;
select plan(19);

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

-- Yesterday at the given time of day.
create function tests.yesterday_at(p_time interval)
returns timestamptz
language sql
stable
as $$
  select date_trunc('day', now()) - interval '1 day' + p_time;
$$;

select tests.make_user('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
select tests.make_user('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb');
select tests.make_user('cccccccc-cccc-cccc-cccc-cccccccccccc');

insert into public.lists (id, name, created_by) values
  ('0000000a-0000-0000-0000-000000000011', 'SA', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'),
  ('0000000a-0000-0000-0000-000000000012', 'L',  'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');

insert into public.list_members (list_id, user_id, joined_at)
values ('0000000a-0000-0000-0000-000000000012', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', now() - interval '10 days');

insert into public.items (id, list_id, name) values
  ('0000000e-0000-0000-0000-000000000031', '0000000a-0000-0000-0000-000000000011', 'Bread'),
  ('0000000e-0000-0000-0000-000000000032', '0000000a-0000-0000-0000-000000000011', 'Bread'),
  ('0000000e-0000-0000-0000-000000000021', '0000000a-0000-0000-0000-000000000012', 'Milk'),
  ('0000000e-0000-0000-0000-000000000022', '0000000a-0000-0000-0000-000000000012', 'Milk');

-- A's purchases at 09:00 and 11:00, on the solo list.
select got_this_private.record_tick_rows(
  got_this_private.tick_id('0000000e-0000-0000-0000-000000000031', tests.yesterday_at('9 hours')),
  '0000000e-0000-0000-0000-000000000031', '0000000a-0000-0000-0000-000000000011',
  tests.yesterday_at('9 hours'), 'Bread', null, null, 'app');
select got_this_private.record_tick_rows(
  got_this_private.tick_id('0000000e-0000-0000-0000-000000000032', tests.yesterday_at('11 hours')),
  '0000000e-0000-0000-0000-000000000032', '0000000a-0000-0000-0000-000000000011',
  tests.yesterday_at('11 hours'), 'Bread', null, null, 'app');

-- 1. Clear at 10:00.
select tests.act_as('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
select is(
  public.clear_purchase_history(tests.yesterday_at('10 hours')),
  tests.yesterday_at('10 hours'),
  '1. clear_purchase_history returns the new reset time, 10:00'
);
reset role;
select results_eq(
  $q$ select bought_at from public.purchases
      where user_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa' order by bought_at $q$,
  $q$ select tests.yesterday_at('11 hours') $q$,
  '1. the 09:00 purchase is deleted and the 11:00 one kept'
);

-- 2. The reset never moves back.
select tests.act_as('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
select is(
  public.clear_purchase_history(tests.yesterday_at('8 hours')),
  tests.yesterday_at('10 hours'),
  '2. a later clear at 08:00 leaves the reset at 10:00'
);
reset role;

-- 3. A clear from the future counts from now.
select tests.act_as('cccccccc-cccc-cccc-cccc-cccccccccccc');
select is(
  public.clear_purchase_history(now() + interval '1 day'),
  now(),
  '3. a future p_cleared_at is capped at now()'
);
reset role;

-- 4. Ticks that arrive after A's clear.
select tests.act_as('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
select public.record_ticks(jsonb_build_array(
  tests.ev('0000000e-0000-0000-0000-000000000021', '0000000a-0000-0000-0000-000000000012', tests.yesterday_at('9 hours 30 minutes')),
  tests.ev('0000000e-0000-0000-0000-000000000022', '0000000a-0000-0000-0000-000000000012', tests.yesterday_at('10 hours 30 minutes'))
));
reset role;
select results_eq(
  $q$ select user_id, bought_at from public.purchases
      where source_item_id in ('0000000e-0000-0000-0000-000000000021', '0000000e-0000-0000-0000-000000000022')
      order by bought_at, user_id $q$,
  $q$ values ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'::uuid, tests.yesterday_at('9 hours 30 minutes')),
             ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid, tests.yesterday_at('10 hours 30 minutes')),
             ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'::uuid, tests.yesterday_at('10 hours 30 minutes')) $q$,
  '4. a 09:30 tick credits only B; a 10:30 tick credits A and B'
);

-- 5. Own purchases: recomputed id, idempotent, nothing at or before the reset.
select tests.act_as('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
select public.upload_own_purchases(jsonb_build_array(jsonb_build_object(
  'tick_id', 'f0000000-0000-0000-0000-000000000001',
  'source_item_id', 'f1000000-0000-0000-0000-000000000001',
  'name', 'Eggs', 'category_id', null, 'bought_at', tests.yesterday_at('12 hours'))));
select public.upload_own_purchases(jsonb_build_array(jsonb_build_object(
  'tick_id', 'f0000000-0000-0000-0000-000000000001',
  'source_item_id', 'f1000000-0000-0000-0000-000000000001',
  'name', 'Eggs', 'category_id', null, 'bought_at', tests.yesterday_at('12 hours'))));
select public.upload_own_purchases(jsonb_build_array(jsonb_build_object(
  'tick_id', 'f0000000-0000-0000-0000-000000000002',
  'source_item_id', 'f1000000-0000-0000-0000-000000000002',
  'name', 'Eggs', 'category_id', null, 'bought_at', tests.yesterday_at('9 hours 30 minutes'))));
reset role;
select results_eq(
  $q$ select id from public.purchases
      where tick_id in ('f0000000-0000-0000-0000-000000000001', 'f0000000-0000-0000-0000-000000000002') $q$,
  $q$ select got_this_private.purchase_id('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'f0000000-0000-0000-0000-000000000001') $q$,
  '5. one purchase under the recomputed id after two uploads; the row before the reset is skipped'
);

-- 6. Only ever the caller's own rows.
select tests.act_as('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb');
select public.upload_own_purchases(jsonb_build_array(jsonb_build_object(
  'user_id', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  'tick_id', 'f0000000-0000-0000-0000-000000000003',
  'source_item_id', 'f1000000-0000-0000-0000-000000000003',
  'name', 'Eggs', 'category_id', null, 'bought_at', tests.yesterday_at('12 hours'))));
reset role;
select results_eq(
  $q$ select user_id from public.purchases where tick_id = 'f0000000-0000-0000-0000-000000000003' $q$,
  $q$ values ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'::uuid) $q$,
  '6. a row naming another user is stored for the caller'
);

-- 7. Batch limit.
select tests.act_as('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
select throws_ok(
  $q$ select public.upload_own_purchases((select jsonb_agg(jsonb_build_object('n', g)) from generate_series(1, 501) as g)) $q$,
  'P0001',
  'too_many_rows',
  '7. 501 rows are refused as a whole'
);

-- 8. One running-low notice per person per week.
select is(public.claim_running_low_week('2026-W46'), true, '8. A claims 2026-W46');
select is(public.claim_running_low_week('2026-W46'), false, '8. A cannot claim 2026-W46 twice');
select throws_ok(
  $q$ select public.claim_running_low_week('banana') $q$,
  'P0001',
  'bad_week',
  '8. a malformed week is refused'
);
reset role;
select tests.act_as('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb');
select is(public.claim_running_low_week('2026-W46'), true, '8. B claims the same week for B');
reset role;

-- 9. Purge.
insert into public.ticks (id, item_id, list_id, ticked_at, name, source, created_at) values
  ('0000000c-0000-0000-0000-000000000061', '0000000e-0000-0000-0000-000000000021',
   '0000000a-0000-0000-0000-000000000012', now() - interval '61 days', 'Old', 'app', now() - interval '61 days'),
  ('0000000c-0000-0000-0000-000000000059', '0000000e-0000-0000-0000-000000000021',
   '0000000a-0000-0000-0000-000000000012', now() - interval '59 days', 'Old', 'app', now() - interval '59 days');
insert into public.running_low_notices (user_id, iso_week, posted_at)
values ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '2026-W30', now() - interval '9 weeks');
select got_this_private.purge();
select results_eq(
  $q$ select id from public.ticks
      where id in ('0000000c-0000-0000-0000-000000000061', '0000000c-0000-0000-0000-000000000059') $q$,
  $q$ values ('0000000c-0000-0000-0000-000000000059'::uuid) $q$,
  '9. purge deletes the 61-day-old tick and keeps the 59-day-old one'
);
select is(
  (select count(*)::int from public.running_low_notices where iso_week = '2026-W30'),
  0,
  '9. purge deletes a 9-week-old running-low notice'
);

-- 10. Anonymous callers.
select tests.act_as_anon();
select throws_ok(
  $q$ select public.clear_purchase_history(now()) $q$,
  '42501',
  null,
  '10. anon cannot call clear_purchase_history'
);
select throws_ok(
  $q$ select public.upload_own_purchases('[]') $q$,
  '42501',
  null,
  '10. anon cannot call upload_own_purchases'
);
select throws_ok(
  $q$ select public.claim_running_low_week('2026-W46') $q$,
  '42501',
  null,
  '10. anon cannot call claim_running_low_week'
);
reset role;

-- 11. A row that does not parse is skipped; the rest of the batch is stored.
select tests.act_as('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
select lives_ok(
  $q$ select public.upload_own_purchases(jsonb_build_array(
        jsonb_build_object(
          'tick_id', 'f0000000-0000-0000-0000-000000000004',
          'source_item_id', 'f1000000-0000-0000-0000-000000000004',
          'name', 'Eggs', 'category_id', null, 'bought_at', tests.yesterday_at('12 hours')),
        jsonb_build_object(
          'tick_id', 'f0000000-0000-0000-0000-000000000005',
          'source_item_id', 'f1000000-0000-0000-0000-000000000005',
          'name', 'Eggs', 'category_id', null, 'bought_at', 'garbage'),
        jsonb_build_object(
          'tick_id', 'not-a-uuid',
          'source_item_id', 'f1000000-0000-0000-0000-000000000006',
          'name', 'Eggs', 'category_id', null, 'bought_at', tests.yesterday_at('12 hours')))) $q$,
  '11. a batch with malformed rows does not fail'
);
reset role;
select results_eq(
  $q$ select tick_id from public.purchases
      where source_item_id in ('f1000000-0000-0000-0000-000000000004', 'f1000000-0000-0000-0000-000000000005',
                               'f1000000-0000-0000-0000-000000000006') $q$,
  $q$ values ('f0000000-0000-0000-0000-000000000004'::uuid) $q$,
  '11. only the well-formed row is stored'
);

select * from finish();
rollback;
