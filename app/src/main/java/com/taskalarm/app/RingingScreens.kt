package com.taskalarm.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taskalarm.engine.AlarmEngine
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun RingingScreen(active: JSONObject, busy: Boolean, answerError: String?, now: Long,
  onAction: (String) -> Unit, onSubmit: (String) -> Unit, onScan: () -> Unit, onMute: (Boolean) -> Unit, onAnswerEdit: () -> Unit,
  onComplete: () -> Unit = {}, onFallback: () -> Unit = {}) {
  val alarm = AlarmDraft.from(active.getJSONObject("alarm"))
  val action = if (active.isNull("action")) null else active.getString("action")
  val test = active.optBoolean("test")
  val muted = active.optBoolean("muted")
  val snoozesLeft = AlarmEngine.snoozesLeft(active.getJSONObject("alarm"))
  val canSnooze = snoozesLeft != 0
  val snoozeTitle = if (test) "Snooze (ends test)" else "Snooze ${minutesText(alarm.snoozeMinutes)}"
  TaskAlarmTheme(dark = true) {
    Box(Modifier.fillMaxSize().background(NightSky)) {
      val topRow = @Composable {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
          if (test) InfoPill(R.drawable.ic_play_arrow, "Test alarm", MaterialTheme.colorScheme.onSecondaryContainer, MaterialTheme.colorScheme.secondaryContainer)
          Spacer(Modifier.weight(1f))
          if (action != null && alarm.allowMuteWhileSolving) FilledTonalIconButton(onClick = { onMute(!muted) }, enabled = !busy) {
            Ico(if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume_up, if (muted) "Unmute alarm" else "Mute while solving")
          }
        }
      }
      val notices = @Composable {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
          if (active.optBoolean("escalated")) Notice("Escalated · full volume" + active.optString("escalationToneName").let { if (it.isEmpty()) "" else " · $it" },
            R.drawable.ic_campaign, MaterialTheme.colorScheme.primary)
          if (active.optBoolean("volumeGuardBlocked")) Notice("Android blocked the volume guard. Check your alarm volume.")
          if (active.optBoolean("flashlightBlocked")) Notice("Flashlight unavailable — another app may be using the camera. Your alarm continues.")
          if (test) Notice("To stop a test, finish its tasks.", R.drawable.ic_play_arrow, MaterialTheme.colorScheme.onSurfaceVariant)
          if (!canSnooze && alarm.allowSnooze) Notice("No snoozes left — complete the dismiss tasks.", R.drawable.ic_info, MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      if (action == null && alarm.movingDismiss) {
        Box(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp)) {
          Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            topRow()
            Spacer(Modifier.height(32.dp))
            LiveClock(now, 104)
            Text(alarm.label, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            notices()
          }
          MovingDismiss(!busy, if (canSnooze) snoozeTitle else null, { onAction("dismiss") }, { onAction("snooze") })
        }
        return@Box
      }
      if (action == null) {
        // Choosing: big clock up top, actions anchored at the bottom within thumb reach.
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
          topRow()
          Spacer(Modifier.weight(0.5f))
          LiveClock(now, 104)
          Text(alarm.label, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
          Spacer(Modifier.weight(1f))
          notices()
          Spacer(Modifier.height(16.dp))
          BigChoice("Dismiss", tasksLine(alarm.dismissTasks, "Turn off now"), R.drawable.ic_alarm_clock, primary = true, enabled = !busy) { onAction("dismiss") }
          Spacer(Modifier.height(12.dp))
          if (canSnooze) BigChoice(snoozeTitle, tasksLine(alarm.snoozeTasks, "Snooze now") + if (snoozesLeft > 0) " · $snoozesLeft left" else "",
            R.drawable.ic_snooze, primary = false, enabled = !busy) { onAction("snooze") }
          Spacer(Modifier.height(24.dp))
        }
        return@Box
      }
      Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        topRow()
        LiveClock(now, 64)
        Text(alarm.label, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        run {
          Spacer(Modifier.height(24.dp))
          ChallengePanel(active, alarm.tasks(action), if (action == "dismiss") "Dismiss" else "Snooze", busy, answerError, onSubmit, onScan, onAnswerEdit,
            onComplete, onFallback)
          if (muted) Text("Muted while you solve. Leaving the app unmutes.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
          if (action == "snooze" || canSnooze) {
            TextButton(onClick = { onAction(if (action == "dismiss") "snooze" else "dismiss") }, enabled = !busy, modifier = Modifier.padding(top = 8.dp)) {
              Text(if (action == "dismiss") "Snooze instead" else "Dismiss instead")
            }
            Text("Switching starts that action's tasks from the beginning.", style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
          }
        }
        Spacer(Modifier.height(20.dp))
        notices()
        Spacer(Modifier.height(24.dp))
      }
    }
  }
}

/**
 * Small Dismiss button that jumps to a random spot every 5 seconds, avoiding the fixed, smaller Snooze in the corner.
 * Tapping it only starts the dismiss tasks; it never skips them.
 */
@Composable
private fun MovingDismiss(enabled: Boolean, snoozeTitle: String?, onDismiss: () -> Unit, onSnooze: () -> Unit) {
  BoxWithConstraints(Modifier.fillMaxSize().padding(bottom = 24.dp)) {
    val buttonW = 120.dp; val buttonH = 52.dp; val snoozeW = 168.dp; val snoozeH = 44.dp
    val spanX = (maxWidth - buttonW).coerceAtLeast(0.dp); val spanY = (maxHeight - buttonH).coerceAtLeast(0.dp)
    fun pick(): Pair<Float, Float> {
      repeat(20) {
        val x = kotlin.random.Random.nextFloat(); val y = 0.25f + kotlin.random.Random.nextFloat() * 0.75f
        val overlapsSnooze = snoozeTitle != null && spanX * x + buttonW > maxWidth - snoozeW - 12.dp && spanY * y + buttonH > maxHeight - snoozeH - 12.dp
        if (!overlapsSnooze) return x to y
      }
      return 0.1f to 0.5f
    }
    var spot by remember { mutableStateOf(0.5f to 0.6f) }
    LaunchedEffect(maxWidth, maxHeight) { while (true) { kotlinx.coroutines.delay(5_000); spot = pick() } }
    Button(onClick = onDismiss, enabled = enabled, modifier = Modifier.offset(x = spanX * spot.first, y = spanY * spot.second).size(buttonW, buttonH)) {
      Text("Dismiss", style = MaterialTheme.typography.titleMedium)
    }
    if (snoozeTitle != null) FilledTonalButton(onClick = onSnooze, enabled = enabled,
      modifier = Modifier.align(Alignment.BottomEnd).widthIn(max = snoozeW).heightIn(min = snoozeH)) {
      Ico(R.drawable.ic_snooze, size = 18.dp); Spacer(Modifier.width(6.dp)); Text(snoozeTitle, maxLines = 1)
    }
  }
}

private fun tasksLine(tasks: List<TaskDraft>, none: String) = when (tasks.size) {
  0 -> none
  1 -> "1 task · ${taskPill(tasks[0])}"
  else -> "${tasks.size} tasks · " + tasks.joinToString(", ") { taskPill(it) }
}

@Composable
private fun LiveClock(now: Long, size: Int) {
  val clock = clockText(now, is24Hour())
  Row(verticalAlignment = Alignment.Bottom) {
    Text(clock.time, style = clockStyle(size.sp), color = Color.White, modifier = Modifier.alignByBaseline())
    if (clock.period.isNotEmpty()) Text(clock.period, style = MaterialTheme.typography.headlineSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp).alignByBaseline())
  }
  Text(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun BigChoice(title: String, subtitle: String, icon: Int, primary: Boolean, enabled: Boolean, onClick: () -> Unit) {
  val colors = if (primary) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f), contentColor = MaterialTheme.colorScheme.onSurface)
  Button(onClick = onClick, enabled = enabled, colors = colors, shape = MaterialTheme.shapes.large,
    modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)) {
    Ico(icon, size = 28.dp)
    Spacer(Modifier.width(16.dp))
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleLarge)
      Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = LocalContentColor.current.copy(alpha = 0.8f))
    }
  }
}

