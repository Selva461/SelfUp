package app.selfup.data

import androidx.room.withTransaction
import app.selfup.core.DayBoundary
import app.selfup.core.Stat
import app.selfup.core.StatChange
import app.selfup.core.WorkCode
import app.selfup.core.WorkCodeResult
import app.selfup.core.WorkJudge
import app.selfup.core.WorkReport
import app.selfup.core.XpRules
import kotlinx.coroutines.flow.Flow
import java.time.Clock

sealed interface WorkImport {
    data class Added(val report: WorkReport, val xp: Int, val changes: List<StatChange>) : WorkImport
    data object Duplicate : WorkImport
    data class Invalid(val line: Int, val message: String) : WorkImport
}

/** Things you built, scored from work codes. XP goes through the same ledger as everything else. */
class WorkRepository(
    private val db: SelfUpDatabase,
    private val clock: Clock,
    private val dayBoundary: DayBoundary,
) {
    private val work = db.workDao()
    private val xp = db.xpDao()

    fun observe(): Flow<List<WorkLogEntity>> = work.observeAll()

    /**
     * Reads [code] and grants its XP once. [judge] overrides the code's own JUDGE line: a code
     * the in-app Groq judge wrote is always Groq's, whatever it claims.
     */
    suspend fun import(code: String, judge: WorkJudge? = null): WorkImport = db.withTransaction {
        val parsed = when (val result = WorkCode.parse(code)) {
            is WorkCodeResult.Error -> return@withTransaction WorkImport.Invalid(result.line, result.message)
            is WorkCodeResult.Ok -> result.report
        }
        val report = judge?.let { parsed.copy(judge = it) } ?: parsed
        val fingerprint = WorkCode.fingerprint(report)
        if (work.count(fingerprint) > 0) return@withTransaction WorkImport.Duplicate

        val now = clock.instant()
        val day = dayBoundary.questDay(now).toEpochDay()
        val totals = xp.totals().associate { Stat.fromKey(it.stat) to it.total }
        val delta = XpRules.settle(totals[report.stat] ?: 0, report.xp)
        val changes = if (delta != 0) XpRules.apply(totals, mapOf(report.stat to delta)) else emptyList()
        if (delta != 0) {
            xp.insertAll(
                listOf(
                    XpEventEntity(
                        stat = report.stat.key,
                        delta = delta,
                        reason = XpReason.WORK,
                        questId = null,
                        completionId = null,
                        epochDay = day,
                        createdAtMillis = now.toEpochMilli(),
                    ),
                ),
            )
        }
        work.insert(
            WorkLogEntity(
                fingerprint = fingerprint,
                title = report.title,
                stat = report.stat.key,
                difficulty = report.difficulty,
                quality = report.quality,
                completeness = report.completeness,
                ownership = report.ownership,
                hours = report.hours,
                judge = report.judge.key,
                note = report.note,
                performance = report.performance,
                xpAwarded = delta,
                epochDay = day,
                atMillis = now.toEpochMilli(),
                code = WorkCode.write(report),
            ),
        )
        WorkImport.Added(report, delta, changes)
    }
}
