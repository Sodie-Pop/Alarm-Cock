package com.taskalarm.app

import android.content.ComponentName
import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ManifestTest {
  @Test fun appIsLockedToPortrait() {
    val context = RuntimeEnvironment.getApplication()
    val info = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
    assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, info.screenOrientation)
  }
}
