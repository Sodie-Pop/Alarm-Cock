package com.taskalarm.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorScreen(alarm: AlarmDraft, isNew: Boolean, readiness: JSONObject?, busy: Boolean, now: Long,
  onChange: (AlarmDraft) -> Unit, onBack: () -> Unit, onSave: () -> Unit, onTest: () -> Unit, onTestWake: () -> Unit,
  onAccess: (String) -> Unit, onSound: (Boolean) -> Unit, onEnroll: (String, String) -> Unit) {
  // "action|taskId" so the sheet survives a trip to the barcode scanner.
  var editing by rememberSaveable { mutableStateOf<String?>(null) }
  var adding by rememberSaveable { mutableStateOf<String?>(null) }
  var soundSheet by rememberSaveable { mutableStateOf(false) }
  var timeDialog by rememberSaveable { mutableStateOf(false) }
  val is24 = is24Hour()
  fun update(change: (AlarmDraft) -> AlarmDraft) { if (!busy) onChange(change(alarm)) }

  Scaffold(
    topBar = {
      TopAppBar(title = { Text(if (isNew) "New alarm" else "Edit alarm") },
        navigationIcon = { IconButton(onClick = onBack, enabled = !busy) { Ico(R.drawable.ic_arrow_back, "Back") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface))
    },
    bottomBar = { EditorActions(busy, onTest, onSave) },
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      SetupCard(readiness, onAccess)
      TimeHero(alarm, is24, now) { timeDialog = true }
      DayPicker(alarm.days) { days -> update { it.copy(days = days) } }
      OutlinedTextField(alarm.label, { value -> update { it.copy(label = value.take(120)) } }, Modifier.fillMaxWidth(),
        label = { Text("Alarm name") }, leadingIcon = { Ico(R.drawable.ic_label) }, singleLine = true,
        shape = MaterialTheme.shapes.medium)

      SectionHeader("To turn it off", "Finish these, in order, to dismiss the alarm.")
      SettingsCard {
        TaskRows(alarm.dismissTasks, "No tasks — one tap turns the alarm off.", busy, { editing = "dismiss|$it" }, { adding = "dismiss" })
      }

      SectionHeader("To snooze", "Snoozing restarts the alarm later; it never skips the turn-off tasks.")
      SettingsCard {
        Text("Snooze for", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(1, 5, 10, 15, 30, 60).forEach { minutes ->
            FilterChip(alarm.snoozeMinutes == minutes, { update { it.copy(snoozeMinutes = minutes) } }, label = { Text(minutesText(minutes)) })
          }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
        TaskRows(alarm.snoozeTasks, "No tasks — one tap snoozes.", busy, { editing = "snooze|$it" }, { adding = "snooze" })
      }

      SectionHeader("Sound & alerts")
      SettingsCard {
        NavRow("Sound", alarm.soundName + if (alarm.normalize && alarm.soundUri.isNotEmpty()) " · boosted" else "", R.drawable.ic_music_note, !busy) { soundSheet = true }
        FlashlightRow(alarm, readiness, busy, onAccess) { mode -> update { it.copy(flashlightMode = mode) } }
        SwitchRow("Protect alarm volume", "Stops the volume being turned down while it rings.", alarm.protectVolume, !busy,
          R.drawable.ic_volume_up) { value -> update { it.copy(protectVolume = value) } }
        SwitchRow("Mute while solving", "Shows a mute button during tasks. Leaving the app unmutes.", alarm.allowMuteWhileSolving, !busy,
          R.drawable.ic_volume_off) { value -> update { it.copy(allowMuteWhileSolving = value) } }
      }

      SectionHeader("Wake-up check")
      SettingsCard { WakeSettings(alarm, busy, ::update, { editing = it }, { adding = it }, onTestWake) }
      Spacer(Modifier.height(16.dp))
    }
  }

  editing?.split("|")?.let { (action, id) ->
    val task = alarm.tasks(action).find { it.id == id }
    if (task == null) { editing = null; return@let }
    ModalBottomSheet(onDismissRequest = { editing = null }) {
      TaskSheet(task,
        onChange = { changed -> update { it.withTasks(action, it.tasks(action).map { t -> if (t.id == id) changed else t }) } },
        onRemove = { editing = null; update { it.withTasks(action, it.tasks(action).filterNot { t -> t.id == id }) } },
        onEnroll = { onEnroll(action, id) }, onDone = { editing = null })
    }
  }
  adding?.let { action ->
    ModalBottomSheet(onDismissRequest = { adding = null }) {
      AddTaskSheet { kind ->
        val task = TaskDraft(kind = kind)
        adding = null
        update { it.withTasks(action, it.tasks(action) + task) }
        editing = "$action|${task.id}"
      }
    }
  }
  if (soundSheet) ModalBottomSheet(onDismissRequest = { soundSheet = false }) {
    SoundSheet(alarm, onPick = { custom -> soundSheet = false; onSound(custom) },
      onDefault = { update { it.copy(soundUri = "", soundName = "Default phone alarm", normalize = false, playbackUri = "") } },
      onNormalize = { value -> update { it.copy(normalize = value) } })
  }
  if (timeDialog) TimeDialog(alarm.hour.toIntOrNull() ?: 7, alarm.minute.toIntOrNull() ?: 0, is24, onDismiss = { timeDialog = false }) { hour, minute ->
    timeDialog = false
    update { it.copy(hour = hour.toString().padStart(2, '0'), minute = minute.toString().padStart(2, '0')) }
  }
}

@Composable
private fun TimeHero(alarm: AlarmDraft, is24: Boolean, now: Long, onClick: () -> Unit) {
  val clock = clockText(alarm.hour.toIntOrNull() ?: 0, alarm.minute.toIntOrNull() ?: 0, is24)
  val next = nextRing(alarm, now)
  Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClickLabel = "Change time", onClick = onClick)
    .padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Row(verticalAlignment = Alignment.Bottom) {
      Text(clock.time, style = clockStyle(88.sp), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.alignByBaseline())
      if (clock.period.isNotEmpty()) Text(clock.period, style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp).alignByBaseline())
    }
    if (next != null) Text("Rings in ${durationText(next - now)}", style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.primary)
  }
}

