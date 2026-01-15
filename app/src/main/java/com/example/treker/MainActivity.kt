package com.example.treker

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainViewModelFactory(private val goalDao: GoalDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(goalDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class MainViewModel(val goalDao: GoalDao) : ViewModel() {
    val goals: Flow<List<Goal>> = goalDao.getAllGoals()
}

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        val app = application as App
        MainViewModelFactory(app.database.goalDao())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissions: MutableList<String> = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 0)

        setContent {
            MacOSGoalTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GoalScreen(viewModel.goalDao, viewModel.goals)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalScreen(goalDao: GoalDao, goalsFlow: Flow<List<Goal>>) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // получить App (чтобы использовать goalUpdateDao)
    val app = context.applicationContext as App

    // состояние для показа календаря (новое)
    var showCalendarScreen by remember { mutableStateOf(false) }

    var goalName by remember { mutableStateOf("") }
    var goalDescription by remember { mutableStateOf("") }
    var selectedDeadline by remember { mutableStateOf<Long?>(null) }
    var selectedImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedAudio by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    var showEditDialog by remember { mutableStateOf(false) }
    var editingGoal by remember { mutableStateOf<Goal?>(null) }
    var editGoalName by remember { mutableStateOf("") }
    var editGoalDescription by remember { mutableStateOf("") }
    var editGoalDeadline by remember { mutableStateOf<Long?>(null) }
    var editSelectedImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var editSelectedAudio by remember { mutableStateOf<String?>(null) }
    var editTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var showEditDatePicker by remember { mutableStateOf(false) }
    var showEditTimePicker by remember { mutableStateOf(false) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var goalToDelete by remember { mutableStateOf<Goal?>(null) }

    var showTagsDialog by remember { mutableStateOf(false) }
    var selectedGoalForTags by remember { mutableStateOf<Goal?>(null) }
    var newTag by remember { mutableStateOf("") }

    var isRecording by remember { mutableStateOf(false) }
    var mediaRecorder: MediaRecorder? by remember { mutableStateOf(null) }
    var audioFilePath: String? by remember { mutableStateOf(null) }

    var editIsRecording by remember { mutableStateOf(false) }
    var editMediaRecorder: MediaRecorder? by remember { mutableStateOf(null) }
    var editAudioFilePath: String? by remember { mutableStateOf(null) }

    // Новое: состояния для аудио по ID цели
    val isPlayingStates = remember { mutableStateMapOf<Int, Boolean>() }
    val mediaPlayers = remember { mutableStateMapOf<Int, MediaPlayer?>() }

    var tempSelectedDate by remember { mutableStateOf<Long?>(null) } // для DatePicker → TimePicker
    var editTempSelectedDate by remember { mutableStateOf<Long?>(null) } // для редактирования цели

    var showAddGoalSheet by remember { mutableStateOf(false) }

    val selectImagesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        val newImages = uris.take(3 - selectedImages.size).mapNotNull { uri ->
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val file = File(context.filesDir, "goal_image_${System.currentTimeMillis()}.jpg")
                inputStream?.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                file.absolutePath
            } catch (e: Exception) {
                android.util.Log.e("GoalScreen", "Error saving image", e)
                null
            }
        }
        selectedImages = (selectedImages + newImages).take(3)
    }

    val editImagesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        val newImages = uris.take(3 - editSelectedImages.size).mapNotNull { uri ->
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val file = File(context.filesDir, "goal_image_${System.currentTimeMillis()}.jpg")
                inputStream?.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                file.absolutePath
            } catch (e: Exception) {
                android.util.Log.e("GoalScreen", "Error saving image", e)
                null
            }
        }
        editSelectedImages = (editSelectedImages + newImages).take(3)
    }

    val goals by goalsFlow.collectAsState(initial = emptyList())

    LaunchedEffect(editingGoal) {
        editingGoal?.let { g ->
            editGoalName = g.name
            editGoalDescription = g.description
            editGoalDeadline = g.deadline
            editSelectedImages = g.imagePaths
            editSelectedAudio = g.audioPath
            editTags = g.tags
        }
    }

    LaunchedEffect(selectedGoalForTags) {
        selectedGoalForTags?.let { g ->
            // Можно загрузить теги, но они уже в g.tags
        }
    }

    fun startRecording(isEdit: Boolean = false) {
        try {
            val file = File(context.filesDir, "goal_audio_${System.currentTimeMillis()}.3gp")
            val path = file.absolutePath
            val recorder: MediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setOutputFile(path)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                prepare()
                start()
            }
            if (isEdit) {
                editMediaRecorder = recorder
                editAudioFilePath = path
                editIsRecording = true
            } else {
                mediaRecorder = recorder
                audioFilePath = path
                isRecording = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            errorMessage = "Ошибка записи: ${e.message}"
        }
    }

    fun stopRecording(isEdit: Boolean = false): String? {
        return try {
            val recorder = if (isEdit) editMediaRecorder else mediaRecorder
            recorder?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    android.util.Log.e("GoalScreen", "Stop recording error", e)
                }
                release()
            }
            if (isEdit) {
                editMediaRecorder = null
                editIsRecording = false
                editAudioFilePath
            } else {
                mediaRecorder = null
                isRecording = false
                audioFilePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
            errorMessage = "Ошибка остановки записи: ${e.message}"
            null
        }
    }

    fun playAudio(path: String, context: Context) {
        try {
            val uri = Uri.fromFile(File(path))
            val player = MediaPlayer.create(context, uri)
            player.setOnCompletionListener { player.release() }
            player.start()
        } catch (e: Exception) {
            e.printStackTrace()
            errorMessage = "Ошибка воспроизведения: ${e.message}"
            android.util.Log.e("GoalScreen", "Playback error", e)
        }
    }

    // Модифицированная функция: теперь управляет состоянием для целей
    fun manageGoalAudio(goalId: Int, context: Context, audioPath: String, onError: (String) -> Unit) {
        val currentIsPlaying = isPlayingStates.getOrPut(goalId) { false }
        val currentPlayer = mediaPlayers[goalId]
        if (currentIsPlaying) {
            currentPlayer?.pause()
            isPlayingStates[goalId] = false
        } else {
            currentPlayer?.let { player ->
                try {
                    player.start()
                    isPlayingStates[goalId] = true
                } catch (e: Exception) {
                    onError("Ошибка воспроизведения: ${e.message}")
                    android.util.Log.e("GoalScreen", "Playback error", e)
                }
            } ?: run {
                // Создаем новый плеер только если нет существующего
                try {
                    val uri = Uri.fromFile(File(audioPath))
                    val player = MediaPlayer.create(context, uri)
                    player?.setOnCompletionListener {
                        isPlayingStates[goalId] = false
                        mediaPlayers[goalId]?.release()
                        mediaPlayers.remove(goalId)
                    }
                    player?.start()
                    mediaPlayers[goalId] = player
                    isPlayingStates[goalId] = true
                } catch (e: Exception) {
                    onError("Ошибка создания плеера: ${e.message}")
                    android.util.Log.e("GoalScreen", "Player creation error", e)
                }
            }
        }
    }

    // Если пользователь хочет посмотреть календарь — показываем отдельный экран
    if (showCalendarScreen) {
        CalendarScreen(
            app = app,
            onBack = { showCalendarScreen = false }
        )
        return
    }

    // Оборачиваем основной UI в Scaffold, чтобы добавить TopAppBar с кнопкой "Календарь"
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Goal Tracker") },
                actions = {
                    IconButton(onClick = { showCalendarScreen = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Календарь")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddGoalSheet = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Goal")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ---------- ADD GOAL DATE PICKER ----------
            if (showDatePicker) {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = selectedDeadline ?: System.currentTimeMillis()
                )

                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            tempSelectedDate = datePickerState.selectedDateMillis
                            showDatePicker = false
                            showTimePicker = true
                        }) {
                            Text("OK")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text("Cancel")
                        }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            if (showTimePicker && tempSelectedDate != null) {
                val calendar = Calendar.getInstance().apply {
                    timeInMillis = tempSelectedDate!!
                }

                val timePickerState = rememberTimePickerState(
                    initialHour = calendar.get(Calendar.HOUR_OF_DAY),
                    initialMinute = calendar.get(Calendar.MINUTE)
                )

                TimePickerDialog(
                    onDismissRequest = {
                        showTimePicker = false
                        tempSelectedDate = null
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            calendar.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            calendar.set(Calendar.MINUTE, timePickerState.minute)
                            selectedDeadline = calendar.timeInMillis
                            showTimePicker = false
                            tempSelectedDate = null
                        }) {
                            Text("OK")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showTimePicker = false
                            tempSelectedDate = null
                        }) {
                            Text("Cancel")
                        }
                    }
                ) {
                    TimePicker(state = timePickerState)
                }
            }

            // ---------- EDIT GOAL DATE PICKER ----------
            if (showEditDatePicker) {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = editGoalDeadline ?: System.currentTimeMillis()
                )

                DatePickerDialog(
                    onDismissRequest = { showEditDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            editTempSelectedDate = datePickerState.selectedDateMillis
                            showEditDatePicker = false
                            showEditTimePicker = true
                        }) {
                            Text("OK")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEditDatePicker = false }) {
                            Text("Cancel")
                        }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            if (showEditTimePicker && editTempSelectedDate != null) {
                val calendar = Calendar.getInstance().apply {
                    timeInMillis = editTempSelectedDate!!
                }

                val timePickerState = rememberTimePickerState(
                    initialHour = calendar.get(Calendar.HOUR_OF_DAY),
                    initialMinute = calendar.get(Calendar.MINUTE)
                )

                TimePickerDialog(
                    onDismissRequest = {
                        showEditTimePicker = false
                        editTempSelectedDate = null
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            calendar.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            calendar.set(Calendar.MINUTE, timePickerState.minute)
                            editGoalDeadline = calendar.timeInMillis
                            showEditTimePicker = false
                            editTempSelectedDate = null
                        }) {
                            Text("OK")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showEditTimePicker = false
                            editTempSelectedDate = null
                        }) {
                            Text("Cancel")
                        }
                    }
                ) {
                    TimePicker(state = timePickerState)
                }
            }

            errorMessage?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }

            // --- остальной UI остаётся прежним (плеер аудио, удаление, список целей и т.д.)
            if (selectedAudio != null) {
                Text("Audio recorded")
                Button(
                    onClick = { selectedAudio?.let { playAudio(it, context) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Play Audio")
                }
                Button(
                    onClick = { selectedAudio = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Remove Audio")
                }
            }

            Text(
                text = "Your Goals",
                style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                textAlign = TextAlign.Center
            )

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(goals, key = { it.id }) { goal ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F7F7))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Правый верхний угол: Tags
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = "Tags: ${goal.tags.size}",
                                    modifier = Modifier
                                        .clickable {
                                            selectedGoalForTags = goal
                                            showTagsDialog = true
                                        }
                                        .padding(8.dp),
                                    style = TextStyle(fontWeight = FontWeight.Medium)
                                )
                            }
                            Text(text = goal.name, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = goal.description, fontSize = 14.sp, color = Color.Gray)
                            goal.deadline?.let {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(it))}",
                                    fontSize = 12.sp,
                                    color = Color.DarkGray
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            var progress by remember { mutableStateOf(goal.progress.toFloat() / 100f) }
                            val progressColor = Color(0xFF007AFF)

                            LinearProgressIndicator(
                                progress = progress,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .padding(vertical = 6.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                color = progressColor
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "Progress: ${(progress * 100).toInt()}%")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    SmallMacButton("-10") {
                                        val newProgress = (progress - 0.1f).coerceIn(0f, 1f)
                                        progress = newProgress
                                        coroutineScope.launch { goalDao.update(goal.copy(progress = (newProgress * 100).toInt())) }
                                    }
                                    SmallMacButton("+10") {
                                        val newProgress = (progress + 0.1f).coerceIn(0f, 1f)
                                        progress = newProgress
                                        coroutineScope.launch { goalDao.update(goal.copy(progress = (newProgress * 100).toInt())) }
                                    }
                                    Button(
                                        onClick = {
                                            goalToDelete = goal
                                            showDeleteDialog = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Delete", color = Color.White)
                                    }
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                IconButton(onClick = {
                                    val shareIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, "My goal: ${goal.name} - ${goal.description}, Progress: ${goal.progress}%")
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share goal"))
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = {
                                    editingGoal = goal
                                    showEditDialog = true
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                goal.audioPath?.let {
                                    val isPlaying = isPlayingStates.getOrPut(goal.id) { false }
                                    IconButton(onClick = {
                                        manageGoalAudio(goal.id, context, it) { msg ->
                                            errorMessage = msg
                                        }
                                    }) {
                                        Crossfade(targetState = isPlaying, label = "AudioIcon") { playing ->
                                            Icon(
                                                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (playing) "Pause Audio" else "Play Audio",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showEditDialog) {
            AlertDialog(
                onDismissRequest = {
                    showEditDialog = false
                    editingGoal = null
                },
                title = { Text("Edit Goal") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editGoalName,
                            onValueChange = { editGoalName = it },
                            label = { Text("Goal Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = editGoalDescription,
                            onValueChange = { editGoalDescription = it },
                            label = { Text("Description") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { showEditDatePicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                editGoalDeadline?.let {
                                    "${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(it))}"
                                } ?: "Select Deadline"
                            )
                        }
                        Text("Images (${editSelectedImages.size}/3):")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            editSelectedImages.forEachIndexed { index, path ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    val bitmap = BitmapFactory.decodeFile(path)?.asImageBitmap()
                                    bitmap?.let {
                                        Image(
                                            bitmap = it,
                                            contentDescription = "Goal image ${index + 1}",
                                            modifier = Modifier.size(64.dp)
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            editSelectedImages = editSelectedImages.toMutableList().apply { removeAt(index) }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text("Remove")
                                    }
                                }
                            }
                        }
                        Button(
                            onClick = { editImagesLauncher.launch("image/*") },
                            enabled = editSelectedImages.size < 3
                        ) {
                            Text("Add Images")
                        }
                        Button(
                            onClick = {
                                if (editIsRecording) {
                                    val path = stopRecording(true)
                                    if (path != null) {
                                        editSelectedAudio = path
                                    }
                                } else {
                                    startRecording(true)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (editIsRecording) "Stop Recording" else if (editSelectedAudio != null) "Re-record Audio" else "Record Audio")
                        }
                        if (editSelectedAudio != null) {
                            Text("Audio recorded")
                            Button(
                                onClick = { editSelectedAudio?.let { playAudio(it, context) } },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Play Audio")
                            }
                            Button(
                                onClick = { editSelectedAudio = null },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Remove Audio")
                            }
                        }
                        // Секция тегов в edit
                        Text("Tags:")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(editTags) { tag: String ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(tag, modifier = Modifier.padding(end = 4.dp))
                                    IconButton(onClick = {
                                        editTags = editTags.toMutableList().apply { remove(tag) }
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove tag")
                                    }
                                }
                            }
                        }
                        var showAddTagField by remember { mutableStateOf(false) }
                        if (showAddTagField) {
                            OutlinedTextField(
                                value = newTag,
                                onValueChange = { newTag = it },
                                label = { Text("New Tag") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row {
                                Button(onClick = {
                                    if (newTag.isNotBlank()) {
                                        editTags = editTags + newTag
                                        newTag = ""
                                        showAddTagField = false
                                    }
                                }) { Text("Add") }
                                Button(onClick = { showAddTagField = false }) { Text("Cancel") }
                            }
                        } else {
                            Button(onClick = { showAddTagField = true }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Text("Add Tag")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (editGoalName.isNotBlank()) {
                            coroutineScope.launch {
                                try {
                                    editingGoal?.let { goal ->
                                        // Очистка старого плеера перед обновлением (если аудио изменилось)
                                        mediaPlayers[goal.id]?.release()
                                        mediaPlayers.remove(goal.id)
                                        isPlayingStates.remove(goal.id)

                                        val updatedGoal = goal.copy(
                                            name = editGoalName,
                                            description = editGoalDescription,
                                            deadline = editGoalDeadline,
                                            imagePaths = editSelectedImages,
                                            audioPath = editSelectedAudio,
                                            tags = editTags
                                        )
                                        goalDao.update(updatedGoal)

                                        app.database.goalUpdateDao().insert(
                                            GoalUpdate(
                                                goalId = updatedGoal.id,
                                                date = getStartOfDayTimestamp(),
                                                type = GoalUpdateType.EDIT_GOAL
                                            )
                                        )

                                        // запись обновления при редактировании
                                        try {

                                        } catch (e: Exception) {
                                            android.util.Log.e("GoalScreen", "Error recording goal update after edit", e)
                                        }

                                        showEditDialog = false
                                        editingGoal = null
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("GoalScreen", "Error updating goal", e)
                                }
                            }
                        }
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showEditDialog = false
                        editingGoal = null
                    }) { Text("Cancel") }
                }
            )
        }

        // Диалог для тегов
        if (showTagsDialog && selectedGoalForTags != null) {
            val currentGoal = selectedGoalForTags
            AlertDialog(
                onDismissRequest = {
                    showTagsDialog = false
                    selectedGoalForTags = null
                },
                title = { Text("Tags for ${currentGoal?.name ?: ""}") },
                text = {
                    Column {
                        LazyColumn {
                            items(currentGoal?.tags ?: emptyList()) { tag: String ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(tag)
                                    IconButton(onClick = {
                                        val updatedTags = currentGoal?.tags?.toMutableList()?.apply { remove(tag) } ?: emptyList()
                                        // Немедленное обновление UI
                                        selectedGoalForTags = currentGoal?.copy(tags = updatedTags)
                                        // Фоновое обновление в БД
                                        coroutineScope.launch {
                                            currentGoal?.let { goal ->
                                                goalDao.update(goal.copy(tags = updatedTags))

                                                app.database.goalUpdateDao().insert(
                                                    GoalUpdate(
                                                        goalId = goal.id,
                                                        date = getStartOfDayTimestamp(),
                                                        type = GoalUpdateType.ADD_TAG
                                                    )
                                                )
                                            }
                                        }
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove tag")
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newTag,
                            onValueChange = { newTag = it },
                            label = { Text("New Tag") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                if (newTag.isNotBlank()) {
                                    val updatedTags = currentGoal?.tags?.plus(newTag) ?: listOf(newTag)
                                    // Немедленное обновление UI
                                    selectedGoalForTags = currentGoal?.copy(tags = updatedTags)
                                    // Фоновое обновление в БД
                                    coroutineScope.launch {
                                        currentGoal?.let { goal ->
                                            goalDao.update(goal.copy(tags = updatedTags))
                                        }
                                        newTag = ""  // Очистка поля после успешного добавления
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Add Tag")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showTagsDialog = false
                        selectedGoalForTags = null
                    }) { Text("Close") }
                }
            )
        }

        if (showDeleteDialog && goalToDelete != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Are you sure you want to delete this task?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val goal = goalToDelete
                            showDeleteDialog = false
                            if (goal != null) {
                                // Очистка плеера перед удалением
                                mediaPlayers[goal.id]?.release()
                                mediaPlayers.remove(goal.id)
                                isPlayingStates.remove(goal.id)

                                coroutineScope.launch {
                                    try {
                                        goalDao.delete(goal)
                                    } catch (e: Exception) {
                                        android.util.Log.e("GoalScreen", "Error deleting goal", e)
                                    } finally {
                                        goalToDelete = null
                                    }
                                }
                            }
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        if (showAddGoalSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAddGoalSheet = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    Text(
                        "New Goal",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )

                    OutlinedTextField(
                        value = goalName,
                        onValueChange = { goalName = it },
                        label = { Text("Goal Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = goalDescription,
                        onValueChange = { goalDescription = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            selectedDeadline?.let {
                                SimpleDateFormat(
                                    "dd.MM.yyyy HH:mm",
                                    Locale.getDefault()
                                ).format(Date(it))
                            } ?: "Select Deadline"
                        )
                    }

                    Button(
                        onClick = { selectImagesLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedImages.size < 3
                    ) {
                        Text("Add Images (${selectedImages.size}/3)")
                    }

                    Button(
                        onClick = {
                            if (isRecording) {
                                val path = stopRecording()
                                if (path != null) selectedAudio = path
                            } else startRecording()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isRecording) "Stop Recording" else "Record Audio")
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                if (goalName.isNotBlank()) {
                                    goalDao.insert(
                                        Goal(
                                            name = goalName,
                                            description = goalDescription,
                                            deadline = selectedDeadline,
                                            imagePaths = selectedImages,
                                            audioPath = selectedAudio,
                                            tags = emptyList()
                                        )
                                    )

                                    app.database.goalUpdateDao().insert(
                                        GoalUpdate(
                                            goalId = 0,
                                            date = getStartOfDayTimestamp(),
                                            type = GoalUpdateType.ADD_GOAL
                                        )
                                    )

                                    goalName = ""
                                    goalDescription = ""
                                    selectedDeadline = null
                                    selectedImages = emptyList()
                                    selectedAudio = null
                                    showAddGoalSheet = false
                                } else {
                                    errorMessage = "Goal name cannot be empty"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Add Goal")
                    }
                }
            }
        }
    }
}

// вспомогательная функция для отметок в календаре (начало дня)
fun getStartOfDayTimestamp(): Long {
    val now = java.util.Calendar.getInstance()
    now.set(java.util.Calendar.HOUR_OF_DAY, 0)
    now.set(java.util.Calendar.MINUTE, 0)
    now.set(java.util.Calendar.SECOND, 0)
    now.set(java.util.Calendar.MILLISECOND, 0)
    return now.timeInMillis
}

@Composable
fun SmallMacButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .width(48.dp)
            .height(36.dp),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5E5EA))
    ) {
        Text(text, fontSize = 12.sp, color = Color.Black)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        text = { content() }
    )
}

@Composable
fun MacOSGoalTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF007AFF),
            onPrimary = Color.White,
            background = Color(0xFFF2F2F7),
            onBackground = Color.Black,
            error = Color(0xFFFF3B30)
        ),
        typography = Typography(),
        content = content
    )
}
