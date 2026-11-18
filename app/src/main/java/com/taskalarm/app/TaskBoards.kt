package com.taskalarm.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import android.view.Surface as DisplaySurface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.delay
import kotlin.random.Random

private const val UNAVAILABLE_MS = 15_000L

@Composable
private fun resumed(): Boolean = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.RESUMED)

/** Calls [onComplete] once per [key]. */
@Composable
private fun rememberOnce(key: String, onComplete: () -> Unit): () -> Unit {
  var fired by remember(key) { mutableStateOf(false) }
  val latest by rememberUpdatedState(onComplete)
  return { if (!fired) { fired = true; latest() } }
}

// ---------------------------------------------------------------- Memory

private val TileColors = listOf(Color(0xffef6c6c), Color(0xff5cb8ff), Color(0xff7ad48a), Color(0xffffc46b))

/** Four numbered tiles flash the sequence; tap them back in order. Submits automatically when the length matches. */
@Composable
internal fun MemoryBoard(sequence: String, key: String, enabled: Boolean, onSubmit: (String) -> Unit) {
  var replay by remember(key) { mutableIntStateOf(0) }
  var playing by remember(key) { mutableStateOf(true) }
  var lit by remember(key) { mutableStateOf<Int?>(null) }
  var input by remember(key) { mutableStateOf("") }
  LaunchedEffect(key, replay) {
    playing = true; input = ""; lit = null
    delay(600)
    for (digit in sequence) { lit = digit.digitToInt(); delay(450); lit = null; delay(220) }
    playing = false
  }
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    for (row in 0..1) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      for (col in 1..2) {
        val tile = row * 2 + col
        val on = lit == tile
        Surface(onClick = {
          if (!playing && enabled) { input += tile; if (input.length == sequence.length) { val answer = input; input = ""; onSubmit(answer) } }
        }, enabled = !playing && enabled, shape = MaterialTheme.shapes.medium,
          color = if (on) Color.White else TileColors[tile - 1].copy(alpha = if (playing) 0.55f else 0.9f),
          modifier = Modifier.weight(1f).height(76.dp).semantics { contentDescription = "Tile $tile" }) {
          Box(contentAlignment = Alignment.Center) {
            Text("$tile", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xff1a1a24))
          }
        }
      }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(sequence.length) { i ->
          Box(Modifier.size(10.dp).clip(CircleShape).background(
            if (i < input.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest))
        }
      }
      Text(if (playing) "Watch…" else "Your turn", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
      TextButton(onClick = { replay++ }, enabled = !playing && enabled) { Text("Show again") }
    }
  }
}

// ---------------------------------------------------------------- Breakout

