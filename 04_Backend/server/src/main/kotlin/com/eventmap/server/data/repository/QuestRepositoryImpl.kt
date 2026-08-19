package com.eventmap.server.data.repository

import com.eventmap.server.data.db.BusinessAccountsTable
import com.eventmap.server.data.db.QuestCheckinsTable
import com.eventmap.server.data.db.QuestPointsTable
import com.eventmap.server.data.db.QuestRunsTable
import com.eventmap.server.data.db.QuestsTable
import com.eventmap.server.data.db.UsersTable
import com.eventmap.server.data.db.XpEventsTable
import com.eventmap.server.domain.model.Bbox
import com.eventmap.server.domain.model.Quest
import com.eventmap.server.domain.model.QuestPoint
import com.eventmap.server.domain.model.QuestRun
import com.eventmap.server.domain.repository.QuestRepository
import com.eventmap.server.domain.repository.XpRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

private suspend fun <T> qtx(block: () -> T) = newSuspendedTransaction(Dispatchers.IO) { block() }

class QuestRepositoryImpl : QuestRepository {

    override suspend fun create(quest: Quest): Quest = qtx {
        val qid = UUID.randomUUID().toString()
        QuestsTable.insert {
            it[id] = qid
            it[businessId] = quest.businessId
            it[title] = quest.title
            it[emoji] = quest.emoji
            it[orderedChain] = quest.orderedChain
            it[startsAt] = quest.startsAt
            it[endsAt] = quest.endsAt
            it[prizeTitle] = quest.prizeTitle
            it[prizeCount] = quest.prizeCount
            it[participantLimit] = quest.participantLimit
        }
        quest.points.forEachIndexed { i, p ->
            QuestPointsTable.insert {
                it[id] = UUID.randomUUID().toString()
                it[questId] = qid
                it[ord] = i + 1
                it[lat] = p.lat
                it[lng] = p.lng
                it[placeName] = p.placeName
                it[condition] = p.condition
            }
        }
        byIdInternal(qid, null)!!
    }

    override suspend fun nearby(bbox: Bbox?, viewerId: String?): List<Quest> = qtx {
        // активные квесты с точкой в bbox (без bbox — все активные)
        val questIds = if (bbox == null) {
            QuestsTable.selectAll().where { QuestsTable.status eq "active" }.map { it[QuestsTable.id] }
        } else {
            val inBox = QuestPointsTable.selectAll()
                .where {
                    (QuestPointsTable.lat greaterEq bbox.swLat) and (QuestPointsTable.lat lessEq bbox.neLat) and
                        (QuestPointsTable.lng greaterEq bbox.swLng) and (QuestPointsTable.lng lessEq bbox.neLng)
                }
                .map { it[QuestPointsTable.questId] }.toSet()
            if (inBox.isEmpty()) emptyList()
            else QuestsTable.selectAll()
                .where { (QuestsTable.status eq "active") and (QuestsTable.id inList inBox) }
                .map { it[QuestsTable.id] }
        }
        questIds.mapNotNull { byIdInternal(it, viewerId) }
    }

    override suspend fun byId(id: String, viewerId: String?): Quest? = qtx { byIdInternal(id, viewerId) }

    override suspend fun join(questId: String, userId: String): QuestRun = qtx {
        val existing = QuestRunsTable.selectAll()
            .where { (QuestRunsTable.questId eq questId) and (QuestRunsTable.userId eq userId) }
            .singleOrNull()
        if (existing != null) return@qtx toRun(existing)
        val rid = UUID.randomUUID().toString()
        QuestRunsTable.insert {
            it[id] = rid
            it[QuestRunsTable.questId] = questId
            it[QuestRunsTable.userId] = userId
            it[startedAtMs] = System.currentTimeMillis()
        }
        QuestRun(rid, questId, userId)
    }

    override suspend fun checkin(runId: String, pointId: String, proofType: String, mediaUrl: String?): QuestRun = qtx {
        QuestCheckinsTable.insert {
            it[QuestCheckinsTable.runId] = runId
            it[QuestCheckinsTable.pointId] = pointId
            it[QuestCheckinsTable.proofType] = proofType
            it[QuestCheckinsTable.mediaUrl] = mediaUrl
            it[createdAtMs] = System.currentTimeMillis()
        }
        val run = QuestRunsTable.selectAll().where { QuestRunsTable.id eq runId }.single()
        val questId = run[QuestRunsTable.questId]
        val totalPoints = QuestPointsTable.selectAll().where { QuestPointsTable.questId eq questId }.count()
        val checked = QuestCheckinsTable.selectAll().where { QuestCheckinsTable.runId eq runId }
            .map { it[QuestCheckinsTable.pointId] }.toSet()
        if (checked.size.toLong() >= totalPoints && run[QuestRunsTable.finishedAtMs] == null) {
            val prize = UUID.randomUUID().toString().replace("-", "")
            QuestRunsTable.update({ QuestRunsTable.id eq runId }) {
                it[finishedAtMs] = System.currentTimeMillis()
                it[prizeQr] = prize
            }
            return@qtx toRun(QuestRunsTable.selectAll().where { QuestRunsTable.id eq runId }.single()).copy(checkedPointIds = checked)
        }
        toRun(run).copy(checkedPointIds = checked)
    }

