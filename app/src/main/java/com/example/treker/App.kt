package com.example.treker

import android.app.Application
import android.util.Log
import androidx.room.Room

class App : Application() {
    lateinit var database: GoalDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        try {
            database = Room.databaseBuilder(
                applicationContext,
                GoalDatabase::class.java, "goal_database"
            )
                .addMigrations(GoalDatabase.MIGRATION_1_2, GoalDatabase.MIGRATION_2_3, GoalDatabase.MIGRATION_3_4, GoalDatabase.MIGRATION_4_5, GoalDatabase.MIGRATION_5_6, GoalDatabase.MIGRATION_6_7)
                .build()
            Log.d("App", "Database initialized successfully")
        } catch (e: Exception) {
            Log.e("App", "Failed to initialize database", e)
            throw e
        }
    }
}