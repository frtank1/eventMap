-- V11 — тег-чипсы в оценках (P4: «🔥 Атмосфера», «🎶 Музыка топ», …).
-- Храним csv кодов тегов; агрегация по тегам для рейтинга места — позже.

ALTER TABLE event_ratings ADD COLUMN IF NOT EXISTS tags TEXT NOT NULL DEFAULT '';
