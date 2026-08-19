package com.eventmap.server.data.seed

import com.eventmap.server.data.db.AttendanceTable
import com.eventmap.server.data.db.BusinessAccountsTable
import com.eventmap.server.data.db.EventCommentsTable
import com.eventmap.server.data.db.EventRatingsTable
import com.eventmap.server.data.db.EventsTable
import com.eventmap.server.data.db.FriendshipsTable
import com.eventmap.server.data.db.OrganizerSubscriptionsTable
import com.eventmap.server.data.db.OrganizersTable
import com.eventmap.server.data.db.QuestPointsTable
import com.eventmap.server.data.db.QuestsTable
import com.eventmap.server.data.db.UsersTable
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.mindrot.jbcrypt.BCrypt
import org.slf4j.LoggerFactory
import java.time.LocalDate

// =====================================================================
//  Демо-сид для dev: наполняет пустую базу жизнью НА МЕСЯЦ ВПЕРЁД.
//  - 5 организаторов, ~30 событий на 30 дней (2 LIVE сегодня, 1 прошедшее),
//  - 4 сид-юзера (demo1..4@eventmap.kz / demo1234) с дружбой и подписками,
//  - оценки с тегами и комментарии на живых/прошедших событиях,
//  - 3 активных квеста (geo / photo / purchase, один — цепочка).
//  Идемпотентно (events не пуст → no-op). Прод (APP_ENV=prod) не трогает.
// =====================================================================

object DemoSeed {

    private val log = LoggerFactory.getLogger(DemoSeed::class.java)

