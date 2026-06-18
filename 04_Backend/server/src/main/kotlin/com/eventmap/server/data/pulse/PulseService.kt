package com.eventmap.server.data.pulse

import com.eventmap.server.data.db.EventsTable
import com.eventmap.server.data.db.LocationPingsTable
import com.eventmap.server.data.db.PulseAggregatesTable
import com.eventmap.server.data.db.PulseAnomaliesTable
import com.eventmap.server.domain.model.Bbox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs
import kotlin.math.roundToInt

// ===== Пульс карты (G1/G2/G9, API §8.5) =====
//
// Приватность (MAP_CONCEPT/контракт §8.5):
//  - пинги пишутся ТОЛЬКО при opt-in (тумблер «аналитика», I2) — проверяет клиент при отправке;
//  - user_hash = HMAC(userId, секрет+дата) — соль меняется ежедневно, склейка дней невозможна;
//  - TTL пингов 15 минут — чистит джоба;
//  - наружу только агрегаты: glow/crowd на событие и аномалии с k >= K_ANOMALY.

private const val PING_TTL_MS = 15 * 60 * 1000L
private const val CROWD_WINDOW_MS = 10 * 60 * 1000L
private const val NEAR_EVENT_DEG = 0.003      // ~300 м
private const val CELL_DEG = 0.003            // ячейка кластеризации G9
private const val K_ANOMALY = 30              // k-анонимность аномалии
private const val ANOMALY_CLOSE_BELOW = 10

data class PulseEntry(val eventId: String, val glow: Float, val crowd10m: Int, val viewers: Int)
data class Anomaly(val id: String, val lat: Double, val lng: Double, val userCount: Int, val startedAtMs: Long)

class PulseService(private val secret: String) {

    private suspend fun <T> ptx(block: () -> T) = newSuspendedTransaction(Dispatchers.IO) { block() }

