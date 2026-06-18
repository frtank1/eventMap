package com.eventmap.server.data.repository

import com.eventmap.server.data.db.BusinessAccountsTable
import com.eventmap.server.data.db.BusinessDocumentsTable
import com.eventmap.server.data.db.PinAnimationsTable
import com.eventmap.server.domain.model.BusinessAccount
import com.eventmap.server.domain.model.BusinessDocument
import com.eventmap.server.domain.model.PinAnimation
import com.eventmap.server.domain.repository.BusinessRepository
import com.eventmap.server.domain.repository.PinAnimationRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

private suspend fun <T> btx(block: () -> T) = newSuspendedTransaction(Dispatchers.IO) { block() }

class BusinessRepositoryImpl : BusinessRepository {

    override suspend fun apply(
        userId: String,
        bin: String,
        legalName: String,
        brandName: String,
        category: String,
        phone: String?,
        social: String?,
        documents: List<Pair<String, String>>,
    ): BusinessAccount = btx {
        val bid = UUID.randomUUID().toString()
        BusinessAccountsTable.insert {
            it[id] = bid
            it[BusinessAccountsTable.userId] = userId
            it[BusinessAccountsTable.bin] = bin
            it[BusinessAccountsTable.legalName] = legalName
            it[BusinessAccountsTable.brandName] = brandName
            it[BusinessAccountsTable.category] = category
            it[BusinessAccountsTable.phone] = phone
            it[BusinessAccountsTable.social] = social
        }
        val docs = documents.map { (kind, url) ->
            val did = UUID.randomUUID().toString()
            BusinessDocumentsTable.insert {
                it[id] = did
                it[businessId] = bid
                it[BusinessDocumentsTable.kind] = kind
                it[fileUrl] = url
            }
            BusinessDocument(did, kind, url)
        }
        BusinessAccount(
            id = bid, userId = userId, bin = bin, legalName = legalName, brandName = brandName,
            category = category, phone = phone, social = social, documents = docs,
        )
    }

    override suspend fun byUser(userId: String): BusinessAccount? = btx {
        BusinessAccountsTable.selectAll()
            .where { BusinessAccountsTable.userId eq userId }
            .singleOrNull()?.let { it.toBusiness(loadDocs(it[BusinessAccountsTable.id])) }
    }

    override suspend fun byId(id: String): BusinessAccount? = btx {
        BusinessAccountsTable.selectAll()
            .where { BusinessAccountsTable.id eq id }
            .singleOrNull()?.let { it.toBusiness(loadDocs(id)) }
    }

    override suspend fun review(id: String, approved: Boolean, reason: String?): BusinessAccount? = btx {
        val updated = BusinessAccountsTable.update({ BusinessAccountsTable.id eq id }) {
            it[status] = if (approved) "approved" else "rejected"
            it[rejectReason] = reason?.takeIf { _ -> !approved }
        }
        if (updated == 0) return@btx null
        BusinessAccountsTable.selectAll()
            .where { BusinessAccountsTable.id eq id }
            .single().toBusiness(loadDocs(id))
    }

    override suspend fun updatePin(userId: String, logoUrl: String?, pinColor: String?, animationId: String?): BusinessAccount? = btx {
        val row = BusinessAccountsTable.selectAll()
            .where { BusinessAccountsTable.userId eq userId }
            .singleOrNull() ?: return@btx null
        val bid = row[BusinessAccountsTable.id]
        BusinessAccountsTable.update({ BusinessAccountsTable.id eq bid }) {
            if (logoUrl != null) it[BusinessAccountsTable.logoUrl] = logoUrl
            if (pinColor != null) it[BusinessAccountsTable.pinColor] = pinColor
            if (animationId != null) it[BusinessAccountsTable.animationId] = animationId
        }
        BusinessAccountsTable.selectAll()
            .where { BusinessAccountsTable.id eq bid }
            .single().toBusiness(loadDocs(bid))
    }

    private fun loadDocs(businessId: String): List<BusinessDocument> =
        BusinessDocumentsTable.selectAll()
            .where { BusinessDocumentsTable.businessId eq businessId }
            .map { BusinessDocument(it[BusinessDocumentsTable.id], it[BusinessDocumentsTable.kind], it[BusinessDocumentsTable.fileUrl]) }

    private fun ResultRow.toBusiness(docs: List<BusinessDocument>) = BusinessAccount(
        id = this[BusinessAccountsTable.id],
        userId = this[BusinessAccountsTable.userId],
        bin = this[BusinessAccountsTable.bin],
        legalName = this[BusinessAccountsTable.legalName],
        brandName = this[BusinessAccountsTable.brandName],
        category = this[BusinessAccountsTable.category],
        phone = this[BusinessAccountsTable.phone],
        social = this[BusinessAccountsTable.social],
        status = this[BusinessAccountsTable.status],
        rejectReason = this[BusinessAccountsTable.rejectReason],
        logoUrl = this[BusinessAccountsTable.logoUrl],
        pinColor = this[BusinessAccountsTable.pinColor],
        animationId = this[BusinessAccountsTable.animationId],
        documents = docs,
    )
}

class PinAnimationRepositoryImpl : PinAnimationRepository {

    override suspend fun byCategory(category: String): List<PinAnimation> = btx {
        PinAnimationsTable.selectAll()
            .where { (PinAnimationsTable.category eq category) and (PinAnimationsTable.isActive eq true) }
            .map(::toAnim)
    }

    override suspend fun byId(id: String): PinAnimation? = btx {
        PinAnimationsTable.selectAll()
            .where { PinAnimationsTable.id eq id }
            .map(::toAnim)
            .singleOrNull()
    }

    override suspend fun add(category: String, name: String, lottieKey: String): PinAnimation = btx {
        val aid = UUID.randomUUID().toString()
        PinAnimationsTable.insert {
            it[id] = aid
            it[PinAnimationsTable.category] = category
            it[PinAnimationsTable.name] = name
            it[PinAnimationsTable.lottieKey] = lottieKey
        }
        PinAnimation(aid, category, name, lottieKey)
    }

    private fun toAnim(r: ResultRow) = PinAnimation(
        r[PinAnimationsTable.id], r[PinAnimationsTable.category],
        r[PinAnimationsTable.name], r[PinAnimationsTable.lottieKey],
    )
}
