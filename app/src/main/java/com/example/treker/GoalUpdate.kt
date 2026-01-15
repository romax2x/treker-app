package com.example.treker

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "goal_updates")
data class GoalUpdate(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val goalId: Int,
    val date: Long,
    val type: GoalUpdateType
)