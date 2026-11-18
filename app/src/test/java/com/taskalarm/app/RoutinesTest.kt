package com.taskalarm.app

import org.junit.Assert.*
import org.junit.Test

class RoutinesTest {
  @Test fun applyingKeepsTheAlarmsIdentityScheduleAndSwitchButTakesEverythingElse() {
    val target = AlarmDraft(id = "a1", label = "Work", hour = "06", minute = "15", days = setOf(1, 2), enabled = false,
      pauseUntil = 99, skipDate = "2030-01-01", dismissTasks = listOf(TaskDraft(kind = "text")), vibrate = false)
    val routine = Routine("r", "Gym", AlarmDraft(id = "other", label = "Template", hour = "09", days = emptySet(),
      dismissTasks = listOf(TaskDraft(id = "t1", kind = "walk"), TaskDraft(id = "t2", kind = "memory")), vibrate = true, snoozeLimit = 3))
    val applied = routine.applyTo(target)
    assertEquals(listOf("a1", "Work", "06", "15"), listOf(applied.id, applied.label, applied.hour, applied.minute))
    assertEquals(setOf(1, 2), applied.days)
    assertFalse(applied.enabled)
    assertEquals(99L, applied.pauseUntil); assertEquals("2030-01-01", applied.skipDate)
    assertEquals(listOf("walk", "memory"), applied.dismissTasks.map { it.kind })
    assertTrue(applied.dismissTasks.none { it.id == "t1" || it.id == "t2" })
    assertTrue(applied.vibrate); assertEquals(3, applied.snoozeLimit)
  }

  @Test fun startersCoverTheOriginalPresets() {
    assertEquals(listOf("Quick start", "Get moving", "Strong wake-up", "Lights on"), StarterRoutines.map { it.name })
    assertTrue(StarterRoutines.all { it.starter })
    assertTrue(StarterRoutines.first { it.name == "Strong wake-up" }.alarm.escalationEnabled)
  }
}
