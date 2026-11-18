package com.taskalarm.app

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

/*
 * Pure game and sensor logic for interactive tasks. Coordinates are normalised to 0..1; times are milliseconds on a
 * monotonic clock (elapsedRealtime). Kept free of Android types so it can be unit-tested on the JVM.
 */

internal data class Box(val x: Float, val y: Float, val w: Float, val h: Float)
internal data class Spot(val x: Float, val y: Float)

/**
 * Breakout where completion depends on active play time, not score: time counts only while the paddle moved in the
 * last 2.5 s, and an idle gap of [idleLimitMs] resets the round's timer. Lost balls are unlimited.
 */
internal class BreakoutSim(difficulty: String, val targetMs: Long, private val idleLimitMs: Long = 20_000) {
  val paddleWidth = when (difficulty) { "easy" -> 0.28f; "medium" -> 0.22f; else -> 0.17f }
  private val speed = when (difficulty) { "easy" -> 0.43f; "medium" -> 0.57f; else -> 0.72f }
  var paddle = 0.5f; private set
  var ballX = 0.5f; private set
  var ballY = 0.7f; private set
  private var vx = speed * 0.447f
  private var vy = -speed * 0.894f
  val bricks = mutableListOf<Box>().apply { refill() }
  var score = 0; private set
  var playedMs = 0L; private set
  var resets = 0; private set
  private var lastInputAt: Long? = null
  private var lastTick: Long? = null
  val complete get() = playedMs >= targetMs

  private fun MutableList<Box>.refill() {
    clear(); for (row in 0 until 4) for (col in 0 until 6) add(Box(0.018f + col / 6f, 0.08f + row * 0.065f, 0.145f, 0.045f))
  }

  /** Finger position across the field (0..1). Only real movement counts as play. */
  fun input(x: Float, now: Long) {
    val target = x.coerceIn(paddleWidth / 2, 1 - paddleWidth / 2)
    if (abs(target - paddle) >= 0.005f) lastInputAt = now
    paddle = target
  }

  /** Leaving the screen earns nothing; a long absence resets the timer on return. */
  fun pause() { lastTick = null }

  fun step(now: Long) {
    val dtMs = lastTick?.let { now - it } ?: 0L
    lastTick = now
    val idle = lastInputAt?.let { now - it } ?: idleLimitMs
    if (idle >= idleLimitMs && playedMs > 0) { playedMs = 0; resets++ }
    if (lastInputAt != null && idle < 2_500 && dtMs in 1..250) playedMs = min(playedMs + dtMs, targetMs)
    val dt = min(dtMs, 50L) / 1000f
    if (dt <= 0f) return
    ballX += vx * dt; ballY += vy * dt
    if (ballX < 0.015f) { ballX = 0.015f; vx = abs(vx) }
    if (ballX > 0.985f) { ballX = 0.985f; vx = -abs(vx) }
    if (ballY < 0.015f) { ballY = 0.015f; vy = abs(vy) }
    if (vy > 0 && ballY in 0.90f..0.94f && abs(ballX - paddle) <= paddleWidth / 2 + 0.015f) {
      ballY = 0.9f
      vx = ((ballX - paddle) / (paddleWidth / 2) * speed * 0.85f).coerceIn(-speed * 0.85f, speed * 0.85f)
      vy = -sqrt((speed * speed - vx * vx).coerceAtLeast(0.02f))
    }
    bricks.firstOrNull { ballX in it.x - 0.012f..it.x + it.w + 0.012f && ballY in it.y - 0.012f..it.y + it.h + 0.012f }?.let {
      bricks.remove(it); vy = -vy; score += 10
    }
    if (bricks.isEmpty()) bricks.refill()
    if (ballY > 1.02f) { ballX = paddle; ballY = 0.72f; vx = speed * 0.447f; vy = -speed * 0.894f }
  }
}

/**
 * Ball maze: horizontal walls with gaps on alternating sides, numbered checkpoints that must be reached in order, then
 * the exit. [flip] mirrors the layout so it changes between rounds.
 */
