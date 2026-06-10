# ADR-002 — Важность ивента на карте, платные ивенты, оценки

Статус: **принято** · 31.05.2026. Три новые фичи + их влияние на API, БД, экраны и карту.

---

## Фича 1. Важность ивента на карте (вес пина)

Пин на карте визуально тем «крупнее/ярче», чем важнее событие. Важность = идущие + LIVE-зрители + оценка организатора (выбор PM/заказчика).

### Формула importance (0..100)

```
importance =
    35 * norm(log1p(going_count))         // сколько сказали «я пойду»
  + 25 * liveBoost                        // LIVE сейчас (0 или 1 * вес зрителей)
  + 20 * (organizer_avg_rating / 5)       // средняя оценка прошлых ивентов организатора
  + 10 * recencyBoost                     // чем ближе старт, тем выше (окно ±3 ч)
  + 10 * paidVerifiedBoost                // платный с подтверждёнными покупками — «серьёзный» ивент

где:
  norm(x)        = min(1, x / log1p(GOING_CAP))   GOING_CAP=500
  liveBoost      = status=='live' ? min(1, 0.4 + log1p(live_viewers)/log1p(1000)) : 0
  recencyBoost   = clamp(1 - |minutesToStart|/180, 0, 1)
  paidVerifiedBoost = paid && paid_count>0 ? min(1, paid_count/50) : 0
```

### Маппинг в визуал пина (DEV_SPEC `MapPin`)
- радиус dot: `36 + importance/100 * 22` dp (36→58)
- свечение/тень: интенсивность ∝ importance
- порог `importance >= 70` → пульсация + всегда показываем label (даже на отдалении)
- LIVE всегда поверх soon при наложении (z-index по importance)

Важность считается на бэкенде в гео-запросе и кладётся в поле `importance` ответа `/events`. Клиент только рисует — без логики ранжирования на устройстве.

---

## Фича 2. Платные ивенты с подтверждением оплаты

### Решение по эквайрингу
**Локальный эквайринг (CloudPayments или Halyk ePay)** — карты РК + Kaspi, выплаты в KZT.
- Stripe отклонён: слабый приём карт РК/Kaspi.
- IAP отклонён: билеты на офлайн-события — физический товар, сторы запрещают IAP для них.
- Итоговый выбор провайдера (CloudPayments vs ePay) — по тарифам/договору, оба интегрируются одинаковым паттерном (redirect/SDK + вебхук). Финал — к Спринту 5.

### Флоу оплаты
```
D1 платный ивент → кнопка «Купить за N ₸»
   → P1 экран оплаты (сумма, карта/Kaspi, 3DS)
   → провайдер (redirect/SDK)
   → вебхук провайдера → бэкенд помечает ticket=paid
   → attendance.type='going' появляется ТОЛЬКО после оплаты
   → P2 успех / P3 ошибка оплаты
```
Для бесплатных ивентов «я пойду» работает как раньше (мгновенно). Для платных — `going` засчитывается лишь по факту оплаченного билета. Это же даёт `paidVerifiedBoost` важности.

### Возвраты/лимиты
- `capacity` проверяется при создании платежа (резерв на 10 мин, иначе отмена).
- Возврат до старта — по политике организатора (поле `refund_policy`).

---

## Фича 3. Оценки ивентов

Пользователь, отмеченный как `was` (был на ивенте), может поставить оценку **1–5** после `end_at`.
- Одна оценка на пользователя на ивент, можно изменить в течение 7 дней.
- `event.rating_avg`, `event.rating_count` — агрегаты.
- `organizer_avg_rating` = среднее по всем завершённым ивентам организатора → входит в формулу важности (Фича 1) и видно в профиле организатора (H8).
- Экран оценки **P4** (после ивента, из пуша/истории H4) + блок рейтинга в карточке D1.
- Анти-накрутка: оценку может ставить только подтверждённый `was` (для платных — был оплаченный билет; для бесплатных — отметка присутствия/гео-чек-ин).

---

## Дельта API (`API_CONTRACT.md`)

| Метод | Путь | Описание |
|---|---|---|
| POST | `/events/{id}/purchase` | создать платёж за билет → `{paymentUrl}` или SDK-параметры |
| POST | `/payments/webhook/{provider}` | вебхук эквайринга → пометить билет paid |
| GET | `/events/{id}/ticket` | мой билет/статус оплаты |
| POST | `/events/{id}/rating` | поставить/изменить оценку 1–5 (только `was`) |
| GET | `/events/{id}/ratings` | агрегаты + отзывы |

`GET /events` — в ответ добавляются: `importance` (0..100), `ratingAvg`, `ratingCount`, `paid`, `priceAmount`, `myTicketStatus`.

---

## Дельта схемы БД

```sql
ALTER TABLE events
  ADD COLUMN importance   REAL DEFAULT 0,     -- пересчёт фоновой джобой/при изменениях
  ADD COLUMN rating_avg   REAL DEFAULT 0,
  ADD COLUMN rating_count INT  DEFAULT 0,
  ADD COLUMN refund_policy TEXT DEFAULT 'none';
CREATE INDEX events_importance_idx ON events (importance DESC);

CREATE TABLE tickets (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  event_id   UUID REFERENCES events(id) ON DELETE CASCADE,
  user_id    UUID REFERENCES users(id) ON DELETE CASCADE,
  amount     INT, currency TEXT,
  status     TEXT DEFAULT 'pending',   -- pending|paid|failed|refunded
  provider   TEXT,                     -- cloudpayments|epay
  provider_txn TEXT,
  reserved_until TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT now(),
  UNIQUE (event_id, user_id)
);
CREATE INDEX tickets_event_idx ON tickets (event_id, status);

CREATE TABLE ratings (
  event_id   UUID REFERENCES events(id) ON DELETE CASCADE,
  user_id    UUID REFERENCES users(id) ON DELETE CASCADE,
  score      SMALLINT CHECK (score BETWEEN 1 AND 5),
  comment    TEXT,
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now(),
  PRIMARY KEY (event_id, user_id)
);
```
`attendance.type='going'` для платных ивентов проставляется триггером/сервисом при `tickets.status='paid'`.

---

## Новые экраны (+4, итого 71)

| Код | Экран | Раздел |
|---|---|---|
| P1 | Оплата билета (сумма, карта/Kaspi, 3DS) | новый раздел P — Платежи |
| P2 | Успешная оплата (билет получен) | P |
| P3 | Ошибка оплаты / повтор | P |
| P4 | Оценить ивент (1–5 + отзыв) | P (или D9) |

Плюс правки существующих: D1 (бейдж цены + кнопка «Купить», блок рейтинга ★4.6·128), C-пины (масштаб по importance), H8 (рейтинг организатора).

---

## Влияние на ROADMAP
- **Спринт 2**: importance в гео-запросе + масштаб пинов (визуально дёшево, ценно рано).
- **Спринт 5**: платежи (P1–P3, эквайринг, вебхуки) + оценки (P4, агрегаты). Финальный выбор провайдера — здесь.
- Стек +: **CloudPayments/ePay SDK** (клиент) и серверная интеграция вебхуков.

---

*ADR. Связан с `API_CONTRACT.md`, `SCREENS.md`, `03_UI_UX/DEV_SPEC.md`, `ROADMAP.md`. 31.05.2026.*
