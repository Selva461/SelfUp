package app.selfup.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "quest")
data class QuestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val difficulty: Int,
    val scheduleKind: String,
    /** Bit (DayOfWeek.value - 1) set for each day a recurring quest repeats on. */
    val weekdayMask: Int,
    val expiresOnEpochDay: Long?,
    val inputKind: String,
    val inputUnit: String?,
    val reminderMinuteOfDay: Int?,
    val penaltyForQuestId: Long?,
    val createdAtMillis: Long,
)

@Entity(
    tableName = "quest_stat",
    primaryKeys = ["questId", "stat"],
    foreignKeys = [
        ForeignKey(
            entity = QuestEntity::class,
            parentColumns = ["id"],
            childColumns = ["questId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("stat")],
)
data class QuestStatEntity(
    val questId: Long,
    val stat: String,
)

data class QuestWithStats(
    @Embedded val quest: QuestEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "questId",
        entity = QuestStatEntity::class,
        projection = ["stat"],
    )
    val stats: List<String>,
)

@Entity(
    tableName = "completion",
    foreignKeys = [
        ForeignKey(
            entity = QuestEntity::class,
            parentColumns = ["id"],
            childColumns = ["questId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    // One completion per quest per quest day.
    indices = [Index(value = ["questId", "epochDay"], unique = true), Index("epochDay")],
)
data class CompletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questId: Long,
    /** The quest day (see DayBoundary), not the calendar day of [completedAtMillis]. */
    val epochDay: Long,
    val completedAtMillis: Long,
    /** The logged number for a logging quest, such as rupees spent. */
    val value: Double?,
)

/**
 * Append-only XP ledger. A stat's XP is the sum of its deltas, so history survives quest
 * deletion (the references are nulled, not cascaded) and every number on screen is auditable.
 */
@Entity(
    tableName = "xp_event",
    foreignKeys = [
        ForeignKey(
            entity = QuestEntity::class,
            parentColumns = ["id"],
            childColumns = ["questId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = CompletionEntity::class,
            parentColumns = ["id"],
            childColumns = ["completionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("stat"), Index("questId"), Index("completionId"), Index("epochDay")],
)
data class XpEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stat: String,
    val delta: Int,
    val reason: String,
    val questId: Long?,
    val completionId: Long?,
    val epochDay: Long,
    val createdAtMillis: Long,
)

data class StatTotalRow(
    val stat: String,
    val total: Long,
)

@Entity(tableName = "meal_log", indices = [Index("atMillis")])
data class MealLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val atMillis: Long,
    val food: String,
)

data class DailySpendRow(
    val epochDay: Long,
    val total: Double,
)

/** Small key-value store for app state such as whether default quests were seeded. */
@Entity(tableName = "app_meta")
data class MetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)

/**
 * One logged lift, run or benchmark. [xpAwarded] is the personal-best bonus it earned, kept
 * so deleting the entry can take exactly that XP back.
 */
@Entity(
    tableName = "record_entry",
    indices = [Index("kind", "atMillis"), Index(value = ["source"], unique = true)],
)
data class RecordEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val atMillis: Long,
    val weightKg: Double?,
    val reps: Int?,
    val seconds: Int?,
    val distanceKm: Double?,
    val xpAwarded: Int,
    /** Where an imported entry came from, such as "gym:session:12:bench"; null when logged here. */
    val source: String? = null,
)

/**
 * One thing that happened to a concept: read its card, took its quiz, finished its task or
 * explained it. Mastery is replayed from these, so the history is the source of truth.
 */
@Entity(tableName = "skill_event", indices = [Index("conceptId")])
data class SkillEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conceptId: String,
    val kind: String,
    val epochDay: Long,
    val atMillis: Long,
    val correct: Int?,
    val total: Int?,
)

object SkillEventKind {
    const val READ = "read"
    const val QUIZ = "quiz"
    const val APPLIED = "applied"
    const val TAUGHT = "taught"
}

/** One finished mind game. [score] is in the game's own unit (digits, N, per minute, cells, ms). */
@Entity(tableName = "mind_score", indices = [Index("game")])
data class MindScoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val game: String,
    val score: Double,
    val epochDay: Long,
    val atMillis: Long,
    val xpAwarded: Int,
)

/**
 * A goal. [measure] is a [app.selfup.core.GoalMeasure.key]; [state] is one of [GoalState].
 * Only [GoalState.ACTIVE] goals get tasks; at most three are active at once.
 */
@Entity(tableName = "goal")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val measure: String,
    val target: Double,
    val baseline: Double?,
    val startEpochDay: Long,
    val deadlineEpochDay: Long,
    val state: String,
    val createdAtMillis: Long,
    /** A written week from a plan code ([app.selfup.core.PlanCode.encodeWeek]); null uses the built-in plan. */
    val plan: String? = null,
)

object GoalState {
    const val ACTIVE = "active"
    const val QUEUED = "queued"
    const val REACHED = "reached"
    const val DROPPED = "dropped"
}

object XpReason {
    const val COMPLETION = "completion"
    const val PENALTY = "penalty"
    const val UNDO = "undo"
    const val RECORD = "record"
    const val SKILL = "skill"
    const val MIND = "mind"
    const val GOAL = "goal"
    const val FOCUS = "focus"
}

/** How each closed quest day went, one row per day: the streak's audit trail. */
@Entity(tableName = "day_record")
data class DayRecordEntity(
    @PrimaryKey val epochDay: Long,
    val due: Int,
    val missed: Int,
    /** A [app.selfup.core.DayOutcome] name. */
    val outcome: String,
    val streakAfter: Int,
    val shieldsAfter: Int,
    val xpLost: Int,
    val closedAtMillis: Long,
)

/** One app's use on one quest day, from the usage log. Every app, not only entertainment. */
@Entity(tableName = "app_usage_day", primaryKeys = ["epochDay", "pkg"])
data class AppUsageDayEntity(
    val epochDay: Long,
    val pkg: String,
    val foregroundMillis: Long,
    val opens: Int,
    val entertainment: Boolean,
)

/** The phone as a whole on one quest day. */
@Entity(tableName = "phone_day")
data class PhoneDayEntity(
    @PrimaryKey val epochDay: Long,
    val firstUseMillis: Long?,
    val lastUseMillis: Long?,
    val totalMillis: Long,
    val entertainmentMillis: Long,
    val opens: Int,
    /** Opens from 23:00 until the reset. */
    val lateOpens: Int,
)

/** Every lock, grace unlock, verified quest and bonus, as it happened. */
@Entity(tableName = "focus_event", indices = [Index("epochDay")])
data class FocusEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val atMillis: Long,
    val epochDay: Long,
    /** One of [FocusEventKind]. */
    val kind: String,
    val pkg: String?,
    val questId: Long?,
    /** Entertainment minutes used that day when it happened. */
    val minutesUsed: Int?,
)

object FocusEventKind {
    const val LOCKED = "locked"
    const val GRACE = "grace"
    const val VERIFIED = "verified"
    const val BONUS = "bonus"
    const val WARNED = "warned"
}