/** Shared task UI for ringing alarms and wake-up checks. */
@Composable
internal fun ChallengePanel(session: JSONObject, tasks: List<TaskDraft>, actionLabel: String, busy: Boolean, answerError: String?,
  onSubmit: (String) -> Unit, onScan: () -> Unit, onAnswerEdit: () -> Unit, onComplete: () -> Unit = {}, onFallback: () -> Unit = {}) {
  val kind = session.optString("kind")
  val index = session.getInt("taskIndex").coerceIn(0, (tasks.size - 1).coerceAtLeast(0))
  val task = tasks.getOrNull(index) ?: return
  val round = session.getInt("round")
  var answer by remember(session.optString("challengeId")) { mutableStateOf("") }
  val total = tasks.sumOf { it.rounds }
  val done = tasks.take(index).sumOf { it.rounds } + round - 1
  fun send() { if (!busy && answer.isNotEmpty()) onSubmit(answer) }
  val challenge = session.optString("challengeId")
  var unavailable by remember(challenge) { mutableStateOf<String?>(null) }
  Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f)) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$actionLabel · task ${index + 1} of ${tasks.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.weight(1f))
        if (task.rounds > 1) Text("Round $round of ${task.rounds}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Progress(done, total)
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconBadge(taskIcon(kind), size = 36.dp)
        Text(when (kind) { "text" -> "Type this exactly, including capitals and symbols."; "math" -> "Solve to keep going."
          "memory" -> "Watch the tiles, then tap them in the same order."
          "barcode" -> if (task.barcodeMode == "specific") "Get up and scan your saved barcode." else "Get up and scan any barcode or QR code."
          else -> session.optString("prompt") + "." },
          style = MaterialTheme.typography.bodyLarge)
      }
      if (unavailable != null) {
        Notice(unavailable!!)
        FilledTonalButton(onClick = onFallback, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Use ${fallbackName(task.fallbackKind)} instead") }
      }
      when (kind) {
        "memory" -> { if (answerError != null) Notice(answerError); MemoryBoard(session.optString("sequence"), challenge, !busy, onSubmit) }
        "breakout" -> BreakoutBoard(task, challenge, !busy, onComplete)
        "maze" -> MazeBoard(task, challenge, !busy, onComplete) { unavailable = it }
        "walk" -> WalkBoard(task, challenge, !busy, onComplete) { unavailable = it }
        "light" -> LightBoard(task, challenge, !busy, onComplete) { unavailable = it }
      }
      if (kind in listOf("memory", "breakout", "maze", "walk", "light")) return@Column
      if (kind != "barcode") Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(session.optString("prompt"), fontFamily = FontFamily.Monospace, fontSize = if (session.optString("prompt").length > 12) 24.sp else 30.sp,
          color = MaterialTheme.colorScheme.secondary, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp))
      }
      if (kind == "barcode") {
        if (answerError != null) Notice(answerError)
        Button(onClick = onScan, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
          Ico(R.drawable.ic_qr_code_scanner); Spacer(Modifier.width(10.dp)); Text("Open scanner", style = MaterialTheme.typography.titleMedium)
        }
      } else {
        OutlinedTextField(answer, { answer = it; onAnswerEdit() }, Modifier.fillMaxWidth(), singleLine = true,
          label = { Text("Your answer") }, isError = answerError != null,
          supportingText = answerError?.let { { Text(it) } },
          textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
            keyboardType = if (kind == "math") KeyboardType.Number else KeyboardType.Password, imeAction = ImeAction.Done),
          keyboardActions = KeyboardActions(onDone = { send() }), shape = MaterialTheme.shapes.medium)
        Button(onClick = ::send, enabled = !busy && answer.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
          Text("Check answer", style = MaterialTheme.typography.titleMedium)
        }
      }
    }
  }
}

