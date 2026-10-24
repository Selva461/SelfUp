package app.selfup.data

import androidx.room.withTransaction
import app.selfup.core.Achievement
import app.selfup.core.Achievements
import app.selfup.core.DayOutcome
import app.selfup.core.DungeonDay
import app.selfup.core.DungeonRank
import app.selfup.core.DungeonWeek
import app.selfup.core.Dungeons
import app.selfup.core.Gold
import app.selfup.core.HunterFacts
import app.selfup.core.Leveling
import app.selfup.core.Performance
import app.selfup.core.RestDays
import app.selfup.core.RestRefusal
import app.selfup.core.Reward
import app.selfup.core.RewardKind
import app.selfup.core.Stat
import app.selfup.core.StatChange
import app.selfup.core.StreakState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Everything the Hunter screens show, in one snapshot. */
data class HunterOverview(
    val today: LocalDate,
    val gold: Int = 0,
    val dungeon: DungeonWeek = DungeonWeek(today, emptyList()),
    /** False for the first, partial week after install, which is shown but not scored. */
    val dungeonScored: Boolean = true,
    val lastRun: DungeonRunEntity? = null,
    val facts: HunterFacts = HunterFacts(),
    /** Unlocked achievement ids, with when. */
    val unlocked: Map<String, Long> = emptyMap(),
    /** The achievement whose title is worn, if any. */
    val title: Achievement? = null,
    val rewards: List<Reward> = emptyList(),
    val purchases: List<PurchaseEntity> = emptyList(),
    val restDays: Set<LocalDate> = emptySet(),
) {
    val restedToday: Boolean get() = today in restDays
    val upcomingRest: List<LocalDate> get() = restDays.filter { !it.isBefore(today) }.sorted()
}

/** What a settlement paid out, for telling the user. */
data class HunterReport(
    val dayGold: Int = 0,
    val dungeon: DungeonRunEntity? = null,
    val dungeonChanges: List<StatChange> = emptyList(),
    val unlocked: List<Achievement> = emptyList(),
) {
    val isEmpty: Boolean get() = dayGold == 0 && dungeon == null && unlocked.isEmpty()
}

sealed interface BuyResult {
    data class Bought(val reward: Reward, val balance: Int, val unlocked: List<Achievement>) : BuyResult
    data class TooPoor(val short: Int) : BuyResult

    /** Screen minutes are capped per day so the lock keeps its teeth. */
    data object DailyLimit : BuyResult
    data object NotFound : BuyResult
}

/**
 * The game layer above quests: gold and the rewards shop, the weekly dungeon, achievements
 * and titles, and rest days. Every payout goes through a ledger row with a unique ref, so
 * settling twice never pays twice, and everything shown is replayable from the tables.
 */