@Composable
internal fun BreakoutBoard(task: TaskDraft, key: String, enabled: Boolean, onComplete: () -> Unit) {
  val game = remember(key) { BreakoutSim(task.difficulty, task.gameSeconds * 1000L) }
  var frame by remember { mutableLongStateOf(0L) }
  val done = rememberOnce(key, onComplete)
  val active = resumed() && enabled
  LaunchedEffect(key, active) {
    if (!active) { game.pause(); return@LaunchedEffect }
    while (true) {
      withFrameNanos { nanos -> game.step(SystemClock.elapsedRealtime()); frame = nanos }
      if (game.complete) { done(); break }
    }
  }
  val brick = MaterialTheme.colorScheme.secondary; val brickAlt = MaterialTheme.colorScheme.tertiary
  val paddle = MaterialTheme.colorScheme.primary; val ball = Color.White
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Canvas(Modifier.fillMaxWidth().height(300.dp).clip(MaterialTheme.shapes.medium).background(Color(0xff0c0e18))
      .semantics { contentDescription = "Breakout. Drag to move the paddle." }
      .pointerInput(key) {
        awaitEachGesture {
          val down = awaitFirstDown()
          game.input(down.position.x / size.width, SystemClock.elapsedRealtime())
          do {
            val event = awaitPointerEvent()
            event.changes.firstOrNull()?.let { game.input(it.position.x / size.width, SystemClock.elapsedRealtime()); it.consume() }
          } while (event.changes.any { it.pressed })
        }
      }) {
      frame.let { } // Redraw every frame.
      val w = size.width; val h = size.height
      game.bricks.forEachIndexed { i, b ->
        drawRoundRect(if ((i / 6) % 2 == 0) brick else brickAlt, Offset(b.x * w, b.y * h), Size(b.w * w, b.h * h), CornerRadius(6f))
      }
      drawRoundRect(paddle, Offset((game.paddle - game.paddleWidth / 2) * w, 0.92f * h), Size(game.paddleWidth * w, 8.dp.toPx()), CornerRadius(12f))
      drawCircle(ball, 0.022f * w.coerceAtMost(h), Offset(game.ballX * w, game.ballY * h))
    }
    LinearProgressIndicator(progress = { game.playedMs / game.targetMs.toFloat() }, Modifier.fillMaxWidth())
    Text("${game.playedMs / 1000} / ${game.targetMs / 1000} s · keep moving the paddle" +
      if (game.resets > 0) " · timer reset after 20 s idle" else "", style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

// ---------------------------------------------------------------- Sensors

/** Registers the first available sensor of [types] while [active]; returns false if the phone has none. */
@Composable
private fun sensorAvailable(types: List<Int>, active: Boolean, key: String, onEvent: (SensorEvent, Int) -> Unit): Boolean {
  val context = LocalContext.current
  val manager = remember { context.getSystemService(SensorManager::class.java) }
  val sensor = remember(types) { types.firstNotNullOfOrNull { manager?.getDefaultSensor(it) } }
  val latest by rememberUpdatedState(onEvent)
  var registered by remember { mutableStateOf(true) }
  DisposableEffect(sensor, active, key) {
    if (sensor == null || !active) return@DisposableEffect onDispose { }
    val listener = object : SensorEventListener {
      override fun onSensorChanged(event: SensorEvent) = latest(event, sensor.type)
      override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }
    registered = try { manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME) } catch (_: SecurityException) { false }
    onDispose { manager.unregisterListener(listener) }
  }
  return sensor != null && registered
}

/** Ticks once a second while [active], so availability timers re-evaluate. */
@Composable
private fun secondTicker(key: String, active: Boolean): Long {
  var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
  LaunchedEffect(key, active) { while (active) { now = SystemClock.elapsedRealtime(); delay(1_000) } }
  return now
}

@Composable
internal fun MazeBoard(task: TaskDraft, key: String, enabled: Boolean, onComplete: () -> Unit, onUnavailable: (String?) -> Unit) {
  val maze = remember(key) { MazeSim(task.difficulty, Random.nextBoolean()) }
  val done = rememberOnce(key, onComplete)
  val active = resumed() && enabled
  val tilt = task.controlMode == "tilt"
  var held by remember(key) { mutableStateOf(0f to 0f) }
  var smoothed by remember(key) { mutableStateOf(0f to 0f) }
  var offset by remember(key) { mutableStateOf(0f to 0f) }
  var lastReading by remember(key) { mutableLongStateOf(0L) }
  val startedAt = remember(key, active) { SystemClock.elapsedRealtime() }
  var frame by remember { mutableLongStateOf(0L) }
  val rotation = LocalView.current.display?.rotation ?: DisplaySurface.ROTATION_0
  val hasSensor = if (tilt) sensorAvailable(listOf(Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER), active, key) { event, _ ->
    val (x, y) = when (rotation) {
      DisplaySurface.ROTATION_90 -> -event.values[1] to -event.values[0]
      DisplaySurface.ROTATION_180 -> event.values[0] to -event.values[1]
      DisplaySurface.ROTATION_270 -> event.values[1] to event.values[0]
      else -> -event.values[0] to event.values[1]
    }
    smoothed = (0.8f * smoothed.first + 0.2f * x) to (0.8f * smoothed.second + 0.2f * y)
    lastReading = SystemClock.elapsedRealtime()
  } else true
  val now = secondTicker(key, active && tilt)
  LaunchedEffect(hasSensor, now, lastReading) {
    if (!tilt) onUnavailable(null)
    else if (!hasSensor) onUnavailable("This phone has no tilt sensor.")
    else onUnavailable(if (now - maxOf(startedAt, lastReading) > UNAVAILABLE_MS) "The tilt sensor stopped responding." else null)
  }
  LaunchedEffect(key, active) {
    var last = SystemClock.elapsedRealtime()
    while (active) {
      withFrameNanos { nanos ->
        val t = SystemClock.elapsedRealtime()
        val fresh = SystemClock.elapsedRealtime() - lastReading < 1_000
        val (dx, dy) = if (!tilt) held else if (fresh) ((smoothed.first - offset.first) / 4f) to ((smoothed.second - offset.second) / 4f) else 0f to 0f
        maze.step(dx, dy, t - last); last = t; frame = nanos
      }
      if (maze.complete) { done(); break }
    }
  }
  val text = rememberTextMeasurer()
  val wall = MaterialTheme.colorScheme.outline; val point = MaterialTheme.colorScheme.secondary
  val reachedColor = MaterialTheme.colorScheme.surfaceContainerHighest; val exit = MaterialTheme.colorScheme.primary
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Canvas(Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium).background(Color(0xff0c0e18))
      .semantics { contentDescription = "Maze. Reach checkpoints in order, then the exit." }) {
      frame.let { }
      val s = size.width
      maze.walls.forEach { drawRoundRect(wall, Offset(it.x * s, it.y * s), Size(it.w * s, it.h * s), CornerRadius(6f)) }
      maze.checkpoints.forEachIndexed { i, cp ->
        val last = i == maze.checkpoints.lastIndex
        val color = if (i < maze.reached) reachedColor else if (last) exit else point
        drawCircle(color, 0.05f * s, Offset(cp.x * s, cp.y * s))
        val label = text.measure(if (last) "E" else "${i + 1}", TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xff101018)))
        drawText(label, topLeft = Offset(cp.x * s - label.size.width / 2, cp.y * s - label.size.height / 2))
      }
      drawCircle(Color.White, MazeSim.RADIUS * s, Offset(maze.x * s, maze.y * s))
    }
    if (tilt) Row(verticalAlignment = Alignment.CenterVertically) {
      Text("Tilt the phone to roll the ball.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f))
      TextButton(onClick = { offset = smoothed }) { Ico(R.drawable.ic_center_focus_strong, size = 18.dp); Spacer(Modifier.width(6.dp)); Text("Set level") }
    } else ArrowPad { dx, dy -> held = dx to dy }
  }
}