    private fun dailyHash(userId: String): String {
        val day = System.currentTimeMillis() / (24 * 60 * 60 * 1000)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(("$secret:$day").toByteArray(), "HmacSHA256"))
        return mac.doFinal(userId.toByteArray()).joinToString("") { "%02x".format(it) }.take(48)
    }

    suspend fun recordPing(userId: String, lat: Double, lng: Double): Unit = ptx {
        LocationPingsTable.insert {
            it[userHash] = dailyHash(userId)
            it[LocationPingsTable.lat] = lat
            it[LocationPingsTable.lng] = lng
            it[createdAtMs] = System.currentTimeMillis()
        }
    }

    suspend fun pulse(bbox: Bbox?): Pair<List<PulseEntry>, List<Anomaly>> = ptx {
        val entries = PulseAggregatesTable.selectAll().map {
            PulseEntry(it[PulseAggregatesTable.eventId], it[PulseAggregatesTable.glow], it[PulseAggregatesTable.crowd10m], it[PulseAggregatesTable.viewers])
        }
        val anomalies = PulseAnomaliesTable.selectAll()
            .where { PulseAnomaliesTable.endedAtMs.isNull() }
            .map { Anomaly(it[PulseAnomaliesTable.id], it[PulseAnomaliesTable.lat], it[PulseAnomaliesTable.lng], it[PulseAnomaliesTable.userCount], it[PulseAnomaliesTable.startedAtMs]) }
            .filter { a -> bbox == null || (a.lat in bbox.swLat..bbox.neLat && a.lng in bbox.swLng..bbox.neLng) }
        entries to anomalies
    }

    /** Один проход агрегации: glow/crowd по live-событиям + кластеры без события (G9) + TTL-чистка */
    suspend fun aggregateOnce(): Unit = ptx {
        val now = System.currentTimeMillis()
        // TTL: чистим старые пинги
        LocationPingsTable.deleteWhere { LocationPingsTable.createdAtMs less (now - PING_TTL_MS) }

        val pings = LocationPingsTable.selectAll()
            .where { LocationPingsTable.createdAtMs greaterEq (now - CROWD_WINDOW_MS) }
            .map { Triple(it[LocationPingsTable.userHash], it[LocationPingsTable.lat], it[LocationPingsTable.lng]) }

        val liveEvents = EventsTable.selectAll()
            .where { (EventsTable.status eq "live") or (EventsTable.status eq "soon") }
            .map { Triple(it[EventsTable.id], it[EventsTable.lat], it[EventsTable.lng]) to (it[EventsTable.goingCount] to it[EventsTable.liveViewers]) }

        // --- агрегаты на событие (G1/G2) ---
        for ((ev, counts) in liveEvents) {
            val (eid, elat, elng) = ev
            val (going, viewers) = counts
            val crowd = pings.filter { abs(it.second - elat) < NEAR_EVENT_DEG && abs(it.third - elng) < NEAR_EVENT_DEG }
                .map { it.first }.toSet().size
            val glow = minOf(1f, going / 150f + crowd / 60f + viewers / 400f)
            val updated = PulseAggregatesTable.update({ PulseAggregatesTable.eventId eq eid }) {
                it[PulseAggregatesTable.glow] = glow
                it[crowd10m] = crowd
                it[PulseAggregatesTable.viewers] = viewers
                it[updatedAtMs] = now
            }
            if (updated == 0) {
                PulseAggregatesTable.insert {
                    it[eventId] = eid
                    it[PulseAggregatesTable.glow] = glow
                    it[crowd10m] = crowd
                    it[PulseAggregatesTable.viewers] = viewers
                    it[updatedAtMs] = now
                }
            }
        }

        // --- аномалии без события (G9): грид-кластеры, k-анонимность ---
        val cells = HashMap<Pair<Int, Int>, MutableSet<String>>()
        for ((hash, lat, lng) in pings) {
            val key = (lat / CELL_DEG).roundToInt() to (lng / CELL_DEG).roundToInt()
            cells.getOrPut(key) { mutableSetOf() }.add(hash)
        }
        val hot = cells.filter { (key, users) ->
            users.size >= K_ANOMALY &&
                liveEvents.none { (ev, _) ->
                    abs(ev.second - key.first * CELL_DEG) < NEAR_EVENT_DEG && abs(ev.third - key.second * CELL_DEG) < NEAR_EVENT_DEG
                }
        }
        val active = PulseAnomaliesTable.selectAll()
            .where { PulseAnomaliesTable.endedAtMs.isNull() }
            .map { Triple(it[PulseAnomaliesTable.id], it[PulseAnomaliesTable.lat], it[PulseAnomaliesTable.lng]) }
        // открываем новые
        for ((key, users) in hot) {
            val clat = key.first * CELL_DEG
            val clng = key.second * CELL_DEG
            val exists = active.any { abs(it.second - clat) < CELL_DEG && abs(it.third - clng) < CELL_DEG }
            if (!exists) {
                PulseAnomaliesTable.insert {
                    it[id] = UUID.randomUUID().toString()
                    it[lat] = clat
                    it[lng] = clng
                    it[userCount] = users.size
                    it[startedAtMs] = now
                }
            }
        }
        // закрываем остывшие
        for ((aid, alat, alng) in active) {
            val stillHot = cells.entries.any { (key, users) ->
                users.size >= ANOMALY_CLOSE_BELOW &&
                    abs(key.first * CELL_DEG - alat) < CELL_DEG && abs(key.second * CELL_DEG - alng) < CELL_DEG
            }
            if (!stillHot) {
                PulseAnomaliesTable.update({ PulseAnomaliesTable.id eq aid }) { it[endedAtMs] = now }
            }
        }
    }

    /** Фоновая джоба: агрегация каждые 30 сек */
    fun startJob(): CoroutineScope {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            while (true) {
                runCatching { aggregateOnce() }
                delay(30_000)
            }
        }
        return scope
    }
}
