package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.StudioApplication
import com.example.data.PipelineProgress
import com.example.data.StudioRunStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class StudioRenderService : Service() {
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var collectorJob: Job? = null
  private var wakeLock: PowerManager.WakeLock? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val controller = (application as StudioApplication).studioController

    when (intent?.action) {
      ACTION_CANCEL -> {
        controller.cancelActiveTask()
        stopForegroundAndSelf()
        return START_NOT_STICKY
      }
      ACTION_STOP_SERVICE -> {
        stopForegroundAndSelf()
        return START_NOT_STICKY
      }
    }

    acquireWakeLock()
    val initialNotification = buildNotification(controller.progress.value)
    ServiceCompat.startForeground(
      this,
      NOTIFICATION_ID,
      initialNotification,
      ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
    )

    if (collectorJob == null) {
      collectorJob =
        serviceScope.launch {
          controller.progress.collectLatest { progress ->
            if (!progress.isRunning && controller.runStatus.value != StudioRunStatus.BUSY) {
              stopForegroundAndSelf()
            } else {
              val manager = getSystemService(NotificationManager::class.java)
              manager.notify(NOTIFICATION_ID, buildNotification(progress))
            }
          }
        }
    }

    return START_NOT_STICKY
  }

  private fun stopForegroundAndSelf() {
    releaseWakeLock()
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  override fun onDestroy() {
    collectorJob?.cancel()
    serviceScope.cancel()
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    releaseWakeLock()
    super.onDestroy()
  }

  private fun acquireWakeLock() {
    if (wakeLock?.isHeld == true) return
    val pm = getSystemService(PowerManager::class.java)
    wakeLock =
      pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MissingPageStudio::RenderWakeLock").apply {
        setReferenceCounted(false)
        acquire(4 * 60 * 60 * 1000L) // safety 4-hour max cap
      }
  }

  private fun releaseWakeLock() {
    val lock = wakeLock
    if (lock != null && lock.isHeld) {
      runCatching { lock.release() }
    }
    wakeLock = null
  }

  private fun buildNotification(progress: PipelineProgress): Notification {
    val openIntent =
      Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
    val openPendingIntent =
      PendingIntent.getActivity(
        this,
        0,
        openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val cancelIntent =
      Intent(this, StudioRenderService::class.java).apply { action = ACTION_CANCEL }
    val cancelPendingIntent =
      PendingIntent.getService(
        this,
        1,
        cancelIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val title =
      if (progress.taskTitle.isNotBlank()) {
        "${progress.taskTitle} (${progress.progressPercent}%)"
      } else {
        "The Missing Page Studio"
      }

    val contentLine =
      buildString {
        append(progress.stepText.ifBlank { "Working in background..." })
        if (progress.etaText.isNotBlank()) {
          append(" • ETA: ")
          append(progress.etaText)
        }
      }

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .setContentTitle(title)
      .setContentText(contentLine)
      .setStyle(NotificationCompat.BigTextStyle().bigText(contentLine))
      .setProgress(100, progress.progressPercent.coerceIn(0, 100), false)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setContentIntent(openPendingIntent)
      .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
      .build()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel =
        NotificationChannel(
          CHANNEL_ID,
          getString(R.string.notif_channel_render),
          NotificationManager.IMPORTANCE_LOW,
        ).apply {
          description = getString(R.string.notif_channel_render_desc)
        }
      getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
  }

  companion object {
    const val CHANNEL_ID = "missing_page_render_channel"
    const val NOTIFICATION_ID = 4107
    const val ACTION_CANCEL = "com.example.action.CANCEL_STUDIO_TASK"
    const val ACTION_STOP_SERVICE = "com.example.action.STOP_STUDIO_SERVICE"
  }
}
