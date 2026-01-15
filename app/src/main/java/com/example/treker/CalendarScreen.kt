package com.example.treker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    app: App,
    onBack: () -> Unit
) {
    val today = remember { startOfDay(System.currentTimeMillis()) }
    var selectedDate by remember { mutableStateOf(today) }

    val updates by app.database.goalUpdateDao()
        .getAllUpdates()
        .collectAsState(initial = emptyList())

    val updatesByDay = remember(updates) {
        updates.groupBy { it.date }
    }

    val selectedUpdates = updatesByDay[selectedDate].orEmpty()

    val calendar = Calendar.getInstance().apply {
        timeInMillis = selectedDate
    }

    val day = calendar.get(Calendar.DAY_OF_MONTH)
    val month = calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())
    val year = calendar.get(Calendar.YEAR)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Большая дата
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = day.toString(),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$month $year",
                    fontSize = 18.sp,
                    color = Color.Gray
                )
            }

            // Календарь
            CalendarMonthView(
                currentDate = selectedDate,
                today = today,
                updatesByDay = updatesByDay,
                onDateClick = { selectedDate = it }
            )

            Text(
                text = "Activity",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )

            if (selectedUpdates.isEmpty()) {
                Text(
                    text = "There was nothing that day",
                    color = Color.Gray
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(selectedUpdates) {
                        CalendarEventItem(it)
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarMonthView(
    currentDate: Long,
    today: Long,
    updatesByDay: Map<Long, List<GoalUpdate>>,
    onDateClick: (Long) -> Unit
) {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = currentDate
        set(Calendar.DAY_OF_MONTH, 1)
    }

    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOffset = calendar.get(Calendar.DAY_OF_WEEK) - 1

    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(4.dp)
    ) {
        items(firstDayOffset) {
            Spacer(modifier = Modifier.size(40.dp))
        }

        items(daysInMonth) { index ->
            val day = index + 1
            val dayCal = calendar.clone() as Calendar
            dayCal.set(Calendar.DAY_OF_MONTH, day)

            val date = startOfDay(dayCal.timeInMillis)

            val isToday = date == today
            val isSelected = date == currentDate
            val hasUpdates = updatesByDay.containsKey(date)

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isSelected -> Color(0xFF1976D2)
                            isToday -> Color(0x331976D2)
                            hasUpdates -> Color(0x3300C853)
                            else -> Color.Transparent
                        }
                    )
                    .clickable { onDateClick(date) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = day.toString(),
                    color = if (isSelected) Color.White else Color.Black
                )
            }
        }
    }
}

@Composable
private fun CalendarEventItem(update: GoalUpdate) {
    val text = when (update.type) {
        GoalUpdateType.ADD_GOAL -> "Goal added"
        GoalUpdateType.EDIT_GOAL -> "Goal updated"
        GoalUpdateType.ADD_TAG -> "Tag added"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp)
        )
    }
}

private fun startOfDay(time: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = time
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
