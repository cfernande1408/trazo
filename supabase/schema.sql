-- Comunidad de Trazo en Supabase: valoraciones de rutas y avisos en el mapa.
-- Se pega entero en Supabase → SQL Editor → New query → Run.
-- Usuarios anónimos: Authentication → Sign In / Providers → "Allow anonymous sign-ins".

-- Valoraciones de rutas ---------------------------------------------------
create table if not exists public.route_reviews (
  id          bigint generated always as identity primary key,
  route_key   text        not null check (length(route_key) between 3 and 200),
  user_id     uuid        not null default auth.uid() references auth.users on delete cascade,
  rating      smallint    not null check (rating between 1 and 5),
  comment     text        check (length(comment) <= 1000),
  author      text        check (length(author) <= 40),
  activity    text        check (activity in ('BIKE', 'HIKE')),
  created_at  timestamptz not null default now(),
  unique (route_key, user_id)            -- una valoración por persona y ruta
);
create index if not exists route_reviews_key on public.route_reviews (route_key, created_at desc);

alter table public.route_reviews enable row level security;
create policy "todos leen"          on public.route_reviews for select using (true);
create policy "cada uno la suya"    on public.route_reviews for insert with check (user_id = auth.uid());
create policy "editar la suya"      on public.route_reviews for update using (user_id = auth.uid());
create policy "borrar la suya"      on public.route_reviews for delete using (user_id = auth.uid());

-- Avisos en el camino -----------------------------------------------------
create table if not exists public.hazards (
  id          bigint generated always as identity primary key,
  user_id     uuid        not null default auth.uid() references auth.users on delete cascade,
  kind        text        not null check (kind in ('ICE','SNOW','MUD','FALLEN_TREE','CLOSED','WATER','LIVESTOCK','HUNTING','OTHER')),
  lat         double precision not null check (lat between -90 and 90),
  lon         double precision not null check (lon between -180 and 180),
  note        text        check (length(note) <= 300),
  created_at  timestamptz not null default now(),
  expires_at  timestamptz not null check (expires_at <= now() + interval '31 days'),
  confirms    int         not null default 0,
  gone        int         not null default 0
);
create index if not exists hazards_where on public.hazards (lat, lon);
create index if not exists hazards_alive on public.hazards (expires_at);

alter table public.hazards enable row level security;
create policy "todos leen"       on public.hazards for select using (true);
create policy "cada uno el suyo" on public.hazards for insert with check (user_id = auth.uid() and confirms = 0 and gone = 0);
create policy "borrar el suyo"   on public.hazards for delete using (user_id = auth.uid());

-- "Sigue ahí" / "Ya no está": un voto por persona, los totales los lleva la función
create table if not exists public.hazard_votes (
  hazard_id   bigint  not null references public.hazards on delete cascade,
  user_id     uuid    not null default auth.uid() references auth.users on delete cascade,
  still_there boolean not null,
  primary key (hazard_id, user_id)
);
alter table public.hazard_votes enable row level security;
create policy "ver los propios" on public.hazard_votes for select using (user_id = auth.uid());

create or replace function public.vote_hazard(h bigint, still boolean)
returns void language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'hay que iniciar sesión'; end if;
  insert into hazard_votes (hazard_id, user_id, still_there) values (h, auth.uid(), still)
    on conflict (hazard_id, user_id) do update set still_there = excluded.still_there;
  update hazards set
    confirms = (select count(*) from hazard_votes v where v.hazard_id = h and v.still_there),
    gone     = (select count(*) from hazard_votes v where v.hazard_id = h and not v.still_there),
    -- Si alguien confirma que sigue, aguanta unos días más
    expires_at = case when still then greatest(expires_at, least(now() + interval '3 days', created_at + interval '31 days')) else expires_at end
  where id = h;
end $$;
grant execute on function public.vote_hazard(bigint, boolean) to authenticated;

-- Limpieza: los caducados se pueden borrar a mano o con pg_cron
-- select cron.schedule('trazo-limpieza', '0 4 * * *', $$delete from public.hazards where expires_at < now() - interval '7 days'$$);
