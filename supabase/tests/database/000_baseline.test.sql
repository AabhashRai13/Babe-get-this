-- Baseline: migrations 001-005 and the seed helpers are in place.
begin;
create extension if not exists pgtap with schema extensions;
select plan(5);

select has_table('public'::name, 'lists'::name, 'lists exists');
select has_table('public'::name, 'items'::name, 'items exists');
select has_table('public'::name, 'list_members'::name, 'list_members exists');
select has_function('public'::name, 'join_list_by_code'::name, 'join_list_by_code exists');

select tests.make_user('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
select tests.act_as('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');

insert into public.lists (id, name, created_by)
values ('0000000a-0000-0000-0000-000000000001', 'Groceries', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');

select is(
  (select count(*)::int from public.list_members
   where list_id = '0000000a-0000-0000-0000-000000000001'),
  1,
  'creating a list makes its creator a member'
);

reset role;

select * from finish();
rollback;