@Composable
private fun Progress(done: Int, total: Int) {
  if (total > 16) { LinearProgressIndicator(progress = { done / total.toFloat() }, Modifier.fillMaxWidth()); return }
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    repeat(total) { step ->
      Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(
        if (step < done) MaterialTheme.colorScheme.primary
        else if (step == done) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.surfaceContainerHighest))
    }
  }
}

@Composable
internal fun WakeCheckScreen(check: JSONObject, busy: Boolean, answerError: String?, now: Long, onReply: (Boolean) -> Unit,
  onSubmit: (String) -> Unit, onScan: () -> Unit, onAnswerEdit: () -> Unit,
  onComplete: () -> Unit = {}, onFallback: () -> Unit = {}) {
  val alarm = AlarmDraft.from(check.getJSONObject("alarm"))
  val deadline = check.getLong("deadlineAt")
  val window = (deadline - check.getLong("dueAt")).coerceAtLeast(1)
  val remaining = ((deadline - now + 999) / 1000).coerceAtLeast(0)
  TaskAlarmTheme(dark = true) {
    Box(Modifier.fillMaxSize().background(DawnSky)) {
      Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
          if (check.optBoolean("test")) InfoPill(R.drawable.ic_play_arrow, "Test check", MaterialTheme.colorScheme.onSecondaryContainer, MaterialTheme.colorScheme.secondaryContainer)
        }
        Ico(R.drawable.ic_wb_sunny, tint = MaterialTheme.colorScheme.primary, size = 56.dp)
        Text("Are you out of bed?", style = MaterialTheme.typography.headlineLarge, color = Color.White, textAlign = TextAlign.Center)
        Text(alarm.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.padding(vertical = 12.dp).size(200.dp), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(progress = { (remaining * 1000f / window).coerceIn(0f, 1f) }, Modifier.fillMaxSize(),
            strokeWidth = 10.dp, trackColor = Color.White.copy(alpha = 0.12f), gapSize = 0.dp)
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')}", style = clockStyle(52.sp), color = Color.White)
            Text("to answer", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
        if (alarm.wakeMode == "yes_no") {
          Button(onClick = { onReply(true) }, enabled = !busy, shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
            Ico(R.drawable.ic_check_circle, size = 28.dp); Spacer(Modifier.width(12.dp)); Text("Yes, I'm up", style = MaterialTheme.typography.titleLarge)
          }
        } else ChallengePanel(check, alarm.wakeTasks, "Confirm", busy, answerError, onSubmit, onScan, onAnswerEdit, onComplete, onFallback)
        TextButton(onClick = { onReply(false) }, enabled = !busy) { Text("No — ring again now") }
        Text("If you don't answer in time, the alarm rings again.", style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
      }
    }
  }
}
