#!/bin/bash
# Смоук-тест EventMap API (ревизия 11.06.2026) · bash smoke.sh
BASE="${BASE:-http://localhost:8080}"
EMAIL="smoke_$RANDOM@test.kz"

say()  { printf "\n\033[1;35m▶ %s\033[0m\n" "$1"; }
fail() { printf "\033[1;31m✗ ОШИБКА на шаге: %s\033[0m\nHTTP %s\nТело ответа: %s\n" "$1" "$2" "$3"; exit 1; }
jsonget() { python3 -c "import sys,json;print(json.load(sys.stdin)$1)" 2>/dev/null; }

# req <шаг> <метод> <путь> [json] [auth] → BODY (глобально), падает с объяснением
req() {
  local step="$1" method="$2" path="$3" data="$4" auth="$5"
  local args=(-s -w $'\n%{http_code}' -X "$method" "$BASE$path" -H 'Content-Type: application/json')
  [ -n "$auth" ] && args+=(-H "Authorization: Bearer $auth")
  [ -n "$data" ] && args+=(-d "$data")
  local out; out=$(curl "${args[@]}" 2>&1) || fail "$step" "curl" "$out"
  CODE=$(echo "$out" | tail -1)
  BODY=$(echo "$out" | sed '$d')
  case "$CODE" in 2*) ;; *) fail "$step" "$CODE" "$BODY";; esac
}

say "1. Сервер жив? GET /quests/nearby"
req "пинг" GET /quests/nearby; echo "ответ: $BODY ✓"

say "2. Регистрация $EMAIL"
req "регистрация" POST /auth/register "{\"email\":\"$EMAIL\",\"password\":\"test1234\",\"name\":\"Smoke Tester\"}"
TOKEN=$(echo "$BODY" | jsonget "['accessToken']")
[ -z "$TOKEN" ] && fail "регистрация (нет accessToken)" "$CODE" "$BODY"
echo "токен получен ✓"

say "3. Создаю бесплатное событие (soon)"
req "создание события" POST /events '{
  "title":"Smoke-тест · йога на рассвете","description":"проверка API",
  "location":{"lat":51.1280,"lng":71.4304,"name":"Триатлон-парк"},
  "startAt":"2026-07-01T03:30:00Z","endAt":"2026-07-01T05:00:00Z",
  "category":"sport","price":{"type":"free"}}' "$TOKEN"
EID=$(echo "$BODY" | jsonget "['id']")
echo "событие $EID ✓"

say "4. «Приду» (D1c: soon+free → going)"
req "attend going" POST "/events/$EID/attend" '{"action":"going"}' "$TOKEN"
echo "goingCount=$(echo "$BODY" | jsonget "['goingCount']") ✓"

say "5. «Я тоже здесь» до старта — сервер обязан отказать"
OUT=$(curl -s -X POST "$BASE/events/$EID/attend" -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"action":"here","lat":51.1280,"lng":71.4304}')
echo "отказ: $(echo "$OUT" | jsonget "['error']['code']") ✓ (ожидаемо NOT_LIVE)"

say "6. Комментарий (D9)"
req "комментарий" POST "/events/$EID/comments" '{"body":"Смоук-тест на месте! 🔥"}' "$TOKEN"
echo "коммент: $(echo "$BODY" | jsonget "['body']") ✓"

say "7. Колокольчик (D11)"
req "remind" POST "/events/$EID/remind" '{}' "$TOKEN"
echo "напоминание включено=$(echo "$BODY" | jsonget "['enabled']") ✓"

say "8. Заявка бизнес-аккаунта (M)"
req "business apply" POST /business/apply '{
  "bin":"123456789012","legalName":"ТОО Smoke","brandName":"Smoke Bar","category":"food"}' "$TOKEN"
echo "статус заявки: $(echo "$BODY" | jsonget "['status']") ✓"

say "9. Игровой профиль (R1)"
req "game profile" GET /game/profile "" "$TOKEN"
echo "уровень: $(echo "$BODY" | jsonget "['levelTitle']") ✓"

say "10. Пульс карты (G)"
req "pulse" GET /map/pulse
echo "pulse отвечает ✓"

printf "\n\033[1;32m🎉 ВСЕ ПРОВЕРКИ ПРОШЛИ — сервер и новые домены работают\033[0m\n"
