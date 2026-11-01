package com.abel.theftguard

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.Location
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GuardService : Service() {

    companion object {
        const val TAG = "TheftGuard"
        const val ACTION_FIND = "/find"
        const val ACTION_WHERE = "/where"
        const val ACTION_STOP = "/stop"
        const val EXTRA_SENDER = "sender"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_LOCAL_TEST = "local_test"

        private const val CHANNEL_ID = "guard"
        private const val ALERT_CHANNEL_ID = "guard_alert"
        private const val NOTIF_ID = 1
        private const val ALERT_NOTIF_ID = 2
        private const val UPDATE_INTERVAL_MS = 60_000L
        private const val MAX_UPDATES = 30
        private const val MAX_RUNTIME_MS = 2 * 60 * 60_000L

        @Volatile
        var alarmActive = false
    }

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var torchId: String? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var originalAlarmVolume: Int? = null
    private var updatesSent = 0
    private var replyTo: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground(intent?.action)
        val sender = intent?.getStringExtra(EXTRA_SENDER)
        val message = intent?.getStringExtra(EXTRA_MESSAGE)
        val localTest = intent?.getBooleanExtra(EXTRA_LOCAL_TEST, false) ?: false
        when (intent?.action) {
            ACTION_STOP -> stopEverything()
            ACTION_WHERE -> sendLocation(sender) { if (!alarmActive) stopSelf() }
            ACTION_FIND -> startFind(sender, message, localTest)
            else -> if (!alarmActive) stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    // ---------- foreground ----------

    private fun startAsForeground(action: String?) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Theft Guard", NotificationManager.IMPORTANCE_LOW)
        )
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Theft Guard")
            .setContentText(
                if (action == ACTION_FIND) "Lost mode active; sending SMS location updates"
                else "Sending an SMS location response"
            )
            .setOngoing(true)
            .build()
        val both = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, n, both)
        } catch (e: SecurityException) {
            // Location type needs location permission; fall back so the alarm still works.
            ServiceCompat.startForeground(this, NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        }
    }

    // ---------- /find ----------

    private fun startFind(sender: String?, message: String?, localTest: Boolean) {
        replyTo = if (localTest) null else sender
        acquireWakeLock()
        if (!alarmActive) {
            alarmActive = true
            startAlarm()
            startStrobe()
            handler.postDelayed({ stopEverything() }, MAX_RUNTIME_MS)
        }
        val backup = Prefs.backupNumber(this)
        val text = message ?: ("This phone is lost. Please return it." + (backup?.let { " Call $it." } ?: ""))
        showLostScreen(text)
        updatesSent = 0
        handler.removeCallbacks(updateRunnable)
        if (localTest) return
        handler.post(updateRunnable)
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (!alarmActive || updatesSent >= MAX_UPDATES) return
            updatesSent++
            sendLocation(replyTo) { }
            handler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    // ---------- alarm ----------

    private fun startAlarm() {
        val am = getSystemService(AudioManager::class.java)
        if (originalAlarmVolume == null) {
            originalAlarmVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
        }
        val pinVolume = object : Runnable {
            override fun run() {
                if (!alarmActive) return
                try {
                    am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
                } catch (e: Exception) {
                    Log.w(TAG, "volume set failed", e)
                }
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(pinVolume)

        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@GuardService, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "alarm failed", e)
        }
    }

    // ---------- torch strobe ----------

    private fun startStrobe() {
        try {
            val cm = getSystemService(CameraManager::class.java)
            torchId = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
        } catch (e: Exception) {
            return
        }
        var on = false
        val strobe = object : Runnable {
            override fun run() {
                if (!alarmActive) return
                on = !on
                setTorch(on)
                handler.postDelayed(this, 500)
            }
        }
        handler.post(strobe)
    }

    private fun setTorch(on: Boolean) {
        val id = torchId ?: return
        try {
            getSystemService(CameraManager::class.java).setTorchMode(id, on)
        } catch (e: Exception) {
            // camera busy or unavailable
        }
    }

    // ---------- lost screen ----------

    private fun showLostScreen(message: String) {
        val i = Intent(this, LostActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_MESSAGE, message)

        // Full-screen-intent notification as a fallback when direct background launch is blocked.
        val pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL_ID, "Lost mode", NotificationManager.IMPORTANCE_HIGH)
        )
        val n = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("This phone is lost")
            .setContentText(message)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setFullScreenIntent(pi, true)
            .setOngoing(true)
            .build()
        nm.notify(ALERT_NOTIF_ID, n)

        try {
            startActivity(i)
        } catch (e: Exception) {
            Log.w(TAG, "direct launch blocked", e)
        }
    }

    // ---------- location + SMS ----------

    private fun recipients(to: String?): Set<String> =
        listOfNotNull(to, Prefs.backupNumber(this)).filter { it.isNotBlank() }.toSet()

    private fun sendLocation(to: String?, done: () -> Unit) {
        fetchLocation { loc ->
            val time = SimpleDateFormat("HH:mm", Locale.US).format(Date())
            val battery = batteryPercent()
            val text = if (loc != null) {
                "Phone location ($time): https://maps.google.com/?q=${loc.latitude},${loc.longitude} " +
                    "(+/-${loc.accuracy.toInt()}m). Battery $battery%."
            } else {
                "Phone is alive but has no location fix ($time). Battery $battery%."
            }
            recipients(to).forEach { sms(it, text) }
            done()
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocation(callback: (Location?) -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            callback(null)
            return
        }
        val client = LocationServices.getFusedLocationProviderClient(this)
        val cts = CancellationTokenSource()
        var finished = false
        val finish: (Location?) -> Unit = { loc ->
            if (!finished) {
                finished = true
                callback(loc)
            }
        }
        val fallback: () -> Unit = {
            client.lastLocation
                .addOnSuccessListener { finish(it) }
                .addOnFailureListener { finish(null) }
            Unit
        }
        handler.postDelayed({ cts.cancel() }, 25_000)
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { loc -> if (loc != null) finish(loc) else fallback() }
            .addOnFailureListener { fallback() }
            .addOnCanceledListener { fallback() }
    }

    private fun sms(to: String, text: String) {
        try {
            val sm = if (Build.VERSION.SDK_INT >= 31) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION") SmsManager.getDefault()
            }
            sm.sendMultipartTextMessage(to, null, sm.divideMessage(text), null, null)
        } catch (e: Exception) {
            Log.e(TAG, "sms failed", e)
        }
    }

    private fun batteryPercent(): Int =
        getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    // ---------- lifecycle ----------

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "theftguard:alarm").apply {
            acquire(MAX_RUNTIME_MS)
        }
    }

    private fun cleanup() {
        alarmActive = false
        handler.removeCallbacksAndMessages(null)
        player?.let {
            try { it.stop() } catch (e: Exception) { }
            it.release()
        }
        player = null
        setTorch(false)
        originalAlarmVolume?.let { previousVolume ->
            try {
                getSystemService(AudioManager::class.java)
                    .setStreamVolume(AudioManager.STREAM_ALARM, previousVolume, 0)
            } catch (e: Exception) {
                Log.w(TAG, "alarm volume restore failed", e)
            }
        }
        originalAlarmVolume = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        getSystemService(NotificationManager::class.java).cancel(ALERT_NOTIF_ID)
        LostActivity.current?.finish()
    }

    private fun stopEverything() {
        cleanup()
        stopSelf()
    }
}
