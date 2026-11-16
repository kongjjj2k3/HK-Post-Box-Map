package com.kongjjj.hkpostboxchecker.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.kongjjj.hkpostboxchecker.MainActivity
import com.kongjjj.hkpostboxchecker.R
import com.kongjjj.hkpostboxchecker.model.TrackPoint
import com.kongjjj.hkpostboxchecker.model.TrackRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TrackingState(
    val isTracking: Boolean = false,
    val points: List<TrackPoint> = emptyList(),
    val distanceMeters: Double = 0.0,
    val startTime: Long = 0L,
    val durationSeconds: Long = 0L
)

class GpsTrackingService : Service() {

    private val binder = LocalBinder()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val _trackingState = MutableStateFlow(TrackingState())
    val trackingState: StateFlow<TrackingState> = _trackingState.asStateFlow()

    private var lastLocation: Location? = null
    private var serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var timerJob: Job? = null

    companion object {
        const val CHANNEL_ID = "gps_tracking_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_START = "ACTION_START_TRACKING"
        const val ACTION_STOP = "ACTION_STOP_TRACKING"
    }

    inner class LocalBinder : Binder() {
        fun getService(): GpsTrackingService = this@GpsTrackingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HKPostBoxChecker::GpsWakeLock")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTrackingInternal()
            ACTION_STOP -> stopTrackingInternal()
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startTrackingInternal() {
        if (_trackingState.value.isTracking) return

        wakeLock?.acquire(10 * 60 * 1000L /* 10 minutes timeout buffer */)

        val startTime = System.currentTimeMillis()
        lastLocation = null

        _trackingState.value = TrackingState(
            isTracking = true,
            points = emptyList(),
            distanceMeters = 0.0,
            startTime = startTime,
            durationSeconds = 0L
        )

        startForegroundServiceNotification()
        startTimer()

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .setMinUpdateDistanceMeters(2.0f)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    processNewLocation(location)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, mainLooper)
    }

    private fun processNewLocation(location: Location) {
        val currentState = _trackingState.value
        if (!currentState.isTracking) return

        var addedDistance = 0.0
        lastLocation?.let { prev ->
            addedDistance = prev.distanceTo(location).toDouble()
        }

        // Avoid GPS jitter noise (only add distance if moved > 1.5m)
        if (lastLocation == null || addedDistance >= 1.5) {
            lastLocation = location
            val newPoint = TrackPoint(
                latitude = location.latitude,
                longitude = location.longitude,
                timestamp = location.time,
                altitude = location.altitude
            )

            val newPoints = currentState.points + newPoint
            val newDist = currentState.distanceMeters + addedDistance

            _trackingState.value = currentState.copy(
                points = newPoints,
                distanceMeters = newDist
            )

            updateNotification()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (_trackingState.value.isTracking) {
                delay(1000L)
                val current = _trackingState.value
                val seconds = (System.currentTimeMillis() - current.startTime) / 1000
                _trackingState.value = current.copy(durationSeconds = seconds)
                updateNotification()
            }
        }
    }

    fun stopTrackingInternal(): TrackRecord? {
        val state = _trackingState.value
        if (!state.isTracking) return null

        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationCallback = null
        timerJob?.cancel()

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }

        val endTime = System.currentTimeMillis()
        val record = if (state.points.size >= 2) {
            TrackRecord(
                id = "track_${state.startTime}",
                title = "軌跡 " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(
                    Date(state.startTime)
                ),
                startTime = state.startTime,
                endTime = endTime,
                distanceMeters = state.distanceMeters,
                points = state.points
            )
        } else {
            null
        }

        _trackingState.value = TrackingState(isTracking = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        return record
    }

    private fun startForegroundServiceNotification() {
        val notification = buildNotification("已開始記錄 GPS 軌跡...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val state = _trackingState.value
        if (!state.isTracking) return

        val distStr = if (state.distanceMeters >= 1000) {
            String.format(Locale.getDefault(), "%.2f km", state.distanceMeters / 1000.0)
        } else {
            String.format(Locale.getDefault(), "%.0f m", state.distanceMeters)
        }

        val mins = state.durationSeconds / 60
        val secs = state.durationSeconds % 60
        val timeStr = String.format(Locale.getDefault(), "%02d:%02d", mins, secs)

        val text = "已記錄: $distStr | 時間: $timeStr | 點數: ${state.points.size}"
        val notification = buildNotification(text)

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GPS 行山/路徑軌跡記錄中")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_record_start)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GPS Tracking Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "GPS Track Recording Service"
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }
}