@Composable
private fun DayPicker(days: Set<Int>, onChange: (Set<Int>) -> Unit) {
  Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
      (1..7).forEach { day ->
        val selected = day in days
        Surface(shape = CircleShape, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
          contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(44.dp).semantics { contentDescription = dayName(day) }
            .toggleable(selected, role = Role.Checkbox) { onChange(if (it) days + day else days - day) }) {
          Box(contentAlignment = Alignment.Center) { Text(dayLetter(day), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
        }
      }
    }
    Text(if (days.isEmpty()) "Rings once" else "Repeats · ${repeatText(days)}", style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun TaskRows(tasks: List<TaskDraft>, emptyText: String, busy: Boolean, onEdit: (String) -> Unit, onAdd: () -> Unit) {
  if (tasks.isEmpty()) Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
  tasks.forEachIndexed { index, task ->
    Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { onEdit(task.id) }.padding(horizontal = 16.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      IconBadge(taskIcon(task.kind))
      Column(Modifier.weight(1f)) {
        Text("${index + 1}. ${taskTitle(task.kind)}", style = MaterialTheme.typography.bodyLarge)
        Text(taskDetail(task), style = MaterialTheme.typography.bodyMedium,
          color = if (task.needsBarcode()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Ico(R.drawable.ic_edit, "Edit task", tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 20.dp)
    }
  }
  if (tasks.size < 10) TextButton(onClick = onAdd, enabled = !busy, modifier = Modifier.padding(horizontal = 8.dp)) {
    Ico(R.drawable.ic_add, size = 20.dp); Spacer(Modifier.width(8.dp)); Text("Add task")
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlashlightRow(alarm: AlarmDraft, readiness: JSONObject?, busy: Boolean, onAccess: (String) -> Unit, onMode: (String) -> Unit) {
  val available = readiness?.optBoolean("flashlightAvailable", true) != false
  Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      Ico(R.drawable.ic_flashlight_on, tint = MaterialTheme.colorScheme.onSurfaceVariant)
      Text("Flashlight", style = MaterialTheme.typography.bodyLarge)
    }
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
      val modes = listOf("off" to "Off", "steady" to "Steady", "flashing" to "Flashing")
      modes.forEachIndexed { index, (mode, label) ->
        SegmentedButton(selected = alarm.flashlightMode == mode, onClick = { onMode(mode) },
          shape = SegmentedButtonDefaults.itemShape(index, modes.size), enabled = !busy && (mode == "off" || available)) { Text(label) }
      }
    }
    if (!available) Notice("This phone has no flashlight.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (alarm.flashlightMode != "off" && readiness?.optBoolean("cameraPermission") != true) {
      Notice("Android needs camera access to control the flashlight.")
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { onAccess("camera") }, enabled = !busy) { Text("Allow camera") }
        TextButton(onClick = { onAccess("appSettings") }, enabled = !busy) { Text("App settings") }
      }
    }
  }
}

@Composable
private fun WakeSettings(alarm: AlarmDraft, busy: Boolean, update: ((AlarmDraft) -> AlarmDraft) -> Unit,
  onEdit: (String) -> Unit, onAdd: (String) -> Unit, onTestWake: () -> Unit) {
  SwitchRow("Make sure I'm up", "After you dismiss, check in later. No answer and it rings again.", alarm.wakeEnabled, !busy,
    R.drawable.ic_wb_sunny) { value -> update { it.copy(wakeEnabled = value) } }
  if (!alarm.wakeEnabled) return
  HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
  Stepper("Ask after", alarm.wakeDelayMinutes, minutesText(alarm.wakeDelayMinutes), alarm.wakeDelayMinutes > 1, alarm.wakeDelayMinutes < 1440,
    { update { it.copy(wakeDelayMinutes = nextMinutes(it.wakeDelayMinutes, false)) } },
    { update { it.copy(wakeDelayMinutes = nextMinutes(it.wakeDelayMinutes, true)) } })
  Stepper("Time to answer", alarm.wakeResponseMinutes, minutesText(alarm.wakeResponseMinutes), alarm.wakeResponseMinutes > 1, alarm.wakeResponseMinutes < 1440,
    { update { it.copy(wakeResponseMinutes = nextMinutes(it.wakeResponseMinutes, false)) } },
    { update { it.copy(wakeResponseMinutes = nextMinutes(it.wakeResponseMinutes, true)) } })
  Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("Confirm by", style = MaterialTheme.typography.bodyLarge)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
      SegmentedButton(alarm.wakeMode == "yes_no", { update { it.copy(wakeMode = "yes_no") } }, SegmentedButtonDefaults.itemShape(0, 2), enabled = !busy) { Text("Tapping Yes") }
      SegmentedButton(alarm.wakeMode == "tasks", { update { it.copy(wakeMode = "tasks") } }, SegmentedButtonDefaults.itemShape(1, 2), enabled = !busy) { Text("Doing tasks") }
    }
  }
  if (alarm.wakeMode == "tasks") TaskRows(alarm.wakeTasks, "Add at least one task.", busy, { onEdit("wake|$it") }, { onAdd("wake") })
  SwitchRow("Same tasks if it rings again", "Off lets you choose different turn-off tasks for the repeat.", alarm.wakeReuseDismissTasks, !busy) { value ->
    update { it.copy(wakeReuseDismissTasks = value) }
  }
  if (!alarm.wakeReuseDismissTasks) Text("Tasks for the repeat alarm", style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
  if (!alarm.wakeReuseDismissTasks) TaskRows(alarm.wakeRetryTasks, "No tasks — one tap turns the repeat off.", busy, { onEdit("retry|$it") }, { onAdd("retry") })
  TextButton(onClick = onTestWake, enabled = !busy, modifier = Modifier.padding(horizontal = 8.dp)) {
    Ico(R.drawable.ic_play_arrow, size = 20.dp); Spacer(Modifier.width(8.dp)); Text("Try a wake-up check now")
  }
}

@Composable
private fun EditorActions(busy: Boolean, onTest: () -> Unit, onSave: () -> Unit) {
  Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
    Column(Modifier.navigationBarsPadding()) {
      if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
      Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onTest, enabled = !busy, modifier = Modifier.heightIn(min = 52.dp)) {
          Ico(R.drawable.ic_play_arrow, size = 20.dp); Spacer(Modifier.width(6.dp)); Text("Test")
        }
        Button(onClick = onSave, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
          Text(if (busy) "Saving…" else "Save alarm")
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskSheet(task: TaskDraft, onChange: (TaskDraft) -> Unit, onRemove: () -> Unit, onEnroll: () -> Unit, onDone: () -> Unit) {
  Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp).navigationBarsPadding(),
    verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      IconBadge(taskIcon(task.kind), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, 48.dp)
      Column {
        Text(taskTitle(task.kind), style = MaterialTheme.typography.titleLarge)
        Text(taskBlurb(task.kind), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    if (task.kind != "barcode") {
      Text("Difficulty", style = MaterialTheme.typography.titleSmall)
      SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        val levels = listOf("easy", "medium", "hard")
        levels.forEachIndexed { index, level ->
          SegmentedButton(task.difficulty == level, { onChange(task.copy(difficulty = level)) }, SegmentedButtonDefaults.itemShape(index, levels.size)) {
            Text(level.replaceFirstChar(Char::uppercase))
          }
        }
      }
      Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Example", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(task.example, fontFamily = FontFamily.Monospace, fontSize = 22.sp, color = MaterialTheme.colorScheme.secondary)
          Text(if (task.kind == "text") "Case and symbols must match. New text every round." else "New numbers every round.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    } else {
      Text("Which barcode?", style = MaterialTheme.typography.titleSmall)
      SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(task.barcodeMode == "any", { onChange(task.copy(barcodeMode = "any")) }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Any") }
        SegmentedButton(task.barcodeMode == "specific", { onChange(task.copy(barcodeMode = "specific")) }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("A specific one") }
      }
      if (task.barcodeMode == "specific") {
        if (task.barcode.isEmpty()) Notice("Scan the item you'll need to get up for — like something in the kitchen.")
        else Notice("Saved: ${task.barcode}", R.drawable.ic_check_circle, MaterialTheme.colorScheme.tertiary)
        FilledTonalButton(onClick = onEnroll, modifier = Modifier.fillMaxWidth()) {
          Ico(R.drawable.ic_qr_code_scanner, size = 20.dp); Spacer(Modifier.width(8.dp))
          Text(if (task.barcode.isEmpty()) "Scan barcode to save" else "Scan a different barcode")
        }
      } else Text("Any readable product barcode or QR code counts.", style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Stepper("Rounds", task.rounds, task.rounds.toString(), task.rounds > 1, task.rounds < 10,
      { onChange(task.copy(rounds = task.rounds - 1)) }, { onChange(task.copy(rounds = task.rounds + 1)) }, horizontalPadding = 0.dp)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      TextButton(onClick = onRemove, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
        Ico(R.drawable.ic_delete, size = 20.dp); Spacer(Modifier.width(6.dp)); Text("Remove")
      }
      Spacer(Modifier.weight(1f))
      Button(onClick = onDone) { Text("Done") }
    }
  }
}

@Composable
internal fun AddTaskSheet(onPick: (String) -> Unit) {
  Column(Modifier.fillMaxWidth().padding(bottom = 16.dp).navigationBarsPadding()) {
    Text("Add a task", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp))
    listOf("math", "text", "barcode").forEach { kind ->
      Row(Modifier.fillMaxWidth().clickable { onPick(kind) }.padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        IconBadge(taskIcon(kind), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, 48.dp)
        Column(Modifier.weight(1f)) {
          Text(taskTitle(kind), style = MaterialTheme.typography.titleMedium)
          Text(taskBlurb(kind), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
  }
}

@Composable
internal fun SoundSheet(alarm: AlarmDraft, onPick: (Boolean) -> Unit, onDefault: () -> Unit, onNormalize: (Boolean) -> Unit) {
  Column(Modifier.fillMaxWidth().padding(bottom = 16.dp).navigationBarsPadding()) {
    Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text("Alarm sound", style = MaterialTheme.typography.titleLarge)
      Text(alarm.soundName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
    Spacer(Modifier.height(8.dp))
    NavRow("Phone alarm sounds", "Pick from the sounds on your phone", R.drawable.ic_alarm_clock) { onPick(false) }
    NavRow("Import an audio file", "MP3, M4A, WAV, Ogg… copied into the app", R.drawable.ic_music_note) { onPick(true) }
    if (alarm.soundUri.isNotEmpty()) {
      NavRow("Use the phone's default", null, R.drawable.ic_close, trailing = {}, onClick = onDefault)
      SwitchRow("Boost quiet sounds", "Raises the file's peak level without clipping. Up to 5 minutes of audio.", alarm.normalize,
        icon = R.drawable.ic_volume_up, onChange = onNormalize)
    }
    Text("Your phone's alarm volume still sets how loud it plays.", style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeDialog(hour: Int, minute: Int, is24: Boolean, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
  val state = rememberTimePickerState(hour, minute, is24)
  var typing by rememberSaveable { mutableStateOf(false) }
  BasicAlertDialog(onDismissRequest = onDismiss) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 6.dp) {
      Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Alarm time", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp))
        if (typing) TimeInput(state) else TimePicker(state)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = { typing = !typing }) {
            Ico(if (typing) R.drawable.ic_timer else R.drawable.ic_keyboard, if (typing) "Use clock dial" else "Type the time")
          }
          Spacer(Modifier.weight(1f))
          TextButton(onClick = onDismiss) { Text("Cancel") }
          TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK") }
        }
      }
    }
  }
}
