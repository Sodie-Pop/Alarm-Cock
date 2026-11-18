package com.taskalarm.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AccessGroupsTest {
  private fun all(readiness: JSONObject) = accessGroups(readiness).let { it.required + it.recommended + it.optional }.associateBy { it.title }

  @Test fun everythingGrantedNeedsNoAttention() {
    val groups = accessGroups(JSONObject().put("batteryUnrestricted", true).put("cameraPermission", true).put("alarmVolume", 7).put("alarmVolumeMax", 7))
    assertEquals(0, groups.blocking)
    assertEquals(0, groups.attention)
    assertTrue((groups.required + groups.recommended + groups.optional).all { it.status == AccessStatus.READY && it.action == null })
  }

  @Test fun missingRequiredAccessBlocksAndOffersTheRightAction() {
    val items = all(JSONObject().put("notifications", false).put("fullScreen", false).put("batteryUnrestricted", true).put("alarmVolume", 7).put("alarmVolumeMax", 7))
    assertEquals(AccessStatus.NEEDED, items.getValue("Alarm notifications").status)
    assertEquals("notifications", items.getValue("Alarm notifications").action)
    assertEquals("fullScreen", items.getValue("Show over the lock screen").action)
    assertEquals(2, accessGroups(JSONObject().put("notifications", false).put("fullScreen", false)).blocking)
  }

  @Test fun restrictedBatteryAndMutedVolumeAreFlaggedAsProblems() {
    val items = all(JSONObject().put("backgroundRestricted", true).put("alarmVolume", 0).put("alarmVolumeMax", 7))
    assertEquals(AccessStatus.NEEDED, items.getValue("Battery usage").status)
    assertEquals(AccessStatus.NEEDED, items.getValue("Alarm volume").status)
    assertEquals("volume", items.getValue("Alarm volume").action)
    assertEquals(1, accessGroups(JSONObject().put("backgroundRestricted", true)).blocking)
  }

  @Test fun optimizedBatteryLowVolumeAndCameraAreOnlySuggestions() {
    val items = all(JSONObject().put("batteryUnrestricted", false).put("alarmVolume", 2).put("alarmVolumeMax", 7).put("cameraPermission", false))
    assertEquals(AccessStatus.SUGGESTED, items.getValue("Battery usage").status)
    assertEquals(AccessStatus.SUGGESTED, items.getValue("Alarm volume").status)
    assertEquals(AccessStatus.SUGGESTED, items.getValue("Camera").status)
  }
}
