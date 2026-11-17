package com.taskalarm.app

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
internal fun Ico(@DrawableRes id: Int, contentDescription: String? = null, modifier: Modifier = Modifier,
  tint: Color = LocalContentColor.current, size: Dp = 24.dp) {
  Icon(painterResource(id), contentDescription, modifier.size(size), tint = tint)
}

/** Icon inside a tinted circle; used to anchor rows and cards. */
@Composable
internal fun IconBadge(@DrawableRes id: Int, container: Color = MaterialTheme.colorScheme.secondaryContainer,
  content: Color = MaterialTheme.colorScheme.onSecondaryContainer, size: Dp = 40.dp) {
  Box(Modifier.size(size).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
    Ico(id, tint = content, size = size * 0.55f)
  }
}

@Composable
internal fun SectionHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
  Column(modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

/** Grouped settings surface. */
@Composable
internal fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
    Column(Modifier.padding(vertical = 8.dp), content = content)
  }
}

/** Whole row toggles (large touch target, single accessibility node). */
@Composable
internal fun SwitchRow(title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true,
  @DrawableRes icon: Int? = null, onChange: (Boolean) -> Unit) {
  Row(Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange).padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
    if (icon != null) Ico(icon, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(title, style = MaterialTheme.typography.bodyLarge)
      if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Switch(checked = checked, onCheckedChange = null, enabled = enabled)
  }
}

@Composable
internal fun NavRow(title: String, subtitle: String?, @DrawableRes icon: Int?, enabled: Boolean = true,
  subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant, trailing: @Composable (() -> Unit)? = null, onClick: () -> Unit) {
  Row(Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
    if (icon != null) Ico(icon, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(title, style = MaterialTheme.typography.bodyLarge)
      if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = subtitleColor)
    }
    if (trailing != null) trailing() else Ico(R.drawable.ic_chevron_right, tint = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

/** Non-interactive summary chip ("Math ×3", "Snooze 5 min"). */
@Composable
internal fun InfoPill(@DrawableRes icon: Int, text: String, contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
  container: Color = MaterialTheme.colorScheme.surfaceContainerHighest) {
  Row(Modifier.clip(CircleShape).background(container).padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    Ico(icon, tint = contentColor, size = 16.dp)
    Text(text, style = MaterialTheme.typography.labelMedium, color = contentColor)
  }
}

/** Compact "− value +" control for bounded numbers. */
@Composable
internal fun Stepper(label: String, value: Int, display: String, canDecrease: Boolean, canIncrease: Boolean,
  onDecrease: () -> Unit, onIncrease: () -> Unit, modifier: Modifier = Modifier, horizontalPadding: Dp = 16.dp) {
  Row(modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    FilledTonalIconButton(onClick = onDecrease, enabled = canDecrease) { Ico(R.drawable.ic_remove, "Decrease $label") }
    Text(display, style = MaterialTheme.typography.titleMedium, modifier = Modifier.widthIn(min = 72.dp).padding(horizontal = 4.dp),
      textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    FilledTonalIconButton(onClick = onIncrease, enabled = canIncrease) { Ico(R.drawable.ic_add, "Increase $label") }
  }
}

/** Minute steps that stay quick to adjust across the 1–1440 range. */
internal fun nextMinutes(value: Int, up: Boolean): Int {
  val steps = listOf(1, 2, 3, 4, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240, 360, 480, 720, 1080, 1440)
  return if (up) steps.firstOrNull { it > value } ?: 1440 else steps.lastOrNull { it < value } ?: 1
}

internal fun minutesText(minutes: Int) = if (minutes < 60) "$minutes min"
  else if (minutes % 60 == 0) "${minutes / 60} hr" else "${minutes / 60} hr ${minutes % 60} min"

/** Inline warning/notice line. */
@Composable
internal fun Notice(text: String, @DrawableRes icon: Int = R.drawable.ic_warning, color: Color = MaterialTheme.colorScheme.error) {
  Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Ico(icon, tint = color, size = 18.dp, modifier = Modifier.padding(top = 1.dp))
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
  }
}

/** Missing Android access, presented as one card with an Allow button per item. */
@Composable
internal fun SetupCard(readiness: JSONObject?, onAccess: (String) -> Unit) {
  if (readiness == null) return
  val missing = listOf(
    Triple("exactAlarms", "Alarms & reminders", "exact"),
    Triple("notifications", "Alarm notifications", "notifications"),
    Triple("wakeNotifications", "Wake-up check notifications", "wakeNotifications"),
    Triple("fullScreen", "Show over the lock screen", "fullScreen"),
  ).filter { !readiness.optBoolean(it.first, true) }
  if (missing.isEmpty()) return
  Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer,
    contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Ico(R.drawable.ic_notifications)
        Text("Finish setting up", style = MaterialTheme.typography.titleMedium)
      }
      Text("Android needs these so your alarms can ring and show their tasks.", style = MaterialTheme.typography.bodyMedium)
      missing.forEach { (_, label, kind) ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
          Button(onClick = { onAccess(kind) }) { Text("Allow") }
        }
      }
    }
  }
}
