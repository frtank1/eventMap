package com.eventmap.server.data.repository

import com.eventmap.server.data.db.BadgesTable
import com.eventmap.server.data.db.DistrictVisitsTable
import com.eventmap.server.data.db.DistrictsTable
import com.eventmap.server.data.db.MissionsTable
import com.eventmap.server.data.db.TimeCapsulesTable
import com.eventmap.server.data.db.UserBadgesTable
import com.eventmap.server.domain.model.Badge
import com.eventmap.server.domain.model.Bbox
import com.eventmap.server.domain.model.Mission
import com.eventmap.server.domain.model.TimeCapsule
import com.eventmap.server.domain.repository.CapsuleRepository
import com.eventmap.server.domain.repository.GameRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

private suspend fun <T> gtx(block: () -> T) = newSuspendedTransaction(Dispatchers.IO) { block() }

class GameRepositoryImpl : GameRepository {

    override suspend fun badgesOf(userId: String): List<Badge> = gtx {
        UserBadgesTable.join(BadgesTable, JoinType.INNER, onColumn = UserBadgesTable.badgeId, otherColumn = BadgesTable.id)
            .selectAll()
            .where { UserBadgesTable.userId eq userId }
            .map { Badge(it[BadgesTable.code], it[BadgesTable.title], it[BadgesTable.emoji]) }
    }

    override suspend fun awardBadgeOnce(userId: String, code: String, title: String, emoji: String?, source: String?): Boolean = gtx {
        val badgeId = BadgesTable.selectAll().where { BadgesTable.code eq code }.singleOrNull()
            ?.get(BadgesTable.id)
            ?: run {
                val bid = UUID.randomUUID().toString()
                BadgesTable.insert {
                    it[id] = bid
                    it[BadgesTable.code] = code
                    it[BadgesTable.title] = title
                    it[BadgesTable.emoji] = emoji
                }
                bid
            }
        val has = UserBadgesTable.selectAll()
            .where { (UserBadgesTable.userId eq userId) and (UserBadgesTable.badgeId eq badgeId) }
            .any()
        if (has) return@gtx false
        UserBadgesTable.insert {
            it[UserBadgesTable.userId] = userId
            it[UserBadgesTable.badgeId] = badgeId
            it[UserBadgesTable.sourceType] = source
        }
        true
    }

    override suspend fun missionsOf(userId: String): List<Mission> = gtx {
        MissionsTable.selectAll()
            .where { (MissionsTable.userId eq userId) and (MissionsTable.status eq "active") }
            .map {
                Mission(
                    it[MissionsTable.id], it[MissionsTable.kind], it[MissionsTable.progress],
                    it[MissionsTable.target], it[MissionsTable.rewardXp], it[MissionsTable.multiplier],
                    it[MissionsTable.status],
                )
            }
    }

    override suspend fun districtsStats(userId: String): Pair<Int, Int> = gtx {
        val total = DistrictsTable.selectAll().count().toInt()
        val visited = DistrictVisitsTable.selectAll()
            .where { DistrictVisitsTable.userId eq userId }
            .count().toInt()
        visited to total
    }

    override suspend fun visitedDistrictIds(userId: String): List<String> = gtx {
        DistrictVisitsTable.selectAll()
            .where { DistrictVisitsTable.userId eq userId }
            .map { it[DistrictVisitsTable.districtId] }
    }

    override suspend fun leaderboard(): List<Triple<String, String, Long>> = gtx {
        val cnt = DistrictVisitsTable.userId.count()
        DistrictVisitsTable.join(DistrictsTable, JoinType.INNER, onColumn = DistrictVisitsTable.districtId, otherColumn = DistrictsTable.id)
            .select(DistrictsTable.id, DistrictsTable.name, cnt)
            .groupBy(DistrictsTable.id, DistrictsTable.name)
            .orderBy(cnt to org.jetbrains.exposed.sql.SortOrder.DESC)
            .limit(10)
            .map { Triple(it[DistrictsTable.id], it[DistrictsTable.name], it[cnt]) }
    }
}

class CapsuleRepositoryImpl : CapsuleRepository {

    override suspend fun create(c: TimeCapsule): TimeCapsule = gtx {
        val cid = UUID.randomUUID().toString()
        TimeCapsulesTable.insert {
            it[id] = cid
            it[ownerId] = c.ownerId
            it[lat] = c.lat
            it[lng] = c.lng
            it[mediaUrl] = c.mediaUrl
            it[bodyText] = c.bodyText
            it[recipientType] = c.recipientType
            it[recipientId] = c.recipientId
            it[opensAtMs] = c.opensAtMs
        }
        c.copy(id = cid)
    }

    override suspend fun byId(id: String): TimeCapsule? = gtx {
        TimeCapsulesTable.selectAll().where { TimeCapsulesTable.id eq id }.map(::toCapsule).singleOrNull()
    }

    override suspend fun nearbyFor(userId: String, bbox: Bbox?): List<TimeCapsule> = gtx {
        val q = TimeCapsulesTable.selectAll().where {
            (TimeCapsulesTable.recipientId eq userId) or
                (TimeCapsulesTable.ownerId eq userId) or
                (TimeCapsulesTable.recipientType eq "public")
        }
        bbox?.let { b ->
            q.andWhere {
                (TimeCapsulesTable.lat greaterEq b.swLat) and (TimeCapsulesTable.lat lessEq b.neLat) and
                    (TimeCapsulesTable.lng greaterEq b.swLng) and (TimeCapsulesTable.lng lessEq b.neLng)
            }
        }
        q.map(::toCapsule)
    }

    override suspend fun markOpened(id: String, by: String): Unit = gtx {
        TimeCapsulesTable.update({ TimeCapsulesTable.id eq id }) {
            it[openedAtMs] = System.currentTimeMillis()
            it[openedBy] = by
        }
    }

    private fun toCapsule(r: ResultRow) = TimeCapsule(
        id = r[TimeCapsulesTable.id],
        ownerId = r[TimeCapsulesTable.ownerId],
        lat = r[TimeCapsulesTable.lat],
        lng = r[TimeCapsulesTable.lng],
        mediaUrl = r[TimeCapsulesTable.mediaUrl],
        bodyText = r[TimeCapsulesTable.bodyText],
        recipientType = r[TimeCapsulesTable.recipientType],
        recipientId = r[TimeCapsulesTable.recipientId],
        opensAtMs = r[TimeCapsulesTable.opensAtMs],
        opened = r[TimeCapsulesTable.openedAtMs] != null,
    )
}
