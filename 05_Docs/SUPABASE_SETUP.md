# EventMap — бэкенд на Supabase (для мобильщика, без серверного кода)

> Цель: заменить демо-фолбэки на живые данные и настоящий логин, не поднимая свой сервер. Supabase даёт Postgres-базу + авто-API + Auth + Storage из веб-админки. Этого хватает для MVP «события на карте + вход».

Делай по шагам сверху вниз. Шаги 1–5 — в браузере (твоя часть). Шаг 6 — код в приложении (можем сделать вместе).

---

## Шаг 1. Создать проект

1. https://supabase.com → Sign in (через GitHub проще всего).
2. **New project**: имя `eventmap`, придумай **Database Password** (запиши — пригодится), регион выбери ближайший (Frankfurt / Singapore — оба ок для КЗ).
3. Подожди ~2 минуты, пока поднимется.

---

## Шаг 2. Таблицы (SQL Editor)

Слева **SQL Editor → New query**, вставь и нажми **Run**. Это создаёт таблицы под наши модели и кладёт демо-события по Астане (чтобы карта была не пустой):

```sql
-- Профили пользователей (связаны с auth.users)
create table profiles (
  id uuid primary key references auth.users on delete cascade,
  name text default '',
  username text default '',
  bio text default '',
  avatar_url text,
  is_organizer boolean default false,
  created_at timestamptz default now()
);

-- Организаторы
create table organizers (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  bio text default '',
  avatar_url text,
  is_verified boolean default false,
  subscribers int default 0,
  created_at timestamptz default now()
);

-- События
create table events (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  description text default '',
  category text default '',
  status text default 'soon',          -- soon | live | ended
  start_at timestamptz,
  end_at timestamptz,
  lat double precision,
  lng double precision,
  location_name text default '',
  price_type text default 'free',       -- free | paid
  price_amount int default 0,
  currency text default 'KZT',
  going_count int default 0,
  live_viewers int default 0,
  rating_avg numeric default 0,
  rating_count int default 0,
  cover text,
  organizer_id uuid references organizers(id),
  source text default 'organizer',      -- organizer | afisha | user
  created_at timestamptz default now()
);

-- Демо-организатор + события по Астане
insert into organizers (id, name, bio, is_verified, subscribers) values
  ('11111111-1111-1111-1111-111111111111', 'Astana Live', 'Концерты и опен-эйры в центре Астаны', true, 1840);

insert into events (title, category, status, lat, lng, location_name, price_type, price_amount, going_count, live_viewers, organizer_id) values
  ('Концерт у Байтерека', 'Концерт', 'live', 51.1283, 71.4306, 'Байтерек, Астана', 'paid', 3000, 48, 128, '11111111-1111-1111-1111-111111111111'),
  ('Астана — Кайрат',     'Спорт',   'live', 51.1083, 71.4156, 'Астана Арена',      'paid', 2000, 540, 540, '11111111-1111-1111-1111-111111111111'),
  ('Йога в парке',        'Йога',    'soon', 51.1380, 71.4100, 'Парк у Хан Шатыра', 'free', 0, 0, 0, null),
  ('Квиз «Мозгобойня»',   'Квиз',    'soon', 51.1450, 71.4300, 'Бар в центре',      'paid', 2500, 0, 0, null),
  ('Открытая лекция: AI', 'Лекция',  'soon', 51.0905, 71.4030, 'Назарбаев Университет', 'free', 0, 0, 0, null);
```

---

## Шаг 3. Auth (вход по email)

1. **Authentication → Providers → Email** — включён по умолчанию.
2. Для тестов: **Authentication → Sign In / Providers → Email** → отключи **Confirm email** (иначе после регистрации надо подтверждать почту). Включишь обратно перед продом.
3. Триггер: при регистрации авто-создавать профиль. В SQL Editor:

```sql
create function public.handle_new_user() returns trigger as $$
begin
  insert into public.profiles (id, name) values (new.id, coalesce(new.raw_user_meta_data->>'name',''));
  return new;
end; $$ language plpgsql security definer;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();
```

---

## Шаг 4. Доступ к данным (RLS)

Supabase по умолчанию закрывает таблицы. Для MVP: события и организаторы — **читать всем**, профиль — править только свой.

```sql
alter table events enable row level security;
alter table organizers enable row level security;
alter table profiles enable row level security;

create policy "events read all"     on events     for select using (true);
create policy "organizers read all" on organizers for select using (true);
create policy "profiles read all"   on profiles   for select using (true);
create policy "profile update own"  on profiles   for update using (auth.uid() = id);
create policy "events insert auth"  on events     for insert with check (auth.role() = 'authenticated');
```

---

## Шаг 5. Ключи

**Project Settings → API**:
- **Project URL** — вида `https://abcd.supabase.co`
- **anon public** ключ — он предназначен для клиента (как Mapbox `pk`), его можно класть в приложение.

> `service_role` ключ — НИКОГДА в приложение и не в чат. Только для серверных задач.

Пришли мне **Project URL** и **anon** ключ (anon безопасен) — и я подключу приложение.

---

## Шаг 6. Подключение к приложению

Два варианта, рекомендую первый:

### Вариант A — библиотека supabase-kt (идиоматично для KMP)
Добавляем модули Postgrest (данные) + Auth (логин) в shared-код, и переписываем репозитории `event`/`auth`/`profile` с демо-фолбэков на реальные запросы к Supabase. Логин — через Supabase Auth (email+пароль), события — `from("events").select()`. Это самый «родной» путь для мобильщика.

### Вариант B — через наш Ktor-клиент
У Supabase есть авто-REST (PostgREST): `GET {url}/rest/v1/events?select=*` с заголовками `apikey` и `Authorization`. Можно навести наш существующий `CoreConfig.baseUrl` на него и поправить пути/заголовки. Меньше новых зависимостей, но URL-схема PostGREST отличается от нашего `API_CONTRACT`, придётся адаптировать датасорсы.

**План (вариант A):**
1. Добавить supabase-kt (BOM + postgrest + auth + ktor-engine) в `gradle/libs.versions.toml` и `composeApp`/нужные модули.
2. Создать `SupabaseClient` (URL + anon key) и отдать его в Koin (`PlatformSdk`).
3. Переписать `AuthRepositoryImpl` → Supabase Auth (signUp/signIn), убрать dev-байпас.
4. Переписать `EventRepositoryImpl.getEvent/...` и `MapViewModel`-источник → запросы к таблице `events` (с маппингом колонок в наши модели).
5. Аналогично `OrganizerRepository`, `ProfileRepository`.

Начнём с auth + событий (карта оживёт на реальных данных), остальное подключим по очереди.

---

## Стоимость

Free tier Supabase: 500 МБ БД, 50 000 активных пользователей в месяц, соцпровайдеры — бесплатно. Для старта и MVP — с запасом.
