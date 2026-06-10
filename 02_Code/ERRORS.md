# Ошибки — как делаю

Три места, не путать.

## 1. Запрос — KtorResponse
Репозиторий всегда возвращает `KtorResponse<T>`, не кидает исключения.
Внутри оборачиваю в `safeRequest { ... }` → получаю `Success(data)` или `Failure(error)`.

## 2. Глобальное — GlobalErrorBus
Сюда падает то, что не про конкретный экран: 401, протух токен, серверная 5xx.
Шлёт ktor-плагин сам. Ловлю один раз в `App()`:
- `Unauthorized` → кидаю на авторизацию
- `ServerError` → показываю глобальный снек

## 3. Экранное — emitError + снек экрана
Обычные ошибки экрана: неверный пароль, не найдено и т.п.
ViewModel наследует `BaseScreenModel`. На `Failure` зову `emitError(error)`.
Экран слушает `errorMessage` и кидает в свой `SnackbarHostState`.

## Как раскидываю Failure во ViewModel
- 401 / сессия → ничего не делаю, это уже ушло в Bus
- бизнес-ошибка (есть текст) → `emitError(error)` → снек экрана
- нет сети / 5xx → либо Bus, либо снек экрана, но не оба сразу

Правило: глобальное чиню в `App`, экранное — в экране. Дважды одно не ловлю.

## Новая фича — чеклист
1. репозиторий → `KtorResponse<T>` через `safeRequest`
2. ViewModel : `BaseScreenModel`
3. на `Failure` → `emitError(error)`
4. экран слушает `errorMessage` → свой снек
5. 401 не трогаю — ловит `App`

Живой пример — модуль `common/feature/example`.
