-- Tick events become purchases (006_got_this.sql, spec "Server" steps 1-9).
-- Users: A creates every list; B joined L, L2, L3 and L4 ten days ago; C is on
-- no list until case 10; D is on L5. Most ticks are one day old (T).
begin;
create extension if not exists pgtap with schema extensions;
select plan(25);

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

select tests.make_user('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
select tests.make_user('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb');
select tests.make_user('cccccccc-cccc-cccc-cccc-cccccccccccc');
select tests.make_user('dddddddd-dddd-dddd-dddd-dddddddddddd');

-- The creator's membership comes from lists_add_creator, with joined_at = now().
insert into public.lists (id, name, created_by) values
  ('0000000a-0000-0000-0000-000000000001', 'L',  'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'),
  ('0000000a-0000-0000-0000-000000000002', 'L2', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'),
  ('0000000a-0000-0000-0000-000000000003', 'L3', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'),
  ('0000000a-0000-0000-0000-000000000004', 'L4', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'),
  ('0000000a-0000-0000-0000-000000000005', 'L5', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');

insert into public.list_members (list_id, user_id, joined_at) values
  ('0000000a-0000-0000-0000-000000000001', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', now() - interval '10 days'),
  ('0000000a-0000-0000-0000-000000000002', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', now() - interval '10 days'),
  ('0000000a-0000-0000-0000-000000000003', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', now() - interval '10 days'),
  ('0000000a-0000-0000-0000-000000000004', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', now() - interval '10 days'),
  ('0000000a-0000-0000-0000-000000000005', 'dddddddd-dddd-dddd-dddd-dddddddddddd', now() - interval '10 days');

-- One item per case, so each case counts only its own purchases.
insert into public.items (id, list_id, name) values
  ('0000000e-0000-0000-0000-000000000001', '0000000a-0000-0000-0000-000000000001', 'Milk'),
  ('0000000e-0000-0000-0000-000000000002', '0000000a-0000-0000-0000-000000000001', 'Milk'),
  ('0000000e-0000-0000-0000-000000000003', '0000000a-0000-0000-0000-000000000001', 'Milk'),
  ('0000000e-0000-0000-0000-000000000004', '0000000a-0000-0000-0000-000000000001', 'Milk'),
  ('0000000e-0000-0000-0000-000000000005', '0000000a-0000-0000-0000-000000000001', 'Milk'),
  ('0000000e-0000-0000-0000-000000000006', '0000000a-0000-0000-0000-000000000001', 'Milk'),
  ('0000000e-0000-0000-0000-000000000007', '0000000a-0000-0000-0000-000000000001', 'Milk'),
  ('0000000e-0000-0000-0000-000000000008', '0000000a-0000-0000-0000-000000000001', 'Milk'),
  ('0000000e-0000-0000-0000-000000000009', '0000000a-0000-0000-0000-000000000001', 'Milk'),
  ('0000000e-0000-0000-0000-000000000010', '0000000a-0000-0000-0000-000000000002', 'Milk'),
  ('0000000e-0000-0000-0000-000000000011', '0000000a-0000-0000-0000-000000000003', 'Milk'),
  ('0000000e-0000-0000-0000-000000000012', '0000000a-0000-0000-0000-000000000004', 'Milk'),
  ('0000000e-0000-0000-0000-000000000013', '0000000a-0000-0000-0000-000000000005', 'Milk');

-- 1. Pinned id vectors (shared with GotThisIdsTest on the phone).
select is(
  got_this_private.tick_id('11111111-1111-1111-1111-111111111111', '2026-11-01T10:00:00Z'),
  'ce0087de-31d1-fb82-a1e7-70abb1f8ce87'::uuid,
  '1. tick_id matches the pinned vector'
);
select is(
  got_this_private.purchase_id('22222222-2222-2222-2222-222222222222', 'ce0087de-31d1-fb82-a1e7-70abb1f8ce87'),
  'c680a8ae-0383-3f3c-356c-4a16906dd012'::uuid,
  '1. purchase_id matches the pinned vector'
);

-- 2. A tick credits the creator and the member.
select tests.act_as('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
select public.record_ticks(jsonb_build_array(
  tests.ev('0000000e-0000-0000-0000-000000000001', '0000000a-0000-0000-0000-000000000001', now() - interval '1 day')
));
reset role;
select results_eq(
  $q$ select user_id, name, category_id from public.purchases
      where tick_id = got_this_private.tick_id('0000000e-0000-0000-0000-000000000001', now() - interval '1 day')
      order by user_id $q$,
  $q$ values ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid, 'Milk'::text, 'dairy'::text),
             ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'::uuid, 'Milk'::text, 'dairy'::text) $q$,
  '2. a tick gives A and B one purchase each, with the event''s name and category'
);

-- 3. The same event again changes nothing.
select tests.act_as('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
select public.record_ticks(jsonb_build_array(
  tests.ev('0000000e-0000-0000-0000-000000000001', '0000000a-0000-0000-0000-000000000001', now() - interval '1 day')
));
reset role;
select is(
  (select count(*)::int from public.purchases
   where tick_id = got_this_private.tick_id('0000000e-0000-0000-0000-000000000001', now() - interval '1 day')),
  2,
  '3. a repeated event still leaves one purchase each'
);

-- 4. Two tick times for one item are two ticks.
select tests.act_as('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
select public.record_ticks(jsonb_build_array(
  tests.ev('0000000e-0000-0000-0000-000000000002', '0000000a-0000-0000-0000-000000000001', now() - interval '2 days'),
  tests.ev('0000000e-0000-0000-0000-000000000002', '0000000a-0000-0000-0000-000000000001', now() - interval '1 day')
));
reset role;
select results_eq(
  $q$ select user_id, count(*)::int from public.purchases
      where source_item_id = '0000000e-0000-0000-0000-000000000002'
      group by user_id order by user_id $q$,
  $q$ values ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid, 2),
             ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'::uuid, 2) $q$,
  '4. two events for one item in one call give two purchases each'
);

-- 5. An undo after the tick tombstones every purchase of that tick.
select tests.act_as('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
select public.record_ticks(jsonb_build_array(
  tests.ev('0000000e-0000-0000-0000-000000000003', '0000000a-0000-0000-0000-000000000001', now() - interval '1 day')
));
select public.record_ticks(jsonb_build_array(
  tests.ev('0000000e-0000-0000-0000-000000000003', '0000000a-0000-0000-0000-000000000001', now() - interval '1 day',
           p_undone => now() - interval '1 day' + interval '1 hour')
));
reset role;
select is(
  (select count(*)::int from public.purchases
   where source_item_id = '0000000e-0000-0000-0000-000000000003' and deleted_at is not null),
  2,
  '5. an undo within 2 hours tombstones both purchases'
);

-- 6. An undo that arrives first wins over the live copy arriving later.
select tests.act_as('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
select public.record_ticks(jsonb_build_array(
  tests.ev('0000000e-0000-0000-0000-000000000004', '0000000a-0000-0000-0000-000000000001', now() - interval '1 day',
           p_undone => now() - interval '1 day' + interval '30 minutes')
));
select public.record_ticks(jsonb_build_array(
  tests.ev('0000000e-0000-0000-0000-000000000004', '0000000a-0000-0000-0000-000000000001', now() - interval '1 day')
));
reset role;
select is(
  (select count(*)::int from public.purchases
   where source_item_id = '0000000e-0000-0000-0000-000000000004'),
  0,
  '6. a tick that first arrives undone never makes purchases, even when the live copy follows'
);

-- 7. An undo outside the 2-hour window is ignored and the tick kept.
select tests.act_as('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
select public.record_ticks(jsonb_build_array(
  tests.ev('0000000e-0000-0000-0000-000000000005', '0000000a-0000-0000-0000-000000000001', now() - interval '1 day',
           p_undone => now() - interval '1 day' + interval '3 hours')
));
reset role;
select is(
  (select count(*)::int from public.purchases
   where source_item_id = '0000000e-0000-0000-0000-000000000005' and deleted_at is null),
  2,
  '7. an undo 3 hours after the tick is ignored; both purchases are live'
);

-- 8. The first copy's name is kept.
select tests.act_as('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
select public.record_ticks(jsonb_build_array(
  tests.ev('0000000e-0000-0000-0000-000000000001', '0000000a-0000-0000-0000-000000000001', now() - interval '1 day',
           p_name => 'Oat milk')
));
reset role;
select is(
  (select name from public.ticks
   where id = got_this_private.tick_id('0000000e-0000-0000-0000-000000000001', now() - interval '1 day')),
  'Milk',
  '8. a second copy with a different name leaves the stored name unchanged'
);

-- 9. A member is credited only with ticks from after they joined.
select tests.act_as('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
select public.record_ticks(jsonb_build_array(
  tests.ev('0000000e-0000-0000-0000-000000000006', '0000000a-0000-0000-0000-000000000001', now() - interval '15 days')
));
reset role;
select results_eq(
  $q$ select user_id from public.purchases
      where source_item_id = '0000000e-0000-0000-0000-000000000006' $q$,
  $q$ values ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid) $q$,
  '9. a tick before B joined credits only A'
);

-- 10. Someone who joins after a tick is not touched by its undo.
select tests.act_as('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
select public.record_ticks(jsonb_build_array(
  tests.ev('0000000e-0000-0000-0000-000000000010', '0000000a-0000-0000-0000-000000000002', now() - interval '1 day')
));
reset role;
insert into public.list_members (list_id, user_id)
values ('0000000a-0000-0000-0000-000000000002', 'cccccccc-cccc-cccc-cccc-cccccccccccc');
select tests.act_as('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
select public.record_ticks(jsonb_build_array(
  tests.ev('0000000e-0000-0000-0000-000000000010', '0000000a-0000-0000-0000-000000000002', now() - interval '1 day',
           p_undone => now() - interval '1 day' + interval '1 hour')
));
reset role;
select results_eq(
  $q$ select user_id, deleted_at is not null from public.purchases
      where source_item_id = '0000000e-0000-0000-0000-000000000010'
      order by user_id $q$,
  $q$ values ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid, true),
             ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'::uuid, true) $q$,
  '10. C, who joined after the tick, has no purchase; A''s and B''s are tombstoned'
);

-- 11. A non-member's event is rejected and stores nothing.
select tests.act_as('cccccccc-cccc-cccc-cccc-cccccccccccc');
select is(
  public.record_ticks(jsonb_build_array(
    tests.ev('0000000e-0000-0000-0000-000000000007', '0000000a-0000-0000-0000-000000000001', now() - interval '1 day')
  )),
  jsonb_build_object(
    'accepted', '[]'::jsonb,
    'retry', '[]'::jsonb,
    'rejected', jsonb_build_array(
      tests.ev('0000000e-0000-0000-0000-000000000007', '0000000a-0000-0000-0000-000000000001', now() - interval '1 day') ->> 'id'
    )
  ),
  '11. C is not a member of L: the event is rejected'
);
reset role;
select is(
  (select count(*)::int from public.ticks where item_id = '0000000e-0000-0000-0000-000000000007'),
  0,
  '11. the rejected event wrote no tick'
);

-- 12. A caller cannot choose the id.
select tests.act_as('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
select is(
  public.record_ticks(jsonb_build_array(
    tests.ev('0000000e-0000-0000-0000-000000000009', '0000000a-0000-0000-0000-000000000001', now() - interval '1 day')
      || '{"id": "99999999-9999-9999-9999-999999999999"}'::jsonb
  )),
  '{"accepted": [], "retry": [], "rejected": ["99999999-9999-9999-9999-999999999999"]}'::jsonb,
  '12. an id that is not the recomputed tick id is rejected'
);

-- 13. The time window.
select is(
  public.record_ticks(jsonb_build_array(
    tests.ev('0000000e-0000-0000-0000-000000000008', '0000000a-0000-0000-0000-000000000001', now() - interval '31 days'),
    tests.ev('0000000e-0000-0000-0000-000000000008', '0000000a-0000-0000-0000-000000000001', now() + interval '31 days'),
    tests.ev('0000000e-0000-0000-0000-000000000008', '0000000a-0000-0000-0000-000000000001', now() + interval '5 days'),
    tests.ev('0000000e-0000-0000-0000-000000000008', '0000000a-0000-0000-0000-000000000001', now() + interval '12 hours')
  )),
  jsonb_build_object(
    'accepted', jsonb_build_array(
      tests.ev('0000000e-0000-0000-0000-000000000008', '0000000a-0000-0000-0000-000000000001', now() + interval '12 hours') ->> 'id'
    ),
    'retry', jsonb_build_array(
      tests.ev('0000000e-0000-0000-0000-000000000008', '0000000a-0000-0000-0000-000000000001', now() + interval '5 days') ->> 'id'
    ),
    'rejected', jsonb_build_array(
      tests.ev('0000000e-0000-0000-0000-000000000008', '0000000a-0000-0000-0000-000000000001', now() - interval '31 days') ->> 'id',
      tests.ev('0000000e-0000-0000-0000-000000000008', '0000000a-0000-0000-0000-000000000001', now() + interval '31 days') ->> 'id'
    )
  ),
  '13. 31 days ago and 31 days ahead are rejected, 5 days ahead is retried, 12 hours ahead is accepted'
);

-- 14. An item the server has not seen yet is retried.
select is(
  public.record_ticks(jsonb_build_array(
    tests.ev('0000000e-0000-0000-0000-000000000099', '0000000a-0000-0000-0000-000000000001', now() - interval '1 day')
  )),
  jsonb_build_object(
    'accepted', '[]'::jsonb,
    'retry', jsonb_build_array(
      tests.ev('0000000e-0000-0000-0000-000000000099', '0000000a-0000-0000-0000-000000000001', now() - interval '1 day') ->> 'id'
    ),
    'rejected', '[]'::jsonb
  ),
  '14. an event for an item not on the server is retried'
);

-- 15. Size limits.
select throws_ok(
  $q$ select public.record_ticks((select jsonb_agg(g) from generate_series(1, 501) as g)) $q$,
  'P0001',
  'too_many_events',
  '15. 501 events are refused as a whole'
);
select is(
  public.record_ticks(jsonb_build_array(
    tests.ev('0000000e-0000-0000-0000-000000000009', '0000000a-0000-0000-0000-000000000001', now() - interval '1 day',
             p_name => repeat('x', 501))
  )),
  jsonb_build_object(
    'accepted', '[]'::jsonb,
    'retry', '[]'::jsonb,
    'rejected', jsonb_build_array(
      tests.ev('0000000e-0000-0000-0000-000000000009', '0000000a-0000-0000-0000-000000000001', now() - interval '1 day') ->> 'id'
    )
  ),
  '15. a 501-character name is rejected'
);
reset role;

-- 16. A tombstoned list still records ticks.
update public.lists set deleted_at = now() where id = '0000000a-0000-0000-0000-000000000003';
select tests.act_as('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
select public.record_ticks(jsonb_build_array(
  tests.ev('0000000e-0000-0000-0000-000000000011', '0000000a-0000-0000-0000-000000000003', now() - interval '1 day')
));
reset role;
select results_eq(
  $q$ select user_id from public.purchases
      where source_item_id = '0000000e-0000-0000-0000-000000000011'
      order by user_id $q$,
  $q$ values ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid),
             ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'::uuid) $q$,
  '16. on a tombstoned list the event is still accepted for A and B'
);

-- 17. A list whose creator deleted their account (created_by null). A's own
--     membership dates from now(), after the tick, so only B is credited.
update public.lists set created_by = null where id = '0000000a-0000-0000-0000-000000000004';
select tests.act_as('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb');
select public.record_ticks(jsonb_build_array(
  tests.ev('0000000e-0000-0000-0000-000000000012', '0000000a-0000-0000-0000-000000000004', now() - interval '1 day')
));
reset role;
select results_eq(
  $q$ select user_id from public.purchases
      where source_item_id = '0000000e-0000-0000-0000-000000000012' $q$,
  $q$ values ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'::uuid) $q$,
  '17. with created_by null, member B gets the purchase and nothing fails'
);

-- 18. Someone who cleared their history after the tick time is skipped.
insert into public.purchase_history_resets (user_id, reset_at)
values ('dddddddd-dddd-dddd-dddd-dddddddddddd', now() - interval '12 hours');
select tests.act_as('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
select public.record_ticks(jsonb_build_array(
  tests.ev('0000000e-0000-0000-0000-000000000013', '0000000a-0000-0000-0000-000000000005', now() - interval '1 day')
));
reset role;
select results_eq(
  $q$ select user_id from public.purchases
      where source_item_id = '0000000e-0000-0000-0000-000000000013' $q$,
  $q$ values ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid) $q$,
  '18. D, whose reset_at is after the tick, gets no purchase; A does'
);

-- 19. RLS.
select tests.act_as('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb');
select results_eq(
  $q$ select distinct user_id from public.purchases $q$,
  $q$ values ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'::uuid) $q$,
  '19. B sees only B''s purchases'
);
select is(
  (select count(*)::int from public.ticks),
  0,
  '19. B sees no ticks'
);
reset role;

-- 20. Internal functions are out of reach.
select tests.act_as('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
select throws_ok(
  $q$ select got_this_private.record_tick_rows(
        gen_random_uuid(), gen_random_uuid(), '0000000a-0000-0000-0000-000000000001'::uuid,
        now(), 'Milk', null::text, null::timestamptz, 'app') $q$,
  '42501',
  null,
  '20. a signed-in user cannot call record_tick_rows'
);
reset role;

-- 21. Anonymous callers cannot record ticks.
select tests.act_as_anon();
select throws_ok(
  $q$ select public.record_ticks('[]') $q$,
  '42501',
  null,
  '21. anon cannot call record_ticks'
);
reset role;

select * from finish();
rollback;
