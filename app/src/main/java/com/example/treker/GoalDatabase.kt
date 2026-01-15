package com.example.treker

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Insert
    suspend fun insert(goal: Goal)

    @Update
    suspend fun update(goal: Goal)

    @Delete
    suspend fun delete(goal: Goal)

    @Query("SELECT * FROM goals")
    fun getAllGoals(): Flow<List<Goal>>
}

@Database(
    entities = [Goal::class, GoalUpdate::class],
    version = 7, // увеличь версию
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class GoalDatabase : RoomDatabase() {
    abstract fun goalDao(): GoalDao
    abstract fun goalUpdateDao(): GoalUpdateDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE goals ADD COLUMN deadline INTEGER")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE goals ADD COLUMN imagePaths TEXT NOT NULL DEFAULT '[]'")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE goals ADD COLUMN audioPath TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE goals ADD COLUMN tags TEXT NOT NULL DEFAULT '[]'")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS goal_updates (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                goalId INTEGER NOT NULL,
                date INTEGER NOT NULL,
                type TEXT NOT NULL
            )
            """.trimIndent()
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE goal_updates ADD COLUMN type TEXT NOT NULL DEFAULT 'ADD_GOAL'"
                )
            }
        }
    }
}
