\set ON_ERROR_STOP on
begin;
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"01ca161e-7690-4aff-900f-6b0b087313e4","email":"user@premiumpe.test","role":"authenticated"}', true);
do $$
begin
    begin
        insert into public.products(name, category, price) values ('QA escritura sin permiso', 'IA', 9);
        raise exception 'RLS permite una escritura sin permiso';
    exception when insufficient_privilege then
        raise notice 'RLS bloqueó al usuario ajeno a administración';
    end;
end $$;
select set_config('request.jwt.claims', '{"sub":"01ca161e-7690-4aff-900f-6b0b087313e4","email":"admin@premiumpe.com","role":"authenticated"}', true);
insert into storage.objects(bucket_id, name) values ('product-images', 'qa.jpg');
insert into public.products(id, name, category, price, image_url) values
    ('2a000000-1111-2222-3333-444455556666', 'QA producto', 'Streaming', 12, 'https://example.test/qa.jpg'),
    ('2b000000-1111-2222-3333-444455556666', 'QA segundo', 'IA', 14, null);
update public.products set price=13 where id='2a000000-1111-2222-3333-444455556666';
select public.save_promotion(null, '{"name":"QA combo","price":18,"active":true}', array[
    '2a000000-1111-2222-3333-444455556666'::uuid, '2b000000-1111-2222-3333-444455556666'::uuid
]) as qa_offer \gset

reset role;
set local role anon;
select set_config('request.jwt.claims', '{"role":"anon"}', true);
do $$
begin
    assert exists(select 1 from public.products where name='QA producto' and price=13 and image_url is not null);
    assert exists(select 1 from storage.objects where bucket_id='product-images' and name='qa.jpg');
    assert exists(select 1 from public.current_promotions where name='QA combo' and original_price=27);
    raise notice 'Lectura pública de producto, portada y promoción correcta';
end $$;
reset role;
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"01ca161e-7690-4aff-900f-6b0b087313e4","email":"admin@premiumpe.com","role":"authenticated"}', true);
update public.promotions set active=false where id=:'qa_offer';
update public.settings set response_time='QA respuesta' where id=1;
do $$
begin
    assert not exists(select 1 from public.current_promotions where name='QA combo');
    assert exists(select 1 from public.settings where id=1 and response_time='QA respuesta');
    raise notice 'Pausa de promoción y escritura de ajustes correctas';
end $$;
delete from public.promotions where id=:'qa_offer';
delete from public.products where name in ('QA producto','QA segundo');
rollback;
