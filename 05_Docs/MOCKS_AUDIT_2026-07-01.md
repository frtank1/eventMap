# Аудит моков и заглушек · 01.07.2026 (после полного паритета с дизайном)

Классификация: что реально фейковое, что честный офлайн-фолбэк, что мёртвый код.

## 1. Честные офлайн-фолбэки — НЕ баги (API-first, sample только при ошибке сети)

Паттерн `(repository.x() as? Success)?.data ?: sample()` — экран живой, при недоступном бэке показывает демо:

- Карта: `sampleEvents()` (только demoMode=true, сейчас false → не используется).
- Квесты: `sampleQuests()` (каталог), Game Hub: `sampleGameState()`.
- Лента/Уведомления/Подписки/Профиль: sample() в FeedViewModel, NotificationsViewModel, SubscriptionsViewModel, ProfileViewModel.
- EventDetailViewModel: sampleEvent/Participants/Media/Ratings.
- Рилс: stubComments в шторке комментариев (fallback при офлайне).
- Пульс/туман/друзья на карте: demo-фолбэки только при demoMode=true.

## 2. Реальные моки на живых экранах — кандидаты на доработку

- **LiveCirclesRow** (LIVE-шторка C5): «кружки участников» — стаб-эмодзи. Данных на бэке нет (нужны видео-кружки участников события).
- **EventReelScreen**: хардкод «12 друзей уже там» в expanded-шторке; счётчик комментариев «340» на кнопке (сама лента комментариев реальная); LiveCircles аналогично.
- **Game Hub**: сезонный пасс — клиентский мок (missions/leaderboard/XP — реальные).
- **RecordVideoScreen**: на desktop/wasm — демо-режим публикации (на мобильных всё реальное).
- **Бизнес-дашборд** (BusinessDashboardScreens): цифры-моки; заявка/пин — реальные ручки есть.
- **Чат** (ChatScreens): полностью мок — бэка чатов нет вообще.
- **EventCreationExtrasScreens, StoryViewerScreen**: мок-контент.
- Бэк: оплата `provider=mock` (Kaspi — к релизу), пуши no-op без FCM env, `/auth/password/forgot|reset` — no-op, `/subscriptions?tab=genres|people` — пусто, subscribers людей = 0.

## 3. Мёртвый код — экраны без единого входа из навигации (можно удалять или подключать)

Grep по `push(...)` не находит ни одного вызова:
FriendsScreen, LeaderboardScreen (при готовом `/districts/leaderboard`!), TimeCapsuleScreen (при готовых `/capsules`!), WrappedScreen, StoryViewerScreen, MapRouteScreen, legacy MapScreen (двойник MapContent), stub `feed/screens/NotificationsScreen` (двойник NotificationsContent), PaymentScreen P1/TicketScreens (покупка идёт из рилса напрямую, экран оплаты не подключён), FollowersFollowingScreen, UserActivityScreen, CreateInviteScreen/InviteCardScreen.

Примечательное: Leaderboard и TimeCapsule имеют ГОТОВЫЙ бэк — подключить дешевле, чем удалять.

## 4. Сид демо-данных (dev, авто при пустой базе) — обновлён «на месяц вперёд»

- ~33 события на 30 дней по недельному расписанию (пн-квиз, вт-забег, ср-лекция, чт-стендап, пт-DJ, сб-маркет+детям, вс-йога) + 2 LIVE сегодня + 1 прошедшее.
- 4 сид-юзера с логином: `demo1@eventmap.kz` … `demo4@eventmap.kz` / пароль `demo1234` — попарно друзья, подписаны на всех организаторов (лента живая).
- Оценки с тегами P4 (рейтинг событий пересчитан), комментарии на LIVE-событиях, история «был».
- 5 организаторов, 3 активных квеста на месяц: цепочка geo/photo/purchase, гео-тур по кофейням, фото-арт-маршрут.
- Идемпотентно; прод (APP_ENV=prod) не трогает.
