package com.taskalarm.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.taskalarm.engine.SoundProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** State owner and navigation: home → editor, with ringing, wake-up check and scanner taking over when active. */
@Composable
internal fun TaskAlarmApp(activity: MainActivity) {
  TaskAlarmTheme {
    var state by remember { mutableStateOf<JSONObject?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var draftJson by rememberSaveable { mutableStateOf<String?>(null) }
    var draftIsNew by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var answerError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var scanAction by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var deleteTarget by remember { mutableStateOf<AlarmDraft?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    suspend fun refresh() {
      state = withContext(Dispatchers.IO) { JSONObject(activity.engine.state()) }
      now = System.currentTimeMillis()
      activity.updateAlarmWindow()
    }
    fun work(block: suspend () -> Unit) {
      if (busy) return
      scope.launch {
        busy = true
        try { block(); refresh() }
        catch (failure: Exception) { error = failure.message ?: "Could not complete this action" }
        finally { busy = false }
      }
    }
    fun announce(alarm: AlarmDraft) {
      val next = nextRing(alarm) ?: return
      scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar("Alarm set for ${durationText(next - System.currentTimeMillis())} from now") }
    }
    LaunchedEffect(Unit) {
      activity.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        while (true) {
          try { refresh() } catch (failure: Exception) { error = failure.message }
          delay(1000)
        }
      }
    }
    val active = state?.optJSONObject("active")
    val wakeCheck = state?.optJSONObject("wakeCheck")
    val draft = draftJson?.let { AlarmDraft.from(JSONObject(it)) }
    val immersive = active != null || wakeCheck != null || scanAction != null
    LaunchedEffect(immersive) { activity.setImmersiveBars(immersive) }
    LaunchedEffect(active?.optString("sessionId"), wakeCheck?.optString("sessionId")) { scanAction = null }
    LaunchedEffect(active?.optString("challengeId"), wakeCheck?.optString("challengeId")) { answerError = null }
    BackHandler(enabled = scanAction != null || active != null || wakeCheck != null || draft != null) {
      if (scanAction != null) scanAction = null
      else if (active == null && wakeCheck != null) activity.moveTaskToBack(true)
      else if (active == null && !busy) draftJson = null
    }
    fun submit(session: JSONObject, check: Boolean, answer: String) {
      work {
        val correct = withContext(Dispatchers.IO) {
          if (check) activity.engine.submitWake(session.getString("sessionId"), session.getString("challengeId"), answer)
          else activity.engine.submit(session.getString("sessionId"), session.getString("challengeId"), answer)
        }
        if (!correct) answerError = if (session.getString("kind") == "barcode") "That isn't your saved barcode. Try again." else "Not quite — try again."
      }
    }
    fun prepareAndRun(test: Boolean, wake: Boolean = false) {
      val current = draft ?: return
      work {
        current.validate()
        val prepared = withContext(Dispatchers.IO) {
          val sound = if (current.normalize && current.soundUri.isNotBlank()) SoundProcessor.prepare(activity, current.soundUri, true) else ""
          // Saving an alarm turns it on, like a bedside clock.
          val result = current.copy(playbackUri = sound, enabled = if (test) current.enabled else true)
          if (wake) activity.engine.testWake(result.json().toString())
          else if (test) activity.engine.test(result.json().toString()) else activity.engine.save(result.json().toString())
          result
        }
        if (test) draftJson = prepared.json().toString() else { draftJson = null; announce(prepared) }
      }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      when {
        scanAction != null -> BarcodeCamera(onRead = { value -> val action = scanAction; scanAction = null; action?.invoke(value) }, onCancel = { scanAction = null })
        active != null -> RingingScreen(active, busy, answerError, now,
          onAction = { action -> work { withContext(Dispatchers.IO) { activity.engine.begin(active.getString("sessionId"), action) } } },
          onSubmit = { submit(active, false, it) }, onScan = { scanAction = { submit(active, false, it) } },
          onMute = { muted -> work { withContext(Dispatchers.IO) { activity.engine.setMuted(active.getString("sessionId"), muted) } } },
          onAnswerEdit = { answerError = null })
        wakeCheck != null -> WakeCheckScreen(wakeCheck, busy, answerError, now,
          onReply = { awake -> work { withContext(Dispatchers.IO) { activity.engine.respondWake(wakeCheck.getString("sessionId"), awake) } } },
          onSubmit = { submit(wakeCheck, true, it) }, onScan = { scanAction = { submit(wakeCheck, true, it) } },
          onAnswerEdit = { answerError = null })
        draft != null -> EditorScreen(draft, draftIsNew, state?.optJSONObject("readiness"), busy, now,
          onChange = { if (!busy) draftJson = it.json().toString() },
          onBack = { draftJson = null }, onSave = { prepareAndRun(false) }, onTest = { prepareAndRun(true) },
          onTestWake = { prepareAndRun(true, true) },
          onAccess = { kind -> try { activity.openAccess(kind) } catch (failure: Exception) { error = failure.message } },
          onSound = { custom -> activity.chooseSound(custom, { uri, name ->
            draftJson?.let { saved ->
              val current = AlarmDraft.from(JSONObject(saved))
              if (current.id == draft.id) draftJson = current.copy(soundUri = uri, soundName = name, playbackUri = "").json().toString()
            }
          }, { error = it }) },
          onEnroll = { action, id -> if (!busy) scanAction = { value ->
            draftJson?.let { saved ->
              val current = AlarmDraft.from(JSONObject(saved))
              draftJson = current.withTasks(action, current.tasks(action).map { if (it.id == id) it.copy(barcode = value) else it }).json().toString()
            }
          } })
        else -> HomeScreen(state, busy, snackbar, now,
          onAdd = { draftIsNew = true; draftJson = AlarmDraft().json().toString() },
          onEdit = { draftIsNew = false; draftJson = it.json().toString() },
          onToggle = { alarm, enabled -> work {
            withContext(Dispatchers.IO) { activity.engine.save(alarm.copy(enabled = enabled).json().toString()) }
            if (enabled) announce(alarm)
          } },
          onDelete = { deleteTarget = it },
          onTest = { alarm -> work { withContext(Dispatchers.IO) { activity.engine.test(alarm.json().toString()) } } },
          onAccess = { kind -> try { activity.openAccess(kind) } catch (failure: Exception) { error = failure.message } })
      }
    }
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, icon = { Ico(R.drawable.ic_warning) },
      title = { Text("Can't do that yet") }, text = { Text(message) },
      confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } }) }
    deleteTarget?.let { target ->
      val clock = clockText(target.hour.toInt(), target.minute.toInt(), is24Hour())
      AlertDialog(onDismissRequest = { deleteTarget = null }, icon = { Ico(R.drawable.ic_delete) },
        title = { Text("Delete ${clock.time} ${clock.period}".trim() + " alarm?") }, text = { Text("${target.label} · ${repeatText(target.days)}") },
        confirmButton = { TextButton(onClick = { deleteTarget = null; work { withContext(Dispatchers.IO) { activity.engine.delete(target.id) } } }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } })
    }
  }
}
