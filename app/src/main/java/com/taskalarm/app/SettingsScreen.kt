package com.taskalarm.app

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.json.JSONObject

internal enum class AccessStatus { READY, NEEDED, SUGGESTED }

/** One permission or device setting as shown on the settings page. */
internal data class AccessItem(
  @DrawableRes val icon: Int, val title: String, val why: String, val status: AccessStatus, val statusText: String,
  val action: String? = null, val actionLabel: String = "Allow",
)

internal data class AccessGroups(val required: List<AccessItem>, val recommended: List<AccessItem>, val optional: List<AccessItem>) {
  /** Anything that can stop an alarm sounding, including restricted battery or a muted alarm stream. */
  val blocking get() = (required + recommended).count { it.status == AccessStatus.NEEDED }
  val attention get() = (required + recommended).count { it.status != AccessStatus.READY }
}

internal fun accessGroups(readiness: JSONObject?): AccessGroups {
  val r = readiness ?: JSONObject()
  fun ok(key: String) = r.optBoolean(key, true)
  fun item(icon: Int, title: String, why: String, ready: Boolean, readyText: String, action: String?, missing: AccessStatus = AccessStatus.NEEDED,
    missingText: String = "Not allowed", actionLabel: String = "Allow") =
    AccessItem(icon, title, why, if (ready) AccessStatus.READY else missing, if (ready) readyText else missingText, if (ready) null else action, actionLabel)

  val required = listOf(
    item(R.drawable.ic_alarm_clock, "Alarms & reminders", "Lets alarms ring at the exact minute, even in battery saver.", ok("exactAlarms"),
      if (r.optBoolean("exactAlarmsAutomatic")) "Allowed automatically" else "Allowed", "exact"),
    item(R.drawable.ic_notifications, "Alarm notifications", "Shows the ringing alarm and its tasks.", ok("notifications"), "Allowed", "notifications"),
    item(R.drawable.ic_fullscreen, "Show over the lock screen", "Opens the task screen on top of the lock screen when an alarm rings.", ok("fullScreen"),
      if (r.optBoolean("fullScreenAutomatic")) "Allowed automatically" else "Allowed", "fullScreen"),
  )
  val volume = r.optInt("alarmVolume", 1); val max = r.optInt("alarmVolumeMax", 1).coerceAtLeast(1)
  val restricted = r.optBoolean("backgroundRestricted")
  val recommended = listOf(
    if (restricted) AccessItem(R.drawable.ic_battery_full, "Battery usage", "Restricted battery use can stop alarms from ringing when the app is closed.",
      AccessStatus.NEEDED, "Restricted — alarms may not ring", "appSettings", "Fix")
    else item(R.drawable.ic_battery_full, "Battery usage", "Unrestricted keeps Android from delaying the alarm. In app settings, open App battery usage.",
      ok("batteryUnrestricted"), "Unrestricted", "appSettings", AccessStatus.SUGGESTED, "Optimized", "Change"),
    AccessItem(R.drawable.ic_volume_up, "Alarm volume", "Your phone's alarm volume sets how loud alarms play.",
      when { volume == 0 -> AccessStatus.NEEDED; volume * 3 < max -> AccessStatus.SUGGESTED; else -> AccessStatus.READY },
      if (volume == 0) "Muted — alarms will be silent" else "${volume * 100 / max}%", if (volume * 3 < max) "volume" else null, "Adjust"),
    item(R.drawable.ic_wb_sunny, "Wake-up check notifications", "Asks if you're up after you dismiss. Only used by alarms with wake-up checks.",
      ok("wakeNotifications"), "Allowed", "wakeNotifications", AccessStatus.SUGGESTED, "Turned off"),
  )
  val optional = listOf(
    item(R.drawable.ic_photo_camera, "Camera", if (r.optBoolean("flashlightAvailable", true)) "For barcode tasks and the flashlight. Asked for when you first use them."
      else "For barcode tasks. Asked for when you first use them.", r.optBoolean("cameraPermission"), "Allowed", "camera", AccessStatus.SUGGESTED, "Not yet allowed"),
    item(R.drawable.ic_directions_walk, "Physical activity", "For walking tasks, which count steps.", r.optBoolean("activityPermission", true),
      "Allowed", "activity", AccessStatus.SUGGESTED, "Not yet allowed"),
  )
  return AccessGroups(required, recommended, optional)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(readiness: JSONObject?, version: String, onBack: () -> Unit, onAccess: (String) -> Unit,
  deliveryTest: JSONObject? = null, now: Long = System.currentTimeMillis(), onTestDelivery: (Int) -> Unit = {}) {
  val groups = accessGroups(readiness)
  Scaffold(topBar = {
    TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Ico(R.drawable.ic_arrow_back, "Back") } },
      colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface))
  }) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)) {
      ReadySummary(groups)
      SectionHeader("Required", "Alarms can't ring properly without these.")
      SettingsCard { groups.required.forEach { AccessRow(it, onAccess) } }
      SectionHeader("Recommended", "Make alarms more dependable.")
      SettingsCard { groups.recommended.forEach { AccessRow(it, onAccess) } }
      SectionHeader("Optional", "Only needed for some features.")
      SettingsCard { groups.optional.forEach { AccessRow(it, onAccess) } }
      SectionHeader("Delivery test", "Checks that an alarm reaches you with the phone locked.")
      SettingsCard {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Lock the phone after starting and wait. For the restart test, restart the phone and leave it at the lock screen.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          deliveryStatus(deliveryTest, now)?.let { (text, ok) ->
            Notice(text, if (ok) R.drawable.ic_check_circle else R.drawable.ic_info, if (ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onTestDelivery(30) }) { Text("Ring in 30 s") }
            OutlinedButton(onClick = { onTestDelivery(120) }) { Text("Restart test (2 min)") }
          }
        }
      }
      SectionHeader("About")
      SettingsCard {
        NavRow("App info", "Task Alarm $version · Android permissions and storage", R.drawable.ic_info) { onAccess("appSettings") }
      }
      Spacer(Modifier.height(24.dp))
    }
  }
}

