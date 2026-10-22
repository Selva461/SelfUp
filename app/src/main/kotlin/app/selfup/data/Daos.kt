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
