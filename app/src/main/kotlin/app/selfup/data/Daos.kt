package app.selfup.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestDao {
    @Transaction
    @Query("SELECT * FROM quest ORDER BY createdAtMillis, id")
    fun observeAll(): Flow<List<QuestWithStats>>

    @Transaction
    @Query("SELECT * FROM quest WHERE id = :id")
    suspend fun get(id: Long): QuestWithStats?

    @Transaction
    @Query(
        """
        SELECT q.* FROM quest q JOIN quest_stat s ON s.questId = q.id
        WHERE s.stat = :stat ORDER BY q.createdAtMillis, q.id
        """,
    )
    fun observeFeeding(stat: String): Flow<List<QuestWithStats>>

    @Transaction
    @Query("SELECT * FROM quest")
    suspend fun all(): List<QuestWithStats>

    @Query("SELECT COUNT(*) FROM quest")
    suspend fun count(): Int

    @Query("DELETE FROM quest WHERE penaltyForQuestId IS NOT NULL AND expiresOnEpochDay < :epochDay")
    suspend fun deletePenaltyQuestsExpiredBefore(epochDay: Long)

    @Insert
    suspend fun insert(quest: QuestEntity): Long

    @Update
    suspend fun update(quest: QuestEntity)

    @Query("DELETE FROM quest WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert
    suspend fun insertStats(stats: List<QuestStatEntity>)

    @Query("DELETE FROM quest_stat WHERE questId = :questId")
    suspend fun clearStats(questId: Long)
}

@Dao
interface CompletionDao {
    @Query("SELECT * FROM completion WHERE epochDay = :epochDay")
    fun observeOn(epochDay: Long): Flow<List<CompletionEntity>>

    /** Ids of one-time quests that are already done, on any day. */
    @Query(
        """
        SELECT DISTINCT c.questId FROM completion c JOIN quest q ON q.id = c.questId
        WHERE q.scheduleKind = '${QuestMapper.ONE_TIME}'
        """,
    )
    fun observeFinishedOneTimeIds(): Flow<List<Long>>

    @Query("SELECT questId FROM completion WHERE epochDay = :epochDay")
    suspend fun questIdsOn(epochDay: Long): List<Long>

    @Query("SELECT * FROM completion WHERE questId = :questId AND epochDay = :epochDay")
    suspend fun get(questId: Long, epochDay: Long): CompletionEntity?

    @Query("SELECT * FROM completion WHERE questId = :questId ORDER BY completedAtMillis DESC LIMIT 1")
    suspend fun latest(questId: Long): CompletionEntity?

    @Query("SELECT value FROM completion WHERE questId = :questId AND value IS NOT NULL ORDER BY completedAtMillis DESC LIMIT 1")
    suspend fun lastValue(questId: Long): Double?

    /** Logged numbers of [questId] before [beforeEpochDay], oldest first: the history a spend is judged against. */
    @Query("SELECT value FROM completion WHERE questId = :questId AND value IS NOT NULL AND epochDay < :beforeEpochDay ORDER BY epochDay")
    suspend fun valuesBefore(questId: Long, beforeEpochDay: Long): List<Double>

    @Query("UPDATE completion SET performance = :performance WHERE id = :id")
    suspend fun setPerformance(id: Long, performance: Double)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(completion: CompletionEntity): Long

    @Query("DELETE FROM completion WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface XpDao {
    @Query("SELECT stat, SUM(delta) AS total FROM xp_event GROUP BY stat")
    fun observeTotals(): Flow<List<StatTotalRow>>

    @Query("SELECT stat, SUM(delta) AS total FROM xp_event GROUP BY stat")
    suspend fun totals(): List<StatTotalRow>

    @Query("SELECT * FROM xp_event WHERE stat = :stat ORDER BY createdAtMillis DESC, id DESC LIMIT :limit")
    fun observeRecent(stat: String, limit: Int): Flow<List<XpEventEntity>>

    @Query("SELECT * FROM xp_event WHERE completionId = :completionId")
    suspend fun forCompletion(completionId: Long): List<XpEventEntity>

    @Insert
    suspend fun insertAll(events: List<XpEventEntity>)
}

@Dao
interface MetaDao {
    @Query("SELECT value FROM app_meta WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM app_meta WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(meta: MetaEntity)

    @Query("DELETE FROM app_meta WHERE `key` = :key")
    suspend fun delete(key: String)
}

@Dao
interface TrackingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putDay(day: DayRecordEntity)

    @Query("SELECT * FROM day_record ORDER BY epochDay DESC LIMIT :limit")
    suspend fun recentDays(limit: Int): List<DayRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAppUsage(rows: List<AppUsageDayEntity>)

    @Query("SELECT * FROM app_usage_day WHERE epochDay = :epochDay ORDER BY foregroundMillis DESC")
    suspend fun appUsageOn(epochDay: Long): List<AppUsageDayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPhoneDay(day: PhoneDayEntity)

    @Query("SELECT * FROM phone_day WHERE epochDay = :epochDay")
    suspend fun phoneDay(epochDay: Long): PhoneDayEntity?

    @Insert
    suspend fun insertFocusEvent(event: FocusEventEntity): Long

    @Query("SELECT * FROM focus_event WHERE epochDay = :epochDay ORDER BY atMillis")
    suspend fun focusEventsOn(epochDay: Long): List<FocusEventEntity>

    @Insert
    suspend fun insertAutoCheck(check: AutoCheckEntity): Long

    @Query("SELECT * FROM auto_check WHERE epochDay = :epochDay ORDER BY atMillis")
    suspend fun autoChecksOn(epochDay: Long): List<AutoCheckEntity>

    @Query("SELECT * FROM auto_check WHERE questId = :questId AND epochDay = :epochDay ORDER BY atMillis DESC, id DESC LIMIT 1")
    suspend fun lastAutoCheck(questId: Long, epochDay: Long): AutoCheckEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putGymScores(rows: List<GymSessionScoreEntity>)

    @Query("SELECT * FROM gym_session_score WHERE epochDay = :epochDay ORDER BY startMillis")
    suspend fun gymScoresOn(epochDay: Long): List<GymSessionScoreEntity>
}

@Dao
interface WorkDao {
    @Query("SELECT * FROM work_log ORDER BY atMillis DESC")
    fun observeAll(): Flow<List<WorkLogEntity>>

    @Query("SELECT COUNT(*) FROM work_log WHERE fingerprint = :fingerprint")
    suspend fun count(fingerprint: String): Int

    @Insert
    suspend fun insert(work: WorkLogEntity): Long
}

@Dao
interface HealthDao {
    @Query("SELECT * FROM meal_log WHERE atMillis >= :sinceMillis ORDER BY atMillis DESC")
    fun observeMeals(sinceMillis: Long): Flow<List<MealLogEntity>>

    @Query("SELECT * FROM meal_log ORDER BY atMillis")
    suspend fun allMeals(): List<MealLogEntity>

    @Insert
    suspend fun insertMeal(meal: MealLogEntity): Long

    @Query("DELETE FROM meal_log WHERE id = :id")
    suspend fun deleteMeal(id: Long)

    @Query("UPDATE meal_log SET quality = :quality WHERE id = :id")
    suspend fun setQuality(id: Long, quality: String)

    @Query("SELECT * FROM meal_log WHERE atMillis >= :fromMillis AND atMillis < :toMillis ORDER BY atMillis")
    suspend fun mealsBetween(fromMillis: Long, toMillis: Long): List<MealLogEntity>

    /** Money logged through number quests, per quest day. */
    @Query(
        """
        SELECT c.epochDay AS epochDay, SUM(c.value) AS total FROM completion c
        JOIN quest_stat s ON s.questId = c.questId
        WHERE s.stat = 'money' AND c.value IS NOT NULL AND c.epochDay >= :sinceEpochDay
        GROUP BY c.epochDay ORDER BY c.epochDay
        """,
    )
    fun observeDailySpend(sinceEpochDay: Long): Flow<List<DailySpendRow>>
}

@Dao
interface RecordDao {
    @Query("SELECT * FROM record_entry ORDER BY atMillis, id")
    fun observeAll(): Flow<List<RecordEntryEntity>>

    @Query("SELECT * FROM record_entry WHERE kind = :kind ORDER BY atMillis, id")
    suspend fun history(kind: String): List<RecordEntryEntity>

    @Query("SELECT * FROM record_entry WHERE kind = :kind ORDER BY atMillis DESC, id DESC LIMIT 1")
    suspend fun latest(kind: String): RecordEntryEntity?

    @Query("SELECT * FROM record_entry WHERE id = :id")
    suspend fun get(id: Long): RecordEntryEntity?

    @Insert
    suspend fun insert(entry: RecordEntryEntity): Long

    @Query("DELETE FROM record_entry WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM record_entry WHERE source LIKE :prefix || '%'")
    suspend fun importedFrom(prefix: String): List<RecordEntryEntity>
}

@Dao
interface SkillDao {
    @Query("SELECT * FROM skill_event ORDER BY epochDay, id")
    fun observeAll(): Flow<List<SkillEventEntity>>

    @Query("SELECT * FROM skill_event ORDER BY epochDay, id")
    suspend fun all(): List<SkillEventEntity>

    @Query("SELECT * FROM skill_event WHERE conceptId = :conceptId ORDER BY epochDay, id")
    suspend fun forConcept(conceptId: String): List<SkillEventEntity>

    @Insert
    suspend fun insert(event: SkillEventEntity): Long
}

@Dao
interface MindDao {
    @Query("SELECT * FROM mind_score ORDER BY atMillis, id")
    fun observeAll(): Flow<List<MindScoreEntity>>

    @Query("SELECT * FROM mind_score WHERE game = :game ORDER BY atMillis, id")
    suspend fun forGame(game: String): List<MindScoreEntity>

    @Insert
    suspend fun insert(score: MindScoreEntity): Long
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goal ORDER BY createdAtMillis, id")
    fun observeAll(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goal WHERE id = :id")
    suspend fun get(id: Long): GoalEntity?

    @Query("SELECT * FROM goal WHERE state = :state ORDER BY createdAtMillis, id")
    suspend fun withState(state: String): List<GoalEntity>

    @Insert
    suspend fun insert(goal: GoalEntity): Long

    @Update
    suspend fun update(goal: GoalEntity)

    @Query("DELETE FROM goal WHERE id = :id")
    suspend fun delete(id: Long)
}

/** Rest days, gold, the shop, dungeons and achievements, plus the counts achievements read. */
@Dao
interface HunterDao {
    @Query("SELECT * FROM rest_day ORDER BY epochDay")
    fun observeRestDays(): Flow<List<RestDayEntity>>

    @Query("SELECT epochDay FROM rest_day")
    suspend fun restDays(): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRestDay(day: RestDayEntity)

    @Query("DELETE FROM rest_day WHERE epochDay = :epochDay")
    suspend fun deleteRestDay(epochDay: Long)

    @Query("SELECT COALESCE(SUM(delta), 0) FROM gold_event")
    fun observeGold(): Flow<Int>

    @Query("SELECT COALESCE(SUM(delta), 0) FROM gold_event")
    suspend fun gold(): Int

    /** Returns -1 when [event]'s ref already paid. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGold(event: GoldEventEntity): Long

    @Query("SELECT * FROM gold_event ORDER BY atMillis DESC, id DESC LIMIT :limit")
    fun observeRecentGold(limit: Int): Flow<List<GoldEventEntity>>

    @Query("SELECT * FROM reward WHERE archived = 0 ORDER BY price, id")
    fun observeRewards(): Flow<List<RewardEntity>>

    @Query("SELECT * FROM reward WHERE id = :id")
    suspend fun reward(id: Long): RewardEntity?

    @Query("SELECT COUNT(*) FROM reward")
    suspend fun rewardCount(): Int

    @Insert
    suspend fun insertReward(reward: RewardEntity): Long

    @Update
    suspend fun updateReward(reward: RewardEntity)

    @Insert
    suspend fun insertPurchase(purchase: PurchaseEntity): Long

    @Query("SELECT * FROM purchase WHERE epochDay = :epochDay")
    suspend fun purchasesOn(epochDay: Long): List<PurchaseEntity>

    @Query("SELECT * FROM purchase ORDER BY atMillis DESC LIMIT :limit")
    fun observePurchases(limit: Int): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM dungeon_run WHERE weekStartEpochDay = :weekStart")
    suspend fun dungeonRun(weekStart: Long): DungeonRunEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDungeonRun(run: DungeonRunEntity): Long

    @Query("SELECT * FROM dungeon_run ORDER BY weekStartEpochDay DESC LIMIT :limit")
    fun observeDungeonRuns(limit: Int): Flow<List<DungeonRunEntity>>

    @Query("SELECT * FROM achievement")
    fun observeAchievements(): Flow<List<AchievementEntity>>

    @Query("SELECT id FROM achievement")
    suspend fun achievementIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAchievement(achievement: AchievementEntity): Long

    // Per-day signals for the dungeon, between two quest days inclusive.

    @Query("SELECT * FROM day_record WHERE epochDay BETWEEN :from AND :to")
    suspend fun dayRecords(from: Long, to: Long): List<DayRecordEntity>

    @Query("SELECT * FROM phone_day WHERE epochDay BETWEEN :from AND :to")
    suspend fun phoneDays(from: Long, to: Long): List<PhoneDayEntity>

    /** Days with a strength quest done (a scored gym session only completes when it passes) or a passing Gym session. */
    @Query(
        """
        SELECT c.epochDay FROM completion c JOIN quest_stat s ON s.questId = c.questId
        WHERE s.stat = 'strength' AND c.epochDay BETWEEN :from AND :to
        UNION
        SELECT epochDay FROM gym_session_score WHERE performance >= :passAt AND epochDay BETWEEN :from AND :to
        """,
    )
    suspend fun trainingDays(from: Long, to: Long, passAt: Double): List<Long>

    /** Days with a skill quiz, a mind game or logged work. */
    @Query(
        """
        SELECT epochDay FROM skill_event WHERE kind = '${SkillEventKind.QUIZ}' AND epochDay BETWEEN :from AND :to
        UNION SELECT epochDay FROM mind_score WHERE epochDay BETWEEN :from AND :to
        UNION SELECT epochDay FROM work_log WHERE epochDay BETWEEN :from AND :to
        """,
    )
    suspend fun studyDays(from: Long, to: Long): List<Long>

    /** Net XP dated [epochDay], leaving out dungeon rewards (those pay their own gold). */
    @Query("SELECT COALESCE(SUM(delta), 0) FROM xp_event WHERE epochDay = :epochDay AND reason != '${XpReason.DUNGEON}'")
    suspend fun netXpOn(epochDay: Long): Int

    @Query("SELECT COALESCE(SUM(delta), 0) FROM xp_event WHERE epochDay <= :epochDay")
    suspend fun netXpThrough(epochDay: Long): Long

    // Counts for achievements.

    @Query("SELECT COUNT(*) FROM day_record WHERE outcome = 'CLEAR'")
    suspend fun clearDayCount(): Int

    @Query("SELECT COUNT(*) FROM record_entry WHERE xpAwarded > 0")
    suspend fun personalBestCount(): Int

    @Query(
        """
        SELECT COUNT(*) FROM (
            SELECT c.epochDay FROM completion c JOIN quest_stat s ON s.questId = c.questId WHERE s.stat = 'strength'
            UNION SELECT epochDay FROM gym_session_score WHERE performance >= :passAt
        )
        """,
    )
    suspend fun trainingDayCount(passAt: Double): Int

    @Query("SELECT COUNT(*) FROM skill_event WHERE kind = '${SkillEventKind.QUIZ}'")
    suspend fun quizCount(): Int

    @Query("SELECT COUNT(DISTINCT conceptId) FROM skill_event WHERE kind = '${SkillEventKind.QUIZ}'")
    suspend fun conceptsQuizzedCount(): Int

    @Query("SELECT COUNT(*) FROM mind_score")
    suspend fun mindGameCount(): Int

    @Query("SELECT COUNT(*) FROM work_log")
    suspend fun workLogCount(): Int

    @Query("SELECT COUNT(*) FROM phone_day WHERE entertainmentMillis <= :maxMillis")
    suspend fun lowScrollDayCount(maxMillis: Long): Int

    @Query("SELECT COUNT(DISTINCT epochDay) FROM auto_check WHERE kind = 'PHONE_OFF' AND xp > 0")
    suspend fun phoneOffPassCount(): Int

    @Query("SELECT COUNT(*) FROM goal WHERE state = '${GoalState.REACHED}'")
    suspend fun goalsReachedCount(): Int

    @Query("SELECT COUNT(*) FROM dungeon_run WHERE rank IN ('S', 'A', 'B', 'C')")
    suspend fun dungeonsClearedCount(): Int

    @Query("SELECT COUNT(*) FROM dungeon_run WHERE rank = 'S'")
    suspend fun sRankCount(): Int

    @Query("SELECT COUNT(*) FROM purchase")
    suspend fun purchaseCount(): Int
}
