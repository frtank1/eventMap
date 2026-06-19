package com.eventmap.server.domain.usecase

import com.eventmap.server.domain.model.Video
import com.eventmap.server.domain.repository.EventRepository
import com.eventmap.server.domain.repository.VideoRepository
import java.util.UUID

// ===== Видео-рилсы (E1/E2 «Сейчас») =====
//
// Загрузка файла идёт через /media/presign (kind=video) → PUT в storage → POST /videos
// с готовым mediaUrl. Лента «Сейчас» — свежие рилсы; react — тоггл ♡.

class GetVideoFeedUseCase(private val videos: VideoRepository) {
    suspend operator fun invoke(viewerId: String?): List<Video> = videos.feedNow(viewerId)
}

class ReactVideoUseCase(private val videos: VideoRepository) {
    /** @return новый счётчик лайков; null — видео не найдено. */
    suspend operator fun invoke(videoId: String, userId: String): Int? = videos.toggleReaction(videoId, userId)
}

class CreateVideoUseCase(
    private val videos: VideoRepository,
    private val events: EventRepository,
) {
    sealed interface Result {
        data class Ok(val video: Video) : Result
        data object EventNotFound : Result
        data class Error(val message: String) : Result
    }

    suspend operator fun invoke(
        authorId: String,
        eventId: String?,
        mediaUrl: String,
        thumb: String?,
        caption: String,
        category: String,
    ): Result {
        if (mediaUrl.isBlank()) return Result.Error("mediaUrl обязателен")
        // Если рилс привязан к событию — проверяем, что оно существует, и берём категорию оттуда.
        var cat = category
        if (!eventId.isNullOrBlank()) {
            val event = events.byId(eventId) ?: return Result.EventNotFound
            if (cat.isBlank()) cat = event.category
        }
        val id = UUID.randomUUID().toString()
        val video = videos.create(id, authorId, eventId?.takeIf { it.isNotBlank() }, mediaUrl, thumb, caption.trim(), cat)
        return Result.Ok(video)
    }
}
