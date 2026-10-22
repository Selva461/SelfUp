package app.selfup.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        QuestEntity::class,
        QuestStatEntity::class,
        CompletionEntity::class,
        XpEventEntity::class,
        MetaEntity::class,
        MealLogEntity::class,
        RecordEntryEntity::class,
        SkillEventEntity::class,
        MindScoreEntity::class,
        GoalEntity::class,
        DayRecordEntity::class,
        AppUsageDayEntity::class,
        PhoneDayEntity::class,
        FocusEventEntity::class,
    ],
    version = SelfUpDatabase.VERSION,
    exportSchema = true,
)
abstract class SelfUpDatabase : RoomDatabase() {
    abstract fun questDao(): QuestDao
    abstract fun completionDao(): CompletionDao
    abstract fun xpDao(): XpDao
    abstract fun metaDao(): MetaDao
    abstract fun healthDao(): HealthDao
    abstract fun recordDao(): RecordDao
    abstract fun skillDao(): SkillDao
    abstract fun mindDao(): MindDao
    abstract fun goalDao(): GoalDao
    abstract fun trackingDao(): TrackingDao

    companion object {
        const val VERSION = 9
        const val NAME = "selfup.db"

        fun create(context: Context): SelfUpDatabase =
            Room.databaseBuilder(context, SelfUpDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()

        /** v1 shipped with the first APK, so every schema change from here on needs a migration. */
        val MIGRATIONS: Array<Migration> = arrayOf(
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `record_entry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`kind` TEXT NOT NULL, `atMillis` INTEGER NOT NULL, `weightKg` REAL, `reps` INTEGER, " +
                            "`seconds` INTEGER, `distanceKm` REAL, `xpAwarded` INTEGER NOT NULL)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_record_entry_kind_atMillis` ON `record_entry` (`kind`, `atMillis`)",
                    )
                }
            },
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `skill_event` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`conceptId` TEXT NOT NULL, `kind` TEXT NOT NULL, `epochDay` INTEGER NOT NULL, " +
                            "`atMillis` INTEGER NOT NULL, `correct` INTEGER, `total` INTEGER)",
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_skill_event_conceptId` ON `skill_event` (`conceptId`)")
                }
            },
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `mind_score` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`game` TEXT NOT NULL, `score` REAL NOT NULL, `epochDay` INTEGER NOT NULL, " +
                            "`atMillis` INTEGER NOT NULL, `xpAwarded` INTEGER NOT NULL)",
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_mind_score_game` ON `mind_score` (`game`)")
                }
            },
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `goal` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`measure` TEXT NOT NULL, `target` REAL NOT NULL, `baseline` REAL, `startEpochDay` INTEGER NOT NULL, " +
                            "`deadlineEpochDay` INTEGER NOT NULL, `state` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL)",
                    )
                }
            },
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `goal` ADD COLUMN `plan` TEXT")
                }
            },
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `record_entry` ADD COLUMN `source` TEXT")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_record_entry_source` ON `record_entry` (`source`)")
                }
            },
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("DROP TABLE IF EXISTS `gas_log`")
                }
            },
            object : Migration(8, 9) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `day_record` (`epochDay` INTEGER NOT NULL, `due` INTEGER NOT NULL, " +
                            "`missed` INTEGER NOT NULL, `outcome` TEXT NOT NULL, `streakAfter` INTEGER NOT NULL, " +
                            "`shieldsAfter` INTEGER NOT NULL, `xpLost` INTEGER NOT NULL, `closedAtMillis` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`epochDay`))",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `app_usage_day` (`epochDay` INTEGER NOT NULL, `pkg` TEXT NOT NULL, " +
                            "`foregroundMillis` INTEGER NOT NULL, `opens` INTEGER NOT NULL, `entertainment` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`epochDay`, `pkg`))",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `phone_day` (`epochDay` INTEGER NOT NULL, `firstUseMillis` INTEGER, " +
                            "`lastUseMillis` INTEGER, `totalMillis` INTEGER NOT NULL, `entertainmentMillis` INTEGER NOT NULL, " +
                            "`opens` INTEGER NOT NULL, `lateOpens` INTEGER NOT NULL, PRIMARY KEY(`epochDay`))",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `focus_event` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`atMillis` INTEGER NOT NULL, `epochDay` INTEGER NOT NULL, `kind` TEXT NOT NULL, `pkg` TEXT, " +
                            "`questId` INTEGER, `minutesUsed` INTEGER)",
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_focus_event_epochDay` ON `focus_event` (`epochDay`)")
                }
            },
        )
    }
}