    suspend fun seedIfEmpty(): Boolean = newSuspendedTransaction(Dispatchers.IO) {
        if (EventsTable.selectAll().limit(1).any()) return@newSuspendedTransaction false
        val now = System.currentTimeMillis()

        // ── Сид-юзеры (можно логиниться: demoN@eventmap.kz / demo1234) ──
        val demoHash = BCrypt.hashpw("demo1234", BCrypt.gensalt())
        val users = listOf(
            Triple("seed-user-1", "Аян", "ayan"),
            Triple("seed-user-2", "Дана", "dana"),
            Triple("seed-user-3", "Тимур", "timur"),
            Triple("seed-user-4", "Айжан", "aizhan"),
        )
        users.forEachIndexed { i, (uid, uname, ulogin) ->
            UsersTable.insert {
                it[id] = uid
                it[email] = "demo${i + 1}@eventmap.kz"
                it[name] = uname
                it[passwordHash] = demoHash
                it[username] = ulogin
                it[city] = "Астана"
                it[interests] = "Концерт,Спорт,Еда"
            }
        }
        // дружба: все четверо — друзья (userA < userB лексикографически)
        val ids = users.map { it.first }
        for (a in ids.indices) for (b in a + 1 until ids.size) {
            FriendshipsTable.insert {
                it[userA] = minOf(ids[a], ids[b])
                it[userB] = maxOf(ids[a], ids[b])
                it[status] = "accepted"
                it[requestedBy] = ids[a]
            }
        }

        // ── Организаторы ──
        val organizers = listOf(
            Triple("seed-org-rooftop", "Rooftop 360", "Крыша с видом на Байтерек · концерты и вечеринки"),
            Triple("seed-org-podval", "Бар «Подвал»", "Стендап по четвергам, квизы по вторникам"),
            Triple("seed-org-runclub", "Astana Run Club", "Забеги по набережной · вт, чт, вс"),
            Triple("seed-org-lectorium", "Лекторий Q", "Наука и технологии простым языком"),
            Triple("seed-org-artspace", "ArtSpace Astana", "Выставки, маркеты и мастер-классы"),
        )
        organizers.forEach { (oid, oname, obio) ->
            OrganizersTable.insert {
                it[id] = oid
                it[name] = oname
                it[bio] = obio
                it[isVerified] = true
                it[subscribers] = 4
            }
        }
        // подписки сид-юзеров — лента /feed у них живая
        ids.forEach { uid ->
            organizers.forEach { (oid, _, _) ->
                OrganizerSubscriptionsTable.insert {
                    it[userId] = uid
                    it[organizerId] = oid
                    it[createdAtMs] = now
                }
            }
        }

        // ── События: вчера (ended) + 2 LIVE сегодня + расписание на 30 дней ──
        data class Ev(
            val id: String, val title: String, val desc: String, val cat: String,
            val status: String, val day: LocalDate, val h: Int, val durH: Int,
            val lat: Double, val lng: Double, val place: String,
            val paid: Int, val orgId: String?, val viewers: Int = 0, val going: Int = 0,
        )

        val today = LocalDate.now()
        val events = mutableListOf(
            Ev("seed-ev-past", "Vinyl Night · ретро-вечер", "Пластинки, диджеи, старая школа.", "Концерт", "ended", today.minusDays(1), 20, 3, 51.1230, 71.4240, "Rooftop 360", 2500, "seed-org-rooftop", going = 96),
            Ev("seed-ev-nightbeats", "Night Beats · DJ-сет на крыше", "Атмосфера, музыка и вид на ночной город. Снимай момент — он попадёт в галерею.", "Концерт", "live", today, 20, 4, 51.1283, 71.4306, "Rooftop 360", 3000, "seed-org-rooftop", viewers = 230, going = 118),
            Ev("seed-ev-standup", "Стендап-вечер · Open Mic", "Открытый микрофон: новички и хедлайнеры.", "Стендап", "live", today, 19, 3, 51.1326, 71.4054, "Бар «Подвал», Хан Шатыр", 2000, "seed-org-podval", viewers = 85, going = 64),
        )

        // недельный шаблон на 30 дней вперёд: каждый день что-то есть
        for (offset in 1..30) {
            val d = today.plusDays(offset.toLong())
            val weekday = d.dayOfWeek.value // 1=Пн … 7=Вс
            fun id(slug: String) = "seed-ev-$slug-$offset"
            when (weekday) {
                1 -> events += Ev(id("quiz"), "Квиз «Мозгобойня»", "7 раундов, призы от бара.", "Квиз", "soon", d, 19, 2, 51.1450, 71.4300, "Бар «Подвал»", 2500, "seed-org-podval", going = 18 + offset % 20)
                2 -> events += Ev(id("run"), "Вечерний забег 5К", "Лёгкий темп, любой уровень.", "Спорт", "soon", d, 19, 1, 51.1580, 71.4350, "Триатлон-парк", 0, "seed-org-runclub", going = 25 + offset % 30)
                3 -> events += Ev(id("lecture"), "Лекция: технологии и город", "Разбор трендов простым языком.", "Лекция", "soon", d, 20, 2, 51.0890, 71.4020, "Mega Silk Way, лекторий", 0, "seed-org-lectorium", going = 30 + offset % 15)
                4 -> events += Ev(id("standup"), "Стендап-четверг", "Проверка нового материала.", "Стендап", "soon", d, 20, 2, 51.1326, 71.4054, "Бар «Подвал»", 2000, "seed-org-podval", going = 40 + offset % 25)
                5 -> events += Ev(id("dj"), "DJ-сет на крыше", "Пятничный сет и вид на город.", "Концерт", "soon", d, 21, 4, 51.1283, 71.4306, "Rooftop 360", 3000, "seed-org-rooftop", going = 60 + offset % 40)
                6 -> {
                    events += Ev(id("market"), "Арт-маркет выходного дня", "Локальные бренды и мастер-классы.", "Выставки", "soon", d, 12, 6, 51.0894, 71.4104, "Парк ЭКСПО", 0, "seed-org-artspace", going = 45 + offset % 30)
                    events += Ev(id("kids"), "Детский день в парке", "Аниматоры и мороженое.", "Детям", "soon", d, 11, 3, 51.1060, 71.4160, "Ботанический сад", 0, null, going = 35)
                }
                7 -> events += Ev(id("yoga"), "Йога на рассвете", "Утренняя практика на траве.", "Йога", "soon", d, 8, 1, 51.1060, 71.4160, "Ботанический сад", 0, "seed-org-runclub", going = 20 + offset % 10)
            }
        }

        events.forEach { e ->
            EventsTable.insert {
                it[id] = e.id
                it[title] = e.title
                it[description] = e.desc
                it[category] = e.cat
                it[status] = e.status
                it[startAt] = "${e.day}T%02d:00".format(e.h)
                it[endAt] = "${e.day}T%02d:00".format(minOf(e.h + e.durH, 23))
                it[lat] = e.lat
                it[lng] = e.lng
                it[locationName] = e.place
                it[priceType] = if (e.paid > 0) "paid" else "free"
                it[priceAmount] = e.paid
                it[currency] = "KZT"
                it[organizerId] = e.orgId
                it[liveViewers] = e.viewers
                it[goingCount] = e.going
            }
        }

        // ── Оценки с тегами (прошедшее + LIVE) от сид-юзеров ──
        data class Rt(val eventId: String, val userId: String, val value: Int, val review: String, val tags: String)
        val ratings = listOf(
            Rt("seed-ev-past", "seed-user-1", 5, "Лучший вечер месяца, звук топ!", "🔥 Атмосфера,🎶 Музыка топ"),
            Rt("seed-ev-past", "seed-user-2", 4, "Классно, но тесновато у бара.", "🎶 Музыка топ,😕 Мало мест"),
            Rt("seed-ev-past", "seed-user-3", 5, "", "🔥 Атмосфера"),
            Rt("seed-ev-nightbeats", "seed-user-4", 5, "Вид на город — вау.", "🔥 Атмосфера,🍸 Бар"),
        )
        ratings.forEach { r ->
            EventRatingsTable.insert {
                it[id] = "seed-rt-${r.eventId}-${r.userId}"
                it[eventId] = r.eventId
                it[userId] = r.userId
                it[value] = r.value
                it[review] = r.review
                it[tags] = r.tags
                it[createdAtMs] = now - 3_600_000
            }
        }
        // кэш рейтинга событий + история «был» у оценивших
        listOf("seed-ev-past" to Pair(4.7, 3), "seed-ev-nightbeats" to Pair(5.0, 1)).forEach { (eid, agg) ->
            EventsTable.update({ EventsTable.id eq eid }) {
                it[ratingAvg] = agg.first
                it[ratingCount] = agg.second
            }
        }
        ratings.forEach { r ->
            AttendanceTable.insert {
                it[userId] = r.userId
                it[eventId] = r.eventId
                it[type] = "was"
            }
        }

        // ── Комментарии D9 на LIVE-событиях ──
        data class Cm(val eventId: String, val userId: String, val body: String, val likes: Int, val minAgo: Long)
        listOf(
            Cm("seed-ev-nightbeats", "seed-user-4", "крыша просто 🔥 уже едем!", 12, 2),
            Cm("seed-ev-nightbeats", "seed-user-3", "лучший вид на город 🌃", 21, 8),
            Cm("seed-ev-nightbeats", "seed-user-2", "какой сет-лист сегодня?", 5, 5),
            Cm("seed-ev-standup", "seed-user-1", "открытый микрофон — топ, идите!", 9, 12),
            Cm("seed-ev-standup", "seed-user-2", "занимайте столики заранее", 4, 25),
        ).forEach { c ->
            EventCommentsTable.insert {
                it[id] = "seed-cm-${c.eventId}-${c.userId}"
                it[eventId] = c.eventId
                it[authorId] = c.userId
                it[body] = c.body
                it[likeCount] = c.likes
                it[createdAtMs] = now - c.minAgo * 60_000
            }
        }

        // ── Бизнес + 3 квеста на месяц (geo / photo / purchase) ──
        BusinessAccountsTable.insert {
            it[id] = "seed-biz-burger"
            it[userId] = "seed-biz-user"
            it[bin] = "000000000001"
            it[legalName] = "ТОО Burger Bros"
            it[brandName] = "Burger Bros"
            it[category] = "Еда"
            it[status] = "approved"
        }
        val monthEnd = today.plusDays(30)
        data class Q(val id: String, val title: String, val emoji: String, val chain: Boolean, val prize: String, val points: List<Triple<Pair<Double, Double>, String, String>>)
        listOf(
            Q("seed-quest-burgers", "Тур по бургерным", "🍔", true, "Бесплатный кофе в любом филиале", listOf(
                Triple(51.1283 to 71.4380, "Burger Bros · Набережная", "geo"),
                Triple(51.1326 to 71.4054, "Burger Bros · Хан Шатыр", "photo"),
                Triple(51.0890 to 71.4020, "Burger Bros · Mega", "purchase"),
            )),
            Q("seed-quest-coffee", "5 кофеен за выходные", "☕", false, "Мерч Coffee Boom", listOf(
                Triple(51.1290 to 71.4300, "Coffee Boom · Байтерек", "geo"),
                Triple(51.1440 to 71.4310, "Coffee Boom · Кабанбай", "geo"),
                Triple(51.1080 to 71.4150, "Coffee Boom · Ботсад", "geo"),
            )),
            Q("seed-quest-art", "Арт-маршрут центра", "🎨", true, "Проходка на выставку", listOf(
                Triple(51.1180 to 71.4700, "Нацмузей", "photo"),
                Triple(51.1283 to 71.4306, "Байтерек · смотровая", "photo"),
                Triple(51.0894 to 71.4104, "ЭКСПО · сфера", "photo"),
            )),
        ).forEach { q ->
            QuestsTable.insert {
                it[id] = q.id
                it[businessId] = "seed-biz-burger"
                it[title] = q.title
                it[emoji] = q.emoji
                it[orderedChain] = q.chain
                it[endsAt] = "${monthEnd}T23:59"
                it[prizeTitle] = q.prize
                it[status] = "active"
            }
            q.points.forEachIndexed { idx, (coords, pname, cond) ->
                QuestPointsTable.insert {
                    it[id] = "${q.id}-p${idx + 1}"
                    it[questId] = q.id
                    it[ord] = idx + 1
                    it[lat] = coords.first
                    it[lng] = coords.second
                    it[placeName] = pname
                    it[condition] = cond
                }
            }
        }

        log.info("DemoSeed: ${events.size} событий на 30 дней, ${users.size} юзера, ${organizers.size} организаторов, 3 квеста, оценки и комментарии")
        true
    }
}
