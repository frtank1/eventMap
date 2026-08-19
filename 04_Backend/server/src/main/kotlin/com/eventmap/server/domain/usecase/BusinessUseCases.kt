package com.eventmap.server.domain.usecase

import com.eventmap.server.domain.model.BusinessAccount
import com.eventmap.server.domain.model.PinAnimation
import com.eventmap.server.domain.repository.BusinessRepository
import com.eventmap.server.domain.repository.PinAnimationRepository

// ===== Бизнес-аккаунты (M1–M6, API §8.1) =====
//
// Правила:
//  - один бизнес-аккаунт на пользователя; БИН/ИИН — 12 цифр;
//  - заявка уходит в pending → модерация админом (M6);
//  - наборы анимаций пина грузит админ ПО КАТЕГОРИЯМ; бизнес видит и может
//    выбрать ТОЛЬКО анимации своей категории (M4) — валидируется на сервере.

class ApplyBusinessUseCase(private val business: BusinessRepository) {
    sealed interface Result {
        data class Ok(val account: BusinessAccount) : Result
        data class Rejected(val code: String, val message: String) : Result
    }

    suspend operator fun invoke(
        userId: String,
        bin: String,
        legalName: String,
        brandName: String,
        category: String,
        phone: String?,
        social: String?,
        documents: List<Pair<String, String>>,
    ): Result {
        if (!bin.matches(Regex("\\d{12}"))) {
            return Result.Rejected("BAD_BIN", "БИН/ИИН — 12 цифр")
        }
        if (brandName.isBlank() || legalName.isBlank()) {
            return Result.Rejected("BAD_REQUEST", "Название бренда и юр. название обязательны")
        }
        if (category.isBlank()) {
            return Result.Rejected("BAD_REQUEST", "Категория обязательна — от неё зависит набор анимаций пина")
        }
        when (business.byUser(userId)?.status) {
            "approved" -> return Result.Rejected("ALREADY_APPROVED", "Бизнес уже подтверждён")
            "pending" -> return Result.Rejected("ALREADY_EXISTS", "Заявка уже на модерации")
            // rejected / нет заявки → apply перезапишет данные, статус снова pending
        }
        // TODO: автопроверка БИН в реестре (egov API) — Спринт интеграций
        return Result.Ok(business.apply(userId, bin, legalName.trim(), brandName.trim(), category, phone, social, documents))
    }
}

class GetMyBusinessUseCase(private val business: BusinessRepository) {
    suspend operator fun invoke(userId: String): BusinessAccount? = business.byUser(userId)
}

class GetPinAnimationsUseCase(
    private val business: BusinessRepository,
    private val animations: PinAnimationRepository,
) {
    sealed interface Result {
        data class Ok(val category: String, val animations: List<PinAnimation>) : Result
        data object NoBusiness : Result
    }

    /** Набор ТОЛЬКО категории бизнеса вызывающего (M4) */
    suspend operator fun invoke(userId: String): Result {
        val biz = business.byUser(userId) ?: return Result.NoBusiness
        return Result.Ok(biz.category, animations.byCategory(biz.category))
    }
}

class UpdateBusinessPinUseCase(
    private val business: BusinessRepository,
    private val animations: PinAnimationRepository,
) {
    sealed interface Result {
        data class Ok(val account: BusinessAccount) : Result
        data object NoBusiness : Result
        data class Rejected(val code: String, val message: String) : Result
    }

    suspend operator fun invoke(userId: String, logoUrl: String?, pinColor: String?, animationId: String?): Result {
        val biz = business.byUser(userId) ?: return Result.NoBusiness
        if (pinColor != null && !pinColor.matches(Regex("#[0-9A-Fa-f]{6}"))) {
            return Result.Rejected("BAD_COLOR", "Цвет — hex вида #RRGGBB")
        }
        if (animationId != null) {
            val anim = animations.byId(animationId)
                ?: return Result.Rejected("ANIMATION_NOT_FOUND", "Анимация не найдена")
            if (anim.category != biz.category) {
                return Result.Rejected("WRONG_CATEGORY", "Анимация из набора «${anim.category}» — твоя категория «${biz.category}»")
            }
        }
        val updated = business.updatePin(userId, logoUrl, pinColor, animationId) ?: return Result.NoBusiness
        return Result.Ok(updated)
    }
}

class ReviewBusinessUseCase(private val business: BusinessRepository) {
    /** Админ (X-Admin-Key). @return null — заявка не найдена */
    suspend operator fun invoke(businessId: String, approved: Boolean, reason: String?): BusinessAccount? =
        business.review(businessId, approved, reason)
}

class AddPinAnimationUseCase(private val animations: PinAnimationRepository) {
    /** Админ: загрузить анимацию в набор категории */
    suspend operator fun invoke(category: String, name: String, lottieKey: String): PinAnimation =
        animations.add(category, name, lottieKey)
}
