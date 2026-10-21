package app.selfup.data

import androidx.room.withTransaction
import app.selfup.core.DayBoundary
import app.selfup.core.ImportedRecord
import app.selfup.core.Metric
import app.selfup.core.PersonalBest
import app.selfup.core.RecordEntry
import app.selfup.core.RecordKind
import app.selfup.core.RecordMath
import app.selfup.core.Stat
import app.selfup.core.StatChange
import app.selfup.core.XpRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant

/** A saved entry with its id, so the screen can delete it. [imported] entries are owned by the app they came from. */
data class LoggedRecord(val id: Long, val entry: RecordEntry, val imported: Boolean = false)

/** One record kind as the Records screen shows it. */
data class RecordSummary(
    val kind: RecordKind,
    /** Every entry, oldest first. */
    val entries: List<LoggedRecord>,
    val bests: Map<Metric, Double>,
) {
    val latest: RecordEntry? get() = entries.lastOrNull()?.entry

    /** The headline metric of each entry over time, for the sparkline and chart. */
    val headline: List<Double>
        get() = entries.mapNotNull { RecordMath.metrics(it.entry)[kind.metrics.first()] }
}

data class ImportResult(
    /** Entries that weren't here before. */
    val added: Int,
    /** Entries replaced because they changed at the source. */
    val updated: Int,
    /** Entries removed because they were deleted at the source. */
    val removed: Int,
    /** Personal bests set by imported entries that earned XP, oldest first. */
    val personalBests: List<Pair<RecordKind, PersonalBest>>,
    val changes: List<StatChange>,
)

data class RecordLogResult(
    val entryId: Long,
    val personalBests: List<PersonalBest>,
    /** XP changes from the personal-best bonus; empty when no record fell. */
    val changes: List<StatChange>,
)

/** Lifts, runs and benchmarks. A personal best grants bonus XP through the same ledger as quests. */
class RecordsRepository(
    private val db: SelfUpDatabase,
    private val clock: Clock,
    private val dayBoundary: DayBoundary,
) {
    private val records = db.recordDao()
    private val xp = db.xpDao()

    fun observeSummaries(): Flow<List<RecordSummary>> = records.observeAll().map { rows ->
        val byKind = rows.groupBy { it.kind }
        RecordKind.entries.map { kind ->
            val entries = byKind[kind.key].orEmpty().map { LoggedRecord(it.id, it.toDomain(), imported = it.source != null) }
            RecordSummary(kind, entries, RecordMath.bests(entries.map { it.entry }))
        }
    }

    /** The last entry of [kind], to prefill the logging sheet. */
    suspend fun latest(kind: RecordKind): RecordEntry? = records.latest(kind.key)?.toDomain()

    suspend fun log(
        kind: RecordKind,
        weightKg: Double? = null,
        reps: Int? = null,
        seconds: Int? = null,
        distanceKm: Double? = null,
    ): RecordLogResult = db.withTransaction {
        val now = clock.instant()
        val entry = RecordEntry(kind, now, weightKg, reps, seconds, distanceKm)
        val history = records.history(kind.key).map { it.toDomain() }
        val bests = RecordMath.personalBests(history, entry)
        val bonus = RecordMath.xpFor(bests)
        val id = records.insert(entry.toEntity(xpAwarded = bonus))
        val changes = if (bonus > 0) grant(kind.stat, bonus, XpReason.RECORD, now) else emptyList()
        RecordLogResult(id, bests, changes)
    }

    /**
     * Makes the entries imported under [prefix] exactly [imported]: new ones are added, ones
     * that changed at the source are replaced, and ones deleted there are removed, taking
     * back any XP they earned. Running it twice with the same input changes nothing.
     *
     * Entries before [earnsFrom] are history: they count towards bests but earn no bonus,
     * so connecting a year of training doesn't pay out a year of personal bests at once.
     */
    suspend fun syncImported(prefix: String, imported: List<ImportedRecord>, earnsFrom: Instant): ImportResult = db.withTransaction {
        require(imported.all { it.source.startsWith(prefix) }) { "Every source must start with $prefix" }
        val wanted = imported.associateBy { it.source }
        val existing = records.importedFrom(prefix).associateBy { it.source!! }
        val now = clock.instant()
        val stale = existing.values.filter { wanted[it.source!!]?.entry != it.toDomain() }
        stale.forEach { row ->
            records.delete(row.id)
            if (row.xpAwarded > 0) grant(RecordKind.fromKey(row.kind).stat, -row.xpAwarded, XpReason.UNDO, now)
        }
        val staleSources = stale.mapTo(mutableSetOf()) { it.source!! }
        val toAdd = imported.filter { it.source !in existing || it.source in staleSources }.sortedBy { it.entry.at }
        val bests = mutableListOf<Pair<RecordKind, PersonalBest>>()
        val changes = mutableListOf<StatChange>()
        toAdd.forEach { (source, entry) ->
            val history = records.history(entry.kind.key).map { it.toDomain() }.filter { it.at < entry.at }
            val beaten = if (entry.at < earnsFrom) emptyList() else RecordMath.personalBests(history, entry)
            val bonus = RecordMath.xpFor(beaten)
            records.insert(entry.toEntity(xpAwarded = bonus, source = source))
            if (bonus > 0) {
                bests += beaten.map { entry.kind to it }
                changes += grant(entry.kind.stat, bonus, XpReason.RECORD, entry.at)
            }
        }
        val updated = toAdd.count { it.source in existing }
        ImportResult(
            added = toAdd.size - updated,
            updated = updated,
            removed = staleSources.count { it !in wanted },
            personalBests = bests,
            changes = changes,
        )
    }

    /** Deletes an entry and takes back any bonus XP it earned. */
    suspend fun delete(id: Long) = db.withTransaction {
        val row = records.get(id) ?: return@withTransaction
        records.delete(id)
        if (row.xpAwarded > 0) grant(RecordKind.fromKey(row.kind).stat, -row.xpAwarded, XpReason.UNDO, clock.instant())
    }

    private suspend fun grant(stat: Stat, delta: Int, reason: String, at: Instant): List<StatChange> {
        val totals = xp.totals().associate { Stat.fromKey(it.stat) to it.total }
        val changes = XpRules.apply(totals, mapOf(stat to delta))
        xp.insertAll(
            listOf(
                XpEventEntity(
                    stat = stat.key,
                    delta = delta,
                    reason = reason,
                    questId = null,
                    completionId = null,
                    epochDay = dayBoundary.questDay(at).toEpochDay(),
                    createdAtMillis = at.toEpochMilli(),
                ),
            ),
        )
        return changes
    }
}

internal fun RecordEntryEntity.toDomain() = RecordEntry(
    kind = RecordKind.fromKey(kind),
    at = Instant.ofEpochMilli(atMillis),
    weightKg = weightKg,
    reps = reps,
    seconds = seconds,
    distanceKm = distanceKm,
)

private fun RecordEntry.toEntity(xpAwarded: Int, source: String? = null) = RecordEntryEntity(
    kind = kind.key,
    atMillis = at.toEpochMilli(),
    weightKg = weightKg,
    reps = reps,
    seconds = seconds,
    distanceKm = distanceKm,
    xpAwarded = xpAwarded,
    source = source,
)