private fun deliveryStatus(test: JSONObject?, now: Long): Pair<String, Boolean>? {
  test ?: return null
  val at = test.optLong("scheduledAt")
  val time = shortDate(at) + " " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(at))
  return when (test.optString("status")) {
    "acknowledged" -> "Last test ($time) rang and was dismissed." to true
    "delivered" -> "Last test ($time) was delivered." to true
    "scheduled" -> if (now > at + 60_000) "The test was due at $time but no delivery was recorded." to false else "Test scheduled for $time." to false
    else -> null
  }
}

@Composable
private fun ReadySummary(groups: AccessGroups) {
  val ready = groups.blocking == 0
  val container = if (ready) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
  val content = if (ready) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer
  Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = container, contentColor = content) {
    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      Ico(if (ready) R.drawable.ic_check_circle else R.drawable.ic_warning, size = 32.dp)
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(if (ready) "Alarms are ready to ring" else if (groups.blocking == 1) "1 setting needs attention" else "${groups.blocking} settings need attention",
          style = MaterialTheme.typography.titleMedium)
        Text(when {
          !ready -> "Fix the items marked below so your alarms can ring."
          groups.attention > 0 -> "A few recommended settings could make them more dependable."
          else -> "Everything Task Alarm needs is set up."
        }, style = MaterialTheme.typography.bodyMedium)
      }
    }
  }
}

@Composable
private fun AccessRow(item: AccessItem, onAccess: (String) -> Unit) {
  val (statusIcon, statusColor) = statusStyle(item.status)
  Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
    Ico(item.icon, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(item.title, style = MaterialTheme.typography.bodyLarge)
      Text(item.why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
        Ico(statusIcon, tint = statusColor, size = 16.dp)
        Text(item.statusText, style = MaterialTheme.typography.labelLarge, color = statusColor)
      }
    }
    val action = item.action
    if (action != null) {
      if (item.status == AccessStatus.NEEDED) Button(onClick = { onAccess(action) }) { Text(item.actionLabel) }
      else FilledTonalButton(onClick = { onAccess(action) }) { Text(item.actionLabel) }
    }
  }
}

@Composable
private fun statusStyle(status: AccessStatus): Pair<Int, Color> = when (status) {
  AccessStatus.READY -> R.drawable.ic_check_circle to MaterialTheme.colorScheme.tertiary
  AccessStatus.NEEDED -> R.drawable.ic_warning to MaterialTheme.colorScheme.error
  AccessStatus.SUGGESTED -> R.drawable.ic_info to MaterialTheme.colorScheme.primary
}