    override suspend fun review(questId: String, approved: Boolean): Quest? = qtx {
        val updated = QuestsTable.update({ QuestsTable.id eq questId }) {
            it[status] = if (approved) "active" else "rejected"
        }
        if (updated == 0) null else byIdInternal(questId, null)
    }

    override suspend fun listByStatus(status: String, limit: Int): List<Quest> = qtx {
        QuestsTable.selectAll()
            .where { QuestsTable.status eq status }
            .limit(limit)
            .map { it[QuestsTable.id] }
            .mapNotNull { byIdInternal(it, null) }
    }

    // внутри открытой транзакции
    private fun byIdInternal(id: String, viewerId: String?): Quest? {
        val q = QuestsTable.selectAll().where { QuestsTable.id eq id }.singleOrNull() ?: return null
        val brand = BusinessAccountsTable.selectAll()
            .where { BusinessAccountsTable.id eq q[QuestsTable.businessId] }
            .singleOrNull()?.get(BusinessAccountsTable.brandName) ?: ""
        val run = viewerId?.let { uid ->
            QuestRunsTable.selectAll()
                .where { (QuestRunsTable.questId eq id) and (QuestRunsTable.userId eq uid) }
                .singleOrNull()?.let { r ->
                    val checked = QuestCheckinsTable.selectAll()
                        .where { QuestCheckinsTable.runId eq r[QuestRunsTable.id] }
                        .map { it[QuestCheckinsTable.pointId] }.toSet()
                    toRun(r).copy(checkedPointIds = checked)
                }
        }
        val points = QuestPointsTable.selectAll()
            .where { QuestPointsTable.questId eq id }
            .orderBy(QuestPointsTable.ord)
            .map { p ->
                QuestPoint(
                    id = p[QuestPointsTable.id], ord = p[QuestPointsTable.ord],
                    lat = p[QuestPointsTable.lat], lng = p[QuestPointsTable.lng],
                    placeName = p[QuestPointsTable.placeName], condition = p[QuestPointsTable.condition],
                    checkedByMe = run?.checkedPointIds?.contains(p[QuestPointsTable.id]) == true,
                )
            }
        val finishers = QuestRunsTable.selectAll()
            .where { (QuestRunsTable.questId eq id) and QuestRunsTable.finishedAtMs.isNotNull() }
            .count()
        return Quest(
            id = q[QuestsTable.id], businessId = q[QuestsTable.businessId], businessBrand = brand,
            title = q[QuestsTable.title], emoji = q[QuestsTable.emoji],
            orderedChain = q[QuestsTable.orderedChain],
            startsAt = q[QuestsTable.startsAt], endsAt = q[QuestsTable.endsAt],
            prizeTitle = q[QuestsTable.prizeTitle], prizeCount = q[QuestsTable.prizeCount],
            participantLimit = q[QuestsTable.participantLimit], status = q[QuestsTable.status],
            points = points, finishersCount = finishers, myRun = run,
        )
    }

    private fun toRun(r: ResultRow) = QuestRun(
        id = r[QuestRunsTable.id],
        questId = r[QuestRunsTable.questId],
        userId = r[QuestRunsTable.userId],
        finished = r[QuestRunsTable.finishedAtMs] != null,
        prizeQr = r[QuestRunsTable.prizeQr],
    )
}

class XpRepositoryImpl : XpRepository {

    override suspend fun add(userId: String, source: String, sourceId: String?, amount: Int): Unit = qtx {
        XpEventsTable.insert {
            it[id] = UUID.randomUUID().toString()
            it[XpEventsTable.userId] = userId
            it[XpEventsTable.sourceType] = source
            it[XpEventsTable.sourceId] = sourceId
            it[XpEventsTable.amount] = amount
        }
    }

    override suspend fun totalFor(userId: String): Long = qtx {
        val sumCol = XpEventsTable.amount.sum()
        XpEventsTable.select(sumCol)
            .where { XpEventsTable.userId eq userId }
            .single()[sumCol]?.toLong() ?: 0L
    }

    override suspend fun topUsers(limit: Int): List<Triple<String, String, Long>> = qtx {
        val sumCol = XpEventsTable.amount.sum()
        XpEventsTable
            .join(UsersTable, JoinType.INNER, onColumn = XpEventsTable.userId, otherColumn = UsersTable.id)
            .select(XpEventsTable.userId, UsersTable.name, sumCol)
            .groupBy(XpEventsTable.userId, UsersTable.name)
            .orderBy(sumCol, SortOrder.DESC)
            .limit(limit)
            .map { Triple(it[XpEventsTable.userId], it[UsersTable.name], it[sumCol]?.toLong() ?: 0L) }
    }

    override suspend fun totalsFor(userIds: List<String>): Map<String, Long> = qtx {
        if (userIds.isEmpty()) return@qtx emptyMap()
        val sumCol = XpEventsTable.amount.sum()
        XpEventsTable.select(XpEventsTable.userId, sumCol)
            .where { XpEventsTable.userId inList userIds }
            .groupBy(XpEventsTable.userId)
            .associate { it[XpEventsTable.userId] to (it[sumCol]?.toLong() ?: 0L) }
    }
}
