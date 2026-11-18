package com.taskalarm.engine

import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

internal object Timing {
  /**
   * Next ring strictly after [now] (and at or after [pauseUntil]) on an allowed weekday, skipping [skipDate]
   * (a local ISO date). DST gaps and overlaps are resolved by the phone's current zone.
   */
  fun next(hour: Int, minute: Int, days: Set<Int>, now: ZonedDateTime = ZonedDateTime.now(), pauseUntil: Long = 0,
    skipDate: String? = null): Long {
    require(hour in 0..23 && minute in 0..59 && days.all { it in 1..7 })
    val boundary = if (pauseUntil - 1 > now.toInstant().toEpochMilli()) Instant.ofEpochMilli(pauseUntil - 1).atZone(now.zone) else now
    val skip = skipDate?.takeIf { it.isNotEmpty() }?.let(LocalDate::parse)
    for (offset in 0L..400L) {
      val day = boundary.toLocalDate().plusDays(offset)
      // atZone handles daylight-saving gaps and overlaps in the phone's current zone.
      val candidate = day.atTime(hour, minute).atZone(now.zone)
      if (candidate.toInstant().isAfter(boundary.toInstant()) && day != skip && (days.isEmpty() || day.dayOfWeek.value in days)) {
        return candidate.toInstant().toEpochMilli()
      }
    }
    error("No next alarm occurrence")
  }
}