internal class MazeSim(difficulty: String, flip: Boolean) {
  private val count = when (difficulty) { "easy" -> 1; "medium" -> 2; else -> 3 }
  private val speed = when (difficulty) { "easy" -> 0.24f; "medium" -> 0.29f; else -> 0.34f }
  private val wallWidth = when (count) { 1 -> 0.65f; 2 -> 0.70f; else -> 0.73f }
  val walls = (0 until count).map { i ->
    val leftWall = (i % 2 == 0) != flip
    Box(if (leftWall) 0f else 1 - wallWidth, (i + 1f) / (count + 2), wallWidth, 0.035f)
  }
  /** Each checkpoint sits just past a wall's gap; the last one is the exit. */
  val checkpoints = walls.map { wall -> Spot(if (wall.x == 0f) 0.86f else 0.14f, wall.y + 0.105f) } + Spot(0.5f, 0.93f)
  var x = if (flip) 0.86f else 0.14f; private set
  var y = 0.10f; private set
  var reached = 0; private set
  val complete get() = reached == checkpoints.size

  private fun collides(px: Float, py: Float) = walls.any { wall ->
    val cx = px.coerceIn(wall.x, wall.x + wall.w); val cy = py.coerceIn(wall.y, wall.y + wall.h)
    hypot(px - cx, py - cy) < RADIUS
  }

  /** [dx], [dy] in -1..1 (full tilt or a held arrow). Stale or huge frame gaps don't move the ball. */
  fun step(dx: Float, dy: Float, dtMs: Long) {
    if (dtMs !in 1..100) return
    val ix = if (dx.isNaN()) 0f else dx.coerceIn(-1f, 1f); val iy = if (dy.isNaN()) 0f else dy.coerceIn(-1f, 1f)
    repeat(10) {
      val nx = (x + ix * speed * dtMs / 10_000f).coerceIn(RADIUS, 1 - RADIUS)
      if (!collides(nx, y)) x = nx
      val ny = (y + iy * speed * dtMs / 10_000f).coerceIn(RADIUS, 1 - RADIUS)
      if (!collides(x, ny)) y = ny
      if (reached < checkpoints.size && hypot(x - checkpoints[reached].x, y - checkpoints[reached].y) < 0.065f) reached++
    }
  }

  companion object { const val RADIUS = 0.025f }
}

/**
 * Light hold for on-change sensors: steady brightness sends no further readings, so the hold keeps counting until a
 * reading drops below the threshold. Readings are timed when received so a cached value can't backdate the hold.
 */
internal class LightHold(private val threshold: Float, private val holdMs: Long) {
  var lux: Float? = null; private set
  var heldMs = 0L; private set
  private var above = false
  private var lastTick = -1L
  val hasReading get() = lux != null
  val complete get() = heldMs >= holdMs

  fun sample(value: Float, receivedAt: Long) {
    if (!value.isFinite()) return
    lux = value
    if (value >= threshold) { if (!above) { heldMs = 0; lastTick = receivedAt }; above = true }
    else { heldMs = 0; above = false; lastTick = -1 }
  }

  fun advance(now: Long) { if (above && lastTick >= 0) { heldMs = min(holdMs, heldMs + (now - lastTick)); lastTick = now } }

  fun pause() { lux = null; heldMs = 0; above = false; lastTick = -1 }
}

/**
 * Counts steps from a step detector (one event per step) or a cumulative step counter (deltas of at most 20 per
 * reading). Events from before the task started, older than 2 s, or out of order are ignored.
 */
internal class StepTracker(private var startNanos: Long) {
  var steps = 0; private set
  var lastReadingNanos = Long.MIN_VALUE; private set
  private var baseline: Float? = null

  private fun fresh(timestamp: Long, now: Long): Boolean {
    val ok = timestamp > lastReadingNanos && timestamp in startNanos..now && now - timestamp <= 2_000_000_000L
    if (ok) lastReadingNanos = timestamp
    return ok
  }

  fun detector(timestamp: Long, now: Long) { if (fresh(timestamp, now)) steps++ }

  fun counter(total: Float, timestamp: Long, now: Long) {
    if (!fresh(timestamp, now)) return
    val previous = baseline
    baseline = total
    if (previous != null && total - previous in 0f..20f) steps += (total - previous).toInt()
  }

  /** On resume: steps taken while away never count. */
  fun restart(now: Long) { startNanos = now; baseline = null }
}
