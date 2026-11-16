package com.eventmap.server.data.venue

import com.eventmap.server.domain.model.*
import com.eventmap.server.domain.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

object VenuePlansTable : Table("venue_plans") {
    val buildingId = varchar("building_id",100)
    val version = long("edit_version")
    val name = varchar("building_name",120)
    val draft = text("draft_json")
    val published = text("published_json").nullable()
    val updatedAtMs = long("updated_at_ms")
    override val primaryKey = PrimaryKey(buildingId)
}
object VenueRevisionsTable : Table("venue_revisions") {
    val revision = varchar("revision",64)
    val buildingId = varchar("building_id",100).references(VenuePlansTable.buildingId)
    val document = text("document_json")
    val publishedAtMs = long("published_at_ms")
    override val primaryKey = PrimaryKey(revision)
}

/** No process cache: different server instances immediately observe committed publications. */
class DbVenueCatalog(
    private val fallback: JsonVenueCatalog = JsonVenueCatalog(),
    private val database: Database? = null,
) : VenueEditor {
    private val json = Json { encodeDefaults = true }
    private fun decode(value: String) = json.decodeFromString<VenueBuilding>(value)
    private suspend fun <T> tx(block: suspend Transaction.() -> T): T =
        newSuspendedTransaction(Dispatchers.IO, db=database, statement=block)
    private fun row(id: String) = VenuePlansTable.selectAll().where { VenuePlansTable.buildingId eq id }.singleOrNull()
    private fun state(row: ResultRow): VenueEditState {
        val id=row[VenuePlansTable.buildingId]
        return VenueEditState(row[VenuePlansTable.version],decode(row[VenuePlansTable.draft]),
            row[VenuePlansTable.published]?.let(::decode),
            VenueRevisionsTable.selectAll().where { VenueRevisionsTable.buildingId eq id }
                .orderBy(VenueRevisionsTable.publishedAtMs to SortOrder.DESC, VenueRevisionsTable.revision to SortOrder.DESC)
                .limit(20).map { VenueRevision(it[VenueRevisionsTable.revision],it[VenueRevisionsTable.publishedAtMs]) })
    }
    private fun conflict(): Nothing = throw VenueEditError("VERSION_CONFLICT","План уже изменён. Загрузите актуальную версию; ваши изменения не сохранены.")
    private fun missing(): Nothing = throw VenueEditError("NOT_FOUND","Здание или сохранённый черновик не найдены")

    override suspend fun building(id: String): VenueBuilding? {
        if(!validBuildingId(id))return null
        return tx { row(id)?.get(VenuePlansTable.published)?.let(::decode) } ?: fallback.building(id)
    }
    override suspend fun list(): List<VenueSummary> = tx {
        val stored=VenuePlansTable.select(VenuePlansTable.buildingId,VenuePlansTable.name,VenuePlansTable.version).orderBy(VenuePlansTable.buildingId).limit(500).map {
            VenueSummary(it[VenuePlansTable.buildingId],it[VenuePlansTable.name],it[VenuePlansTable.version])
        }
        (fallback.all().map { VenueSummary(it.buildingId,it.name,0) }.associateBy { it.buildingId } + stored.associateBy { it.buildingId })
            .values.sortedBy { it.buildingId }.take(500)
    }
    override suspend fun editor(id: String): VenueEditState? {
        if(!validBuildingId(id))return null
        val existing=tx { row(id)?.let(::state) }
        return existing ?: fallback.building(id)?.let { VenueEditState(0,it,it) }
    }
    override suspend fun save(id: String, expectedVersion: Long, building: VenueBuilding): VenueEditState {
        val normalized=building.copy(revision="draft")
        if(expectedVersion<0 || building.buildingId!=id || !normalized.isValid() || json.encodeToString(normalized).toByteArray().size>1_000_000)
            throw VenueEditError("INVALID_PLAN","Проверьте ID, размеры схемы, уникальность этажей/помещений и контуры в пределах плана")
        val base=fallback.building(id)
        return tx {
            // The conflict-safe insert and row lock also cover first saves from two instances.
            if(base!=null)VenuePlansTable.insertIgnore {
                it[buildingId]=id;it[version]=0;it[name]=base.name;it[draft]=json.encodeToString(base)
                it[published]=json.encodeToString(base);it[updatedAtMs]=System.currentTimeMillis()
            }
            val current=VenuePlansTable.selectAll().where { VenuePlansTable.buildingId eq id }.forUpdate().singleOrNull() ?: missing()
            if(current[VenuePlansTable.version]!=expectedVersion)conflict()
            VenuePlansTable.update({ VenuePlansTable.buildingId eq id }) {
                it[draft]=json.encodeToString(normalized);it[name]=normalized.name;it[version]=expectedVersion+1;it[updatedAtMs]=System.currentTimeMillis()
            }
            state(row(id)!!)
        }
    }
    override suspend fun publish(id: String, expectedVersion: Long): VenueEditState = tx {
        val current=VenuePlansTable.selectAll().where { VenuePlansTable.buildingId eq id }.forUpdate().singleOrNull() ?: missing()
        if(current[VenuePlansTable.version]!=expectedVersion)conflict()
        val draft=decode(current[VenuePlansTable.draft])
        val old=current[VenuePlansTable.published]?.let(::decode)
        // Keeping previously published identities prevents broken event references, including concurrent creates.
        if(old!=null && old.floors.any { floor ->
            val next=draft.floors.firstOrNull { it.id==floor.id }
            next==null || floor.rooms.any { room -> next.rooms.none { it.id==room.id } }
        })throw VenueEditError("REFERENCES_REMOVED","Нельзя удалять опубликованные ID этажей или помещений. Сохраните ID; можно изменить название, контур или убрать рисунок.")
        val published=draft.copy(revision="p-"+UUID.randomUUID().toString())
        if(!published.isValid())throw VenueEditError("INVALID_PLAN","Схема не прошла проверку")
        val document=json.encodeToString(published);val now=System.currentTimeMillis()
        VenueRevisionsTable.insert {
            it[revision]=published.revision;it[buildingId]=id;it[this.document]=document;it[publishedAtMs]=now
        }
        VenuePlansTable.update({ VenuePlansTable.buildingId eq id }) {
            it[this.published]=document;it[version]=expectedVersion+1;it[updatedAtMs]=now
        }
        state(row(id)!!)
    }
    override suspend fun revision(id: String, revision: String): VenueBuilding? = tx {
        VenueRevisionsTable.selectAll().where { (VenueRevisionsTable.buildingId eq id) and (VenueRevisionsTable.revision eq revision) }
            .singleOrNull()?.get(VenueRevisionsTable.document)?.let(::decode)
    }
}