@Composable
private fun ArrowPad(onHold: (Float, Float) -> Unit) {
  @Composable fun Arrow(label: String, icon: Int, rotate: Float, dx: Float, dy: Float) {
    FilledTonalIconButton(onClick = {}, modifier = Modifier.size(56.dp).semantics { contentDescription = label }
      .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(); onHold(dx, dy); do { val e = awaitPointerEvent() } while (e.changes.any { it.pressed }); onHold(0f, 0f) } }) {
      Ico(icon, modifier = Modifier.rotate(rotate))
    }
  }
  Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Arrow("Up", R.drawable.ic_arrow_upward, 0f, 0f, -1f)
    Row(horizontalArrangement = Arrangement.spacedBy(56.dp)) {
      Arrow("Left", R.drawable.ic_arrow_upward, -90f, -1f, 0f); Arrow("Right", R.drawable.ic_arrow_upward, 90f, 1f, 0f)
    }
    Arrow("Down", R.drawable.ic_arrow_downward, 0f, 0f, 1f)
  }
}

private fun Context.hasActivityPermission() = Build.VERSION.SDK_INT < 29 ||
  ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

@Composable
internal fun WalkBoard(task: TaskDraft, key: String, enabled: Boolean, onComplete: () -> Unit, onUnavailable: (String?) -> Unit) {
  val context = LocalContext.current
  var permitted by remember { mutableStateOf(context.hasActivityPermission()) }
  val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = it }
  val done = rememberOnce(key, onComplete)
  val active = resumed() && enabled && permitted
  val steps = remember(key) { StepTracker(SystemClock.elapsedRealtimeNanos()) }
  var count by remember(key) { mutableIntStateOf(0) }
  var lastReading by remember(key) { mutableLongStateOf(0L) }
  val startedAt = remember(key, active) { steps.restart(SystemClock.elapsedRealtimeNanos()); SystemClock.elapsedRealtime() }
  val hasSensor = sensorAvailable(listOf(Sensor.TYPE_STEP_DETECTOR, Sensor.TYPE_STEP_COUNTER), active, key) { event, type ->
    val nowNanos = SystemClock.elapsedRealtimeNanos()
    if (type == Sensor.TYPE_STEP_DETECTOR) steps.detector(event.timestamp, nowNanos) else steps.counter(event.values[0], event.timestamp, nowNanos)
    lastReading = SystemClock.elapsedRealtime(); count = steps.steps
    if (count >= task.stepTarget) done()
  }
  val now = secondTicker(key, active)
  LaunchedEffect(permitted, hasSensor, now, lastReading) {
    onUnavailable(when {
      !permitted -> "Step counting needs the Physical activity permission."
      !hasSensor -> "This phone has no step sensor."
      now - maxOf(startedAt, lastReading) > UNAVAILABLE_MS -> "No steps detected for 15 seconds."
      else -> null
    })
  }
  Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
      CircularProgressIndicator(progress = { count / task.stepTarget.toFloat() }, Modifier.fillMaxSize(), strokeWidth = 10.dp,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest, gapSize = 0.dp)
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$count", style = clockStyle(44.sp)); Text("of ${task.stepTarget} steps", style = MaterialTheme.typography.bodySmall)
      }
    }
    if (!permitted) Button(onClick = { ask.launch(Manifest.permission.ACTIVITY_RECOGNITION) }) { Text("Allow step counting") }
    else Text("Steps count only while this screen is open.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
internal fun LightBoard(task: TaskDraft, key: String, enabled: Boolean, onComplete: () -> Unit, onUnavailable: (String?) -> Unit) {
  val done = rememberOnce(key, onComplete)
  val active = resumed() && enabled
  val hold = remember(key) { LightHold(task.lightLux.toFloat(), task.lightHoldSeconds * 1000L) }
  var lux by remember(key) { mutableStateOf<Float?>(null) }
  var held by remember(key) { mutableLongStateOf(0L) }
  val startedAt = remember(key, active) { if (!active) hold.pause(); SystemClock.elapsedRealtime() }
  val hasSensor = sensorAvailable(listOf(Sensor.TYPE_LIGHT), active, key) { event, _ ->
    hold.sample(event.values[0], SystemClock.elapsedRealtime()); lux = hold.lux
  }
  LaunchedEffect(key, active) {
    while (active) {
      hold.advance(SystemClock.elapsedRealtime()); held = hold.heldMs
      if (hold.complete) { done(); break }
      delay(33)
    }
  }
  val now = secondTicker(key, active)
  LaunchedEffect(hasSensor, now, lux) {
    onUnavailable(when {
      !hasSensor -> "This phone has no light sensor."
      lux == null && now - startedAt > UNAVAILABLE_MS -> "The light sensor isn't sending readings."
      else -> null
    })
  }
  Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (lux != null) Text("${lux!!.toInt()} lux", style = clockStyle(40.sp))
    else Text("Waiting for the light sensor…", style = MaterialTheme.typography.titleMedium)
    Text("Target ${task.lightLux} lux for ${task.lightHoldSeconds} s", style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant)
    LinearProgressIndicator(progress = { held / (task.lightHoldSeconds * 1000f) }, Modifier.fillMaxWidth())
    Text("Turn on the lights or face a window. The sensor is usually near the front camera.", style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
