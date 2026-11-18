package com.taskalarm.engine

import org.json.JSONArray
import java.security.SecureRandom

/** Per-session task list adjustments; the saved alarm is never changed. */
internal object TaskOrder {
  private val random = SecureRandom()
  private val levels = listOf("easy", "medium", "hard")

  fun shuffled(tasks: JSONArray): JSONArray {
    val list = (0 until tasks.length()).map { tasks.getJSONObject(it) }.toMutableList()
    for (i in list.indices.reversed()) { val j = random.nextInt(i + 1); val t = list[i]; list[i] = list[j]; list[j] = t }
    return JSONArray(list)
  }

  /** Raise each task's difficulty by [steps] levels, capped at hard. Tasks without a difficulty are unchanged. */
  fun harder(tasks: JSONArray, steps: Int): JSONArray {
    if (steps <= 0) return tasks
    return JSONArray((0 until tasks.length()).map { index ->
      val task = org.json.JSONObject(tasks.getJSONObject(index).toString())
      val level = levels.indexOf(task.optString("difficulty"))
      if (level >= 0) task.put("difficulty", levels[(level + steps).coerceAtMost(levels.lastIndex)])
      task
    })
  }

  fun randomMinutes(min: Int, max: Int) = min + random.nextInt(max - min + 1)
}