class HunterRepository(
    private val db: SelfUpDatabase,
    private val life: LifeRepository,
) {
    private val clock = life.clock
    private val boundary = life.dayBoundary
    private val hunter = db.hunterDao()
    private val meta = db.metaDao()
    private val xp = db.xpDao()
    private val completions = db.completionDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<HunterOverview> = life.observeToday().flatMapLatest { today ->
        val ledgers = combine(xp.observeTotals(), hunter.observeGold(), hunter.observeAchievements(), meta.observe(TITLE_KEY)) { totals, gold, unlocked, title ->
            Ledgers(totals, gold, unlocked, title)
        }
        val shop = combine(hunter.observeRewards(), hunter.observePurchases(30), hunter.observeRestDays(), hunter.observeDungeonRuns(1)) { rewards, purchases, rest, runs ->
            Shop(rewards, purchases, rest, runs.firstOrNull())
        }
        combine(ledgers, shop) { l, s ->
            val rest = s.rest.map { LocalDate.ofEpochDay(it.epochDay) }.toSet()
            val start = Dungeons.weekStart(today)
            val from = meta.get(DUNGEON_FROM_KEY)?.toLongOrNull()
            val unlocked = l.unlocked.associate { it.id to it.unlockedAtMillis }
            HunterOverview(
                today = today,
                gold = l.gold,
                dungeon = Dungeons.progress(start, dungeonDays(start, today, rest)),
                dungeonScored = from == null || start.toEpochDay() >= from,
                lastRun = s.lastRun,
                facts = facts(l.totals.associate { Stat.fromKey(it.stat) to it.total }),
                unlocked = unlocked,
                title = l.title?.takeIf { it in unlocked }?.let(Achievements::byId),
                rewards = s.rewards.map(::toReward),
                purchases = s.purchases,
                restDays = rest,
            )
        }
    }

    private data class Ledgers(val totals: List<StatTotalRow>, val gold: Int, val unlocked: List<AchievementEntity>, val title: String?)
    private data class Shop(val rewards: List<RewardEntity>, val purchases: List<PurchaseEntity>, val rest: List<RestDayEntity>, val lastRun: DungeonRunEntity?)

    /**
     * Pays out for finished days and weeks and unlocks earned achievements. Run after
     * [LifeRepository.closeFinishedDays], so each day's outcome and penalties are final.
     */
    suspend fun settle(): HunterReport = db.withTransaction {
        seedRewardsOnce()
        val today = life.today()
        val dayGold = settleGold(today)
        val (run, changes) = settleDungeon(today)
        HunterReport(dayGold, run, changes, checkAchievements())
    }

    /** Unlocks any achievement now earned, paying its gold. Cheap enough to run on every XP change. */
    suspend fun checkAchievements(): List<Achievement> = db.withTransaction {
        val facts = facts(xp.totals().associate { Stat.fromKey(it.stat) to it.total })
        val earned = Achievements.newlyEarned(facts, hunter.achievementIds().toSet())
        val now = clock.millis()
        val day = life.today().toEpochDay()
        earned.filter { hunter.insertAchievement(AchievementEntity(it.id, now, it.tier.gold)) != -1L }.onEach {
            hunter.insertGold(GoldEventEntity(delta = it.tier.gold, reason = GoldReason.ACHIEVEMENT, ref = "ach:${it.id}", epochDay = day, atMillis = now))
        }
    }

    private suspend fun settleGold(today: LocalDate): Int {
        val yesterday = today.minusDays(1)
        val now = clock.millis()
        val last = meta.get(GOLD_SETTLED_KEY)?.toLongOrNull()
        if (last == null) {
            // XP earned before gold existed converts once, so nothing already won is lost.
            val earlier = hunter.netXpThrough(yesterday.toEpochDay()).toInt()
            if (earlier > 0) hunter.insertGold(GoldEventEntity(delta = earlier, reason = GoldReason.START, ref = "start", epochDay = today.toEpochDay(), atMillis = now))
            meta.put(MetaEntity(GOLD_SETTLED_KEY, yesterday.toEpochDay().toString()))
            return 0
        }
        if (last >= yesterday.toEpochDay()) return 0
        val records = hunter.dayRecords(last + 1, yesterday.toEpochDay()).associateBy { it.epochDay }
        var paid = 0
        for (day in (last + 1)..yesterday.toEpochDay()) {
            val outcome = records[day]?.outcome?.let(DayOutcome::valueOf) ?: DayOutcome.EMPTY
            val gold = Gold.forDay(hunter.netXpOn(day), outcome)
            if (gold > 0 && hunter.insertGold(GoldEventEntity(delta = gold, reason = GoldReason.DAY, ref = "day:$day", epochDay = day, atMillis = now)) != -1L) {
                paid += gold
            }
        }
        meta.put(MetaEntity(GOLD_SETTLED_KEY, yesterday.toEpochDay().toString()))
        return paid
    }

    /** Settles the most recent finished week, if it was scored and isn't settled yet. Older unsettled weeks are skipped. */
    private suspend fun settleDungeon(today: LocalDate): Pair<DungeonRunEntity?, List<StatChange>> {
        val current = Dungeons.weekStart(today)
        val from = meta.get(DUNGEON_FROM_KEY)?.toLongOrNull()
        if (from == null) {
            // A week joined part-way through is a warm-up; scoring starts with the first full week.
            val first = if (today == current) current else current.plusDays(7)
            meta.put(MetaEntity(DUNGEON_FROM_KEY, first.toEpochDay().toString()))
            return null to emptyList()
        }
        val week = current.minusDays(7)
        if (week.toEpochDay() < from || hunter.dungeonRun(week.toEpochDay()) != null) return null to emptyList()

        val rest = hunter.restDays().map(LocalDate::ofEpochDay).toSet()
        val result = Dungeons.progress(week, dungeonDays(week, week.plusDays(6), rest))
        val deltas = Dungeons.xp(result)
        val gold = Dungeons.gold(result)
        val now = clock.millis()
        val run = DungeonRunEntity(
            weekStartEpochDay = week.toEpochDay(),
            detail = result.detail,
            performance = result.performance,
            rank = result.rank.name,
            xp = deltas.values.sum(),
            gold = gold,
            settledAtMillis = now,
        )
        if (hunter.insertDungeonRun(run) == -1L) return null to emptyList()
        // Dated today: that's when the reward lands, and the closed Sunday stays as it was.
        val changes = deltas.flatMap { (stat, delta) -> life.grantBonus(stat, delta, today, XpReason.DUNGEON) }
        if (gold > 0) hunter.insertGold(GoldEventEntity(delta = gold, reason = GoldReason.DUNGEON, ref = "dungeon:${week.toEpochDay()}", epochDay = today.toEpochDay(), atMillis = now))
        return run to changes
    }

    /** What each day from [start] to [through] did for the dungeon; today counts as clear once every daily is done. */
    private suspend fun dungeonDays(start: LocalDate, through: LocalDate, rest: Set<LocalDate>): List<DungeonDay> {
        val from = start.toEpochDay()
        val to = through.toEpochDay()
        val records = hunter.dayRecords(from, to).associateBy { it.epochDay }
        val phone = hunter.phoneDays(from, to).associateBy { it.epochDay }
        val trained = hunter.trainingDays(from, to, Performance.FAIL_BELOW).toSet()
        val studied = hunter.studyDays(from, to).toSet()
        val today = life.today()
        return (from..to).map { epochDay ->
            val date = LocalDate.ofEpochDay(epochDay)
            val clear = when {
                date == today -> {
                    val due = life.dailiesDueOn(date)
                    due.isNotEmpty() && completions.questIdsOn(epochDay).toSet().containsAll(due.map { it.id })
                }
                else -> records[epochDay]?.outcome == DayOutcome.CLEAR.name
            }
            DungeonDay(
                date = date,
                clear = clear,
                trained = epochDay in trained,
                studied = epochDay in studied,
                scrollMinutes = phone[epochDay]?.let { (it.entertainmentMillis / 60_000).toInt() },
                rested = date in rest,
            )
        }
    }

    private suspend fun facts(totals: Map<Stat, Long>): HunterFacts {
        val streak = StreakState.decode(meta.get(STREAK_KEY))
        return HunterFacts(
            bestStreak = maxOf(streak.best, streak.current),
            clearDays = hunter.clearDayCount(),
            levels = Stat.launchSet.associateWith { Leveling.levelFor(totals[it] ?: 0) },
            personalBests = hunter.personalBestCount(),
            trainingDays = hunter.trainingDayCount(Performance.FAIL_BELOW),
            quizzes = hunter.quizCount(),
            conceptsStudied = hunter.conceptsQuizzedCount(),
            mindGames = hunter.mindGameCount(),
            workLogs = hunter.workLogCount(),
            lowScrollDays = hunter.lowScrollDayCount(Dungeons.LOW_SCROLL_MINUTES * 60_000L),
            phoneOffVerified = hunter.phoneOffPassCount(),
            goalsReached = hunter.goalsReachedCount(),
            dungeonsCleared = hunter.dungeonsClearedCount(),
            sRankDungeons = hunter.sRankCount(),
            purchases = hunter.purchaseCount(),
        )
    }

    // Rest days

    /** Declares [day] a rest day, or says why it can't be. */
    suspend fun declareRest(day: LocalDate): RestRefusal? = db.withTransaction {
        val taken = hunter.restDays().map(LocalDate::ofEpochDay).toSet()
        RestDays.refusal(day, clock.instant(), boundary, taken)?.let { return@withTransaction it }
        hunter.insertRestDay(RestDayEntity(day.toEpochDay(), clock.millis()))
        null
    }

    /** Calls off a rest day while that's still allowed. Returns whether it was removed. */
    suspend fun cancelRest(day: LocalDate): Boolean {
        if (!RestDays.canCancel(day, clock.instant(), boundary)) return false
        hunter.deleteRestDay(day.toEpochDay())
        return true
    }

    // Titles

    /** Wears [achievementId]'s title, or the rank title when null. Only unlocked titles can be worn. */
    suspend fun equipTitle(achievementId: String?) {
        if (achievementId == null) {
            meta.delete(TITLE_KEY)
        } else if (achievementId in hunter.achievementIds()) {
            meta.put(MetaEntity(TITLE_KEY, achievementId))
        }
    }

    // Shop

    suspend fun buy(rewardId: Long): BuyResult = db.withTransaction {
        val entity = hunter.reward(rewardId)?.takeIf { !it.archived } ?: return@withTransaction BuyResult.NotFound
        val reward = toReward(entity)
        val day = life.today().toEpochDay()
        if (reward.kind == RewardKind.SCREEN_MINUTES &&
            hunter.purchasesOn(day).count { it.kind == RewardKind.SCREEN_MINUTES.key } >= Gold.SCREEN_BUYS_PER_DAY
        ) {
            return@withTransaction BuyResult.DailyLimit
        }
        val balance = hunter.gold()
        if (balance < reward.price) return@withTransaction BuyResult.TooPoor(reward.price - balance)
        val now = clock.millis()
        val purchaseId = hunter.insertPurchase(
            PurchaseEntity(rewardId = reward.id, title = reward.title, price = reward.price, kind = reward.kind.key, minutes = reward.minutes, epochDay = day, atMillis = now),
        )
        hunter.insertGold(GoldEventEntity(delta = -reward.price, reason = GoldReason.PURCHASE, ref = "buy:$purchaseId", epochDay = day, atMillis = now))
        if (reward.kind == RewardKind.SCREEN_MINUTES) addExtraMinutes(day, reward.minutes)
        BuyResult.Bought(reward, balance - reward.price, checkAchievements())
    }

    /** Entertainment minutes bought for today, on top of the daily budget. */
    suspend fun extraMinutesToday(): Int = extraMinutes(meta.get(EXTRA_MINUTES_KEY), life.today().toEpochDay())

    private suspend fun addExtraMinutes(day: Long, minutes: Int) {
        val current = extraMinutes(meta.get(EXTRA_MINUTES_KEY), day)
        meta.put(MetaEntity(EXTRA_MINUTES_KEY, "$day:${current + minutes}"))
    }

    /** Inserts a new reward (id 0) or updates one. */
    suspend fun saveReward(reward: Reward): Long =
        if (reward.id == 0L) {
            hunter.insertReward(RewardEntity(title = reward.title.trim(), price = reward.price, kind = reward.kind.key, minutes = reward.minutes, createdAtMillis = clock.millis()))
        } else {
            val existing = requireNotNull(hunter.reward(reward.id)) { "No reward with id ${reward.id}" }
            hunter.updateReward(existing.copy(title = reward.title.trim(), price = reward.price, kind = reward.kind.key, minutes = reward.minutes))
            reward.id
        }

    /** Takes a reward off the shelf; past purchases keep their copy. */
    suspend fun deleteReward(id: Long) {
        hunter.reward(id)?.let { hunter.updateReward(it.copy(archived = true)) }
    }

    private suspend fun seedRewardsOnce() {
        if (meta.get(REWARDS_SEEDED_KEY) != null) return
        if (hunter.rewardCount() == 0) Gold.defaultRewards.forEach { saveReward(it) }
        meta.put(MetaEntity(REWARDS_SEEDED_KEY, "1"))
    }

    private fun toReward(e: RewardEntity) = Reward(e.id, e.title, e.price, RewardKind.fromKey(e.kind), e.minutes)

    companion object {
        /** "epochDay:minutes": entertainment minutes bought for that quest day. Read by the app lock. */
        const val EXTRA_MINUTES_KEY = "focus_extra_minutes"

        /** Minutes in an [EXTRA_MINUTES_KEY] value that apply to [epochDay]; a value from another day is spent. */
        fun extraMinutes(value: String?, epochDay: Long): Int {
            val parts = value?.split(':') ?: return 0
            if (parts.size != 2 || parts[0].toLongOrNull() != epochDay) return 0
            return parts[1].toIntOrNull()?.coerceAtLeast(0) ?: 0
        }

        private const val TITLE_KEY = "title"
        private const val GOLD_SETTLED_KEY = "gold_settled_day"
        private const val DUNGEON_FROM_KEY = "dungeon_from_week"
        private const val REWARDS_SEEDED_KEY = "seeded_rewards"

        /** Shared with [LifeRepository], which owns the streak. */
        private const val STREAK_KEY = "streak"
    }
}

/** A short label like "Rank B" for a stored run. */
val DungeonRunEntity.rankLabel: String get() = runCatching { DungeonRank.valueOf(rank).label }.getOrDefault(rank)
