package com.taskalarm.engine

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Runs an ordered task list on a session (ringing alarm) or wake-up check record. Answer tasks (text, math, memory,
 * barcode) are checked here; interactive tasks (breakout, maze, walk, light) run in the UI and report completion,
 * which is accepted only for the current challenge and after a plausible minimum time.
 */
internal object SessionTasks {
  enum class Result { WRONG, NEXT, COMPLETE }
  val INTERACTIVE = setOf("breakout", "maze", "walk", "light")
  private val FALLBACK_CAPABLE = setOf("walk", "light", "maze")

  fun start(session: JSONObject, tasks: JSONArray, now: Long = System.currentTimeMillis()) {
    require(tasks.length() > 0) { "Choose at least one task" }
    session.put("taskIndex", 0).put("round", 1)
    challenge(session, tasks, now)
  }

  private fun challenge(session: JSONObject, tasks: JSONArray, now: Long) {
    val task = tasks.getJSONObject(session.getInt("taskIndex"))
    val kind = task.getString("kind")
    session.put("kind", kind).put("challengeId", UUID.randomUUID().toString()).put("issuedAt", now).remove("sequence")
    when (kind) {
      "barcode" -> session.put("prompt", if (task.optString("barcodeMode") == "any") "Scan any barcode" else "Scan your saved barcode")
        .put("answer", task.optString("barcode"))
      in INTERACTIVE -> session.put("prompt", describe(task)).put("answer", "")
      else -> {
        val generated = Tasks.generate(kind, task.optString("difficulty", "easy"))
        session.put("prompt", generated.prompt).put("answer", generated.answer)
        // The memory sequence is shown on screen, so it is exposed to the UI alongside the prompt.
        if (kind == "memory") session.put("sequence", generated.answer)
      }
    }
  }

  private fun describe(task: JSONObject) = when (task.getString("kind")) {
    "breakout" -> "Keep the ball in play for ${task.optInt("gameSeconds", 60)} seconds"
    "maze" -> "Reach each checkpoint, then the exit"
    "walk" -> "Walk ${task.optInt("stepTarget", 20)} steps"
    else -> "Find ${task.optInt("lightLux", 200)} lux of light and hold it for ${task.optInt("lightHoldSeconds", 3)} seconds"
  }

  /** Shortest time a genuine completion could take; earlier reports are rejected. */
  fun minimumMs(task: JSONObject): Long = when (task.getString("kind")) {
    "breakout" -> task.optInt("gameSeconds", 60) * 1000L
    "walk" -> task.optInt("stepTarget", 20) * 250L
    "light" -> task.optInt("lightHoldSeconds", 3) * 1000L
    "maze" -> 3000L
    else -> 0L
  }

  private fun current(session: JSONObject, tasks: JSONArray, challengeId: String): JSONObject {
    check(session.getString("challengeId") == challengeId) { "This task has already advanced" }
    return tasks.getJSONObject(session.getInt("taskIndex"))
  }

  fun submit(session: JSONObject, tasks: JSONArray, challengeId: String, answer: String, now: Long = System.currentTimeMillis()): Result {
    val task = current(session, tasks, challengeId)
    val kind = task.getString("kind")
    if (kind in INTERACTIVE) return Result.WRONG
    val correct = if (kind == "barcode" && task.optString("barcodeMode") == "any") answer.isNotEmpty()
      else Tasks.matches(kind, session.getString("answer"), answer)
    return if (correct) advance(session, tasks, now) else Result.WRONG
  }

  fun complete(session: JSONObject, tasks: JSONArray, challengeId: String, now: Long = System.currentTimeMillis()): Result {
    val task = current(session, tasks, challengeId)
    check(task.getString("kind") in INTERACTIVE) { "This task needs an answer" }
    check(now - session.optLong("issuedAt", 0) >= minimumMs(task)) { "This task isn't finished yet" }
    return advance(session, tasks, now)
  }

  /** Swaps a sensor task for its math/text fallback in this session's copy only; the saved alarm is unchanged. */
  fun fallback(session: JSONObject, tasks: JSONArray, challengeId: String, now: Long = System.currentTimeMillis()) {
    val task = current(session, tasks, challengeId)
    check(task.getString("kind") in FALLBACK_CAPABLE) { "This task has no fallback" }
    task.put("kind", task.optString("fallbackKind", "math").takeIf { it == "math" || it == "text" } ?: "math")
    challenge(session, tasks, now)
  }

  private fun advance(session: JSONObject, tasks: JSONArray, now: Long): Result {
    val task = tasks.getJSONObject(session.getInt("taskIndex"))
    if (session.getInt("round") < task.optInt("rounds", 1)) session.put("round", session.getInt("round") + 1)
    else session.put("taskIndex", session.getInt("taskIndex") + 1).put("round", 1)
    if (session.getInt("taskIndex") == tasks.length()) return Result.COMPLETE
    challenge(session, tasks, now)
    return Result.NEXT
  }
}
