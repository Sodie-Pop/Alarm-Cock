package com.taskalarm.engine

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.*
import android.net.Uri
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class RingingService : Service() {
  private var player: MediaPlayer? = null
  private var focus: AudioFocusRequest? = null
  private var tone: ToneGenerator? = null
  private var sessionId: String? = null
  private var muted = false
  private var guardRunning = false
  private var guardedVolume = 0
  private var restoreVolume: Int? = null
  private var scannerActive = false
  private var flashlightWakeLock: PowerManager.WakeLock? = null
  private var vibrating = false
  internal val ringingWakeLock by lazy {
    getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TaskAlarm:ringing")
      .apply { setReferenceCounted(false) }
  }
  /** While ringing: keep the CPU awake (fallback beep and volume guard are Handler loops) and push back the backstop. */
  private val heartbeat = object : Runnable {
    override fun run() {
      ringingWakeLock.acquire(WAKE_LOCK_MS)
      try { AlarmEngine(this@RingingService).armBackstop() } catch (_: RuntimeException) { }
      handler.postDelayed(this, HEARTBEAT_MS)
    }
  }
  private val handler = Handler(Looper.getMainLooper())
  private val flashlight by lazy { AlarmFlashlight(handler, CameraTorch(this)::set) { blocked ->
    synchronized(AlarmEngine.LOCK) {
      val store = AlarmStore(this)
      val sessions = store.sessions()
      if (sessions.firstOrNull()?.getString("sessionId") == sessionId) {
        sessions.first().put("flashlightBlocked", blocked); store.sessions(sessions)
      }
    }
  } }
  private val fallbackBeep = object : Runnable {
    override fun run() {
      if (!muted) tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1000)
      handler.postDelayed(this, 1500)
    }
  }
  private val volumeGuard = object : Runnable {
    override fun run() {
      if (!guardRunning) return
      val audio = getSystemService(AudioManager::class.java)
      try {
        check(!audio.isVolumeFixed) { "This device controls its own volume" }
        val level = guardedVolume.coerceIn(0, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM))
        if (audio.getStreamVolume(AudioManager.STREAM_ALARM) != level) audio.setStreamVolume(AudioManager.STREAM_ALARM, level, 0)
      } catch (_: RuntimeException) {
        guardRunning = false
        synchronized(AlarmEngine.LOCK) {
          val store = AlarmStore(this@RingingService)
          val sessions = store.sessions()
          if (sessions.firstOrNull()?.getString("sessionId") == sessionId) {
            sessions.first().put("volumeGuardBlocked", true); store.sessions(sessions)
          }
        }
      }
      if (guardRunning) handler.postDelayed(this, 250)
    }
  }
  private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()

  override fun onBind(intent: Intent?) = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val session = synchronized(AlarmEngine.LOCK) { AlarmStore(this).sessions().firstOrNull() }
    if (session == null) { releaseFlashlight(); stopHeartbeat(); stopSelf(); return START_NOT_STICKY }
    val alarm = session.getJSONObject("alarm")
    val manager = getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(NotificationChannel(CHANNEL, "Ringing alarms", NotificationManager.IMPORTANCE_HIGH).apply {
      description = "Active alarms and their task screen"; setSound(null, null); lockscreenVisibility = Notification.VISIBILITY_PUBLIC
    })
    val open = PendingIntent.getActivity(this, 0, AlarmEngine(this).launchIntent(),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val notification = NotificationCompat.Builder(this, CHANNEL)
      .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(alarm.getString("label"))
      .setContentText(if (getSystemService(UserManager::class.java).isUserUnlocked) "Open to complete your alarm tasks" else "Unlock your phone to complete your alarm tasks").setCategory(NotificationCompat.CATEGORY_ALARM)
      .setPriority(NotificationCompat.PRIORITY_MAX).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setOngoing(true).setContentIntent(open).setFullScreenIntent(open, true).build()
    val serviceType = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
    ServiceCompat.startForeground(this, 1001, notification, serviceType)
    FallbackAlert.cancel(this)
    handler.removeCallbacks(heartbeat); heartbeat.run()
    if (intent?.hasExtra("scannerActive") == true) scannerActive = intent.getBooleanExtra("scannerActive", false)
    if (sessionId != session.getString("sessionId")) {
      releaseFlashlight()
      releaseAudio(); sessionId = session.getString("sessionId")
      val audio = getSystemService(AudioManager::class.java)
      muted = session.optBoolean("muted")
      if (alarm.optBoolean("protectVolume")) {
        restoreVolume = session.optInt("originalVolume", audio.getStreamVolume(AudioManager.STREAM_ALARM))
        guardedVolume = session.optInt("guardVolume", VolumePolicy.guardedLevel(restoreVolume!!, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)))
        guardRunning = true; handler.post(volumeGuard)
      }
      if (alarm.optBoolean("vibrate")) startVibration()
      focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { }.build().also { audio.requestAudioFocus(it) }
      val sound = if (alarm.optBoolean("normalize")) alarm.optString("playbackUri", alarm.optString("soundUri")) else alarm.optString("soundUri")
      val chosen = sound.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
      if (!play(chosen ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))) {
        if (!play(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))) {
          tone = ToneGenerator(AudioManager.STREAM_ALARM, 100); handler.post(fallbackBeep)
        }
      }
    }
    val flashlightMode = alarm.optString("flashlightMode", "off")
    if (flashlightMode != "off" && !scannerActive) {
      if (flashlightWakeLock == null) flashlightWakeLock = getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TaskAlarm:flashlight").apply { acquire() }
    } else {
      flashlightWakeLock?.let { if (it.isHeld) it.release() }; flashlightWakeLock = null
    }
    flashlight.update(flashlightMode, scannerActive)
    muted = session.optBoolean("muted")
    player?.setVolume(if (muted) 0f else 1f, if (muted) 0f else 1f)
    if (muted) tone?.stopTone()
    return START_STICKY
  }

  private fun play(uri: Uri): Boolean = try {
    val candidate = MediaPlayer()
    player = candidate
    candidate.setAudioAttributes(attributes)
    candidate.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
    candidate.setDataSource(this, uri)
    candidate.isLooping = true
    candidate.setVolume(if (muted) 0f else 1f, if (muted) 0f else 1f)
    candidate.setOnErrorListener { _, _, _ ->
      candidate.release(); player = null
      tone = ToneGenerator(AudioManager.STREAM_ALARM, 100); handler.post(fallbackBeep); true
    }
    candidate.prepare(); candidate.start(); true
  } catch (_: Exception) { player?.release(); player = null; false }

  /** Repeating pattern on the alarm usage; muting sound leaves it running. */
  private fun startVibration() {
    val vibrator = if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java).defaultVibrator
      else @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
    if (!vibrator.hasVibrator()) return
    val effect = VibrationEffect.createWaveform(VIBRATION_PATTERN, 0)
    if (Build.VERSION.SDK_INT >= 33) vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
    else @Suppress("DEPRECATION") vibrator.vibrate(effect, attributes)
    vibrating = true
  }
  private fun stopVibration() {
    if (!vibrating) return
    val vibrator = if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java).defaultVibrator
      else @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
    vibrator.cancel(); vibrating = false
  }

  private fun releaseAudio() {
    stopVibration()
    handler.removeCallbacks(volumeGuard); guardRunning = false
    handler.removeCallbacks(fallbackBeep); tone?.release(); tone = null
    player?.release(); player = null
    focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }; focus = null
    restoreVolume?.let {
      try { getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_ALARM, it, 0) } catch (_: RuntimeException) { }
    }
    restoreVolume = null
  }

  private fun releaseFlashlight() {
    flashlight.close()
    flashlightWakeLock?.let { if (it.isHeld) it.release() }; flashlightWakeLock = null
  }

  private fun stopHeartbeat() {
    handler.removeCallbacks(heartbeat)
    if (ringingWakeLock.isHeld) ringingWakeLock.release()
  }

  override fun onTaskRemoved(rootIntent: Intent?) {
    // Some devices kill the process shortly after the task is swiped away; ring again soon if that happens.
    if (synchronized(AlarmEngine.LOCK) { AlarmStore(this).sessions().isNotEmpty() }) AlarmEngine(this).armBackstop(TASK_REMOVED_BACKSTOP_MS)
    super.onTaskRemoved(rootIntent)
  }

  override fun onDestroy() {
    stopHeartbeat(); releaseFlashlight(); releaseAudio()
    // Keep the backstop while an alarm is still active; otherwise clear one a late heartbeat may have re-armed.
    if (synchronized(AlarmEngine.LOCK) { AlarmStore(this).sessions().isEmpty() }) AlarmEngine(this).cancelBackstop()
    super.onDestroy()
  }
  companion object {
    const val CHANNEL = "task-alarm-ringing-v1"
    const val HEARTBEAT_MS = 30_000L
    const val WAKE_LOCK_MS = 10 * 60_000L
    const val TASK_REMOVED_BACKSTOP_MS = 5_000L
    val VIBRATION_PATTERN = longArrayOf(0, 800, 600)
  }
}
