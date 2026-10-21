package app.selfup.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        QuestEntity::class,
        QuestStatEntity::class,
        CompletionEntity::class,
        XpEventEntity::class,
        MetaEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class SelfUpDatabase : RoomDatabase() {
    abstract fun questDao(): QuestDao
    abstract fun completionDao(): CompletionDao
    abstract fun xpDao(): XpDao
    abstract fun metaDao(): MetaDao

    companion object {
        fun create(context: Context): SelfUpDatabase =
            Room.databaseBuilder(context, SelfUpDatabase::class.java, "selfup.db").build()
    }
}
