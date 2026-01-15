package com.example.treker

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalUpdateDao {

    @Insert
    suspend fun insert(update: GoalUpdate)

    @Query("SELECT * FROM goal_updates")
    fun getAllUpdates(): Flow<List<GoalUpdate>>
}
