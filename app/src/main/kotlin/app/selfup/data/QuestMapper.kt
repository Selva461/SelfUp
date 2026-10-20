package app.selfup.data

import app.selfup.core.CompletionInput
import app.selfup.core.Difficulty
import app.selfup.core.Quest
import app.selfup.core.Schedule
import app.selfup.core.Stat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Converts between the domain [Quest] and its Room rows. */
object QuestMapper {
    const val RECURRING = "recurring"
    const val ONE_TIME = "one_time"

    private const val TAP = "tap"
    private const val NUMBER = "number"
    private const val YES_NO = "yes_no"

    fun toDomain(row: QuestWithStats): Quest {
        val q = row.quest
        return Quest(
            id = q.id,
            title = q.title,
            difficulty = Difficulty.fromValue(q.difficulty),
            stats = row.stats.map(Stat::fromKey).toSet(),
            schedule = when (q.scheduleKind) {
                RECURRING -> Schedule.Recurring(daysFromMask(q.weekdayMask))
                ONE_TIME -> Schedule.OneTime(q.expiresOnEpochDay?.let(LocalDate::ofEpochDay))
                else -> error("Unknown schedule kind ${q.scheduleKind}")
            },
            input = when (q.inputKind) {
                TAP -> CompletionInput.Tap
                NUMBER -> CompletionInput.Number(q.inputUnit.orEmpty())
                YES_NO -> CompletionInput.YesNo
                else -> error("Unknown input kind ${q.inputKind}")
            },
            reminder = q.reminderMinuteOfDay?.let { LocalTime.of(it / 60, it % 60) },
            penaltyFor = q.penaltyForQuestId,
        )
    }

    fun toEntity(quest: Quest, createdAtMillis: Long): QuestEntity {
        val schedule = quest.schedule
        val input = quest.input
        return QuestEntity(
            id = quest.id,
            title = quest.title.trim(),
            difficulty = quest.difficulty.value,
            scheduleKind = when (schedule) {
                is Schedule.Recurring -> RECURRING
                is Schedule.OneTime -> ONE_TIME
            },
            weekdayMask = if (schedule is Schedule.Recurring) maskFromDays(schedule.days) else 0,
            expiresOnEpochDay = (schedule as? Schedule.OneTime)?.expiresOn?.toEpochDay(),
            inputKind = when (input) {
                CompletionInput.Tap -> TAP
                is CompletionInput.Number -> NUMBER
                CompletionInput.YesNo -> YES_NO
            },
            inputUnit = (input as? CompletionInput.Number)?.unit,
            reminderMinuteOfDay = quest.reminder?.let { it.hour * 60 + it.minute },
            penaltyForQuestId = quest.penaltyFor,
            createdAtMillis = createdAtMillis,
        )
    }

    fun statRows(questId: Long, stats: Set<Stat>): List<QuestStatEntity> =
        stats.map { QuestStatEntity(questId, it.key) }

    internal fun maskFromDays(days: Set<DayOfWeek>): Int =
        days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

    internal fun daysFromMask(mask: Int): Set<DayOfWeek> =
        DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()
}
