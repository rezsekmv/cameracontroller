package hu.rezsekmv.cameracontroller.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import hu.rezsekmv.cameracontroller.R
import hu.rezsekmv.cameracontroller.data.ApiResult
import hu.rezsekmv.cameracontroller.data.CameraRepository
import hu.rezsekmv.cameracontroller.data.MotionDetectionStatus
import hu.rezsekmv.cameracontroller.data.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CameraWidgetService : Service() {

    companion object {
        private const val TAG = "CameraWidgetService"
        private const val FOREGROUND_NOTIFICATION_ID = 1001
        private const val MOTION_ALERT_NOTIFICATION_ID = 1002
        private const val FOREGROUND_CHANNEL_ID = "camera_widget_service_channel"
        private const val ALERT_CHANNEL_ID = "camera_motion_alert_channel"
        
        const val ACTION_TOGGLE_MOTION = "ACTION_TOGGLE_MOTION"
        const val ACTION_REFRESH_STATUS = "ACTION_REFRESH_STATUS"
        const val ACTION_REGISTER_RECEIVERS = "ACTION_REGISTER_RECEIVERS"
        const val ACTION_UNREGISTER_RECEIVERS = "ACTION_UNREGISTER_RECEIVERS"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val repository = CameraRepository()
    private lateinit var preferencesRepository: PreferencesRepository
    
    private var broadcastReceiver: BroadcastReceiver? = null
    private var lastStatus: MotionDetectionStatus? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        preferencesRepository = PreferencesRepository(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_REGISTER_RECEIVERS -> {
                registerBroadcastReceivers()
                stopSelf()
            }
            ACTION_UNREGISTER_RECEIVERS -> {
                unregisterBroadcastReceivers()
                stopSelf()
            }
            ACTION_REFRESH_STATUS -> {
                refreshCameraStatus()
                // Stop service after refresh
                serviceScope.launch {
                    kotlinx.coroutines.delay(5000) // Allow more time for refresh
                    stopSelf()
                }
            }
            ACTION_TOGGLE_MOTION -> {
                toggleMotionDetection()
                // Stop service after toggle
                serviceScope.launch {
                    kotlinx.coroutines.delay(3000) // Allow time for toggle operation
                    stopSelf()
                }
            }
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerBroadcastReceivers() {
        if (broadcastReceiver != null) return
        
        Log.d(TAG, "Registering broadcast receivers")
        
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.d(TAG, "Broadcast received: ${intent.action}")
                when (intent.action) {
                    Intent.ACTION_BOOT_COMPLETED -> {
                        Log.d(TAG, "Device booted - refreshing widget")
                        refreshCameraStatus()
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        Log.d(TAG, "Device unlocked - refreshing widget and checking motion status")
                        refreshCameraStatusAndNotify()
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        Log.d(TAG, "Screen turned on - refreshing widget")
                        refreshCameraStatus()
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        Log.d(TAG, "Screen turned off - no action needed")
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BOOT_COMPLETED)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        
        registerReceiver(broadcastReceiver, filter)
    }

    private fun unregisterBroadcastReceivers() {
        broadcastReceiver?.let {
            Log.d(TAG, "Unregistering broadcast receivers")
            unregisterReceiver(it)
            broadcastReceiver = null
        }
    }

    private fun refreshCameraStatus() {
        Log.d(TAG, "Refreshing camera status")
        performRefresh(showNotification = false)
    }
    
    private fun refreshCameraStatusAndNotify() {
        Log.d(TAG, "Refreshing camera status with notification check")
        performRefresh(showNotification = true)
    }
    
    private fun performRefresh(showNotification: Boolean) {
        serviceScope.launch {
            try {
                // Load settings and configure repository
                val settings = preferencesRepository.cameraSettings.first()
                val endpoint = "http://${settings.username}:${settings.password}@${settings.ipAddress}"
                repository.updateEndpoint(endpoint)
                
                val timeoutInt = settings.timeoutSeconds.toIntOrNull() ?: 2
                repository.updateConfiguration(
                    getConfigPath = settings.getConfigPath,
                    setConfigPath = settings.setConfigPath,
                    timeoutSeconds = timeoutInt
                )
                
                // Get current status
                val status = repository.getMotionDetectionStatus()
                lastStatus = status
                
                Log.d(TAG, "Status received: enabled=${status.isEnabled}, available=${status.isAvailable}, error=${status.errorMessage}")
                
                val (buttonText, statusText) = when {
                    !status.isAvailable -> {
                        val errorMsg = status.errorMessage ?: "Connection failed"
                        "❌ Failed" to errorMsg
                    }
                    status.isEnabled == true -> "🔴 Motion ON" to "Last: just now"
                    status.isEnabled == false -> "🟢 Motion OFF" to "Last: just now"
                    else -> "⚫ Unknown" to "Tap to open"
                }
                
                updateWidget(buttonText, statusText)
                
                // Show notification if motion is ON and we're checking after unlock
                if (showNotification && status.isAvailable && status.isEnabled == true) {
                    showMotionDetectionAlert()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing status", e)
                updateWidget("❌ Error", "Tap to open")
                // Update lastStatus to mark as unavailable
                lastStatus = MotionDetectionStatus(null, false, "Network error: ${e.message}")
            }
        }
    }

    private fun toggleMotionDetection() {
        Log.d(TAG, "Toggling motion detection")
        
        serviceScope.launch {
            try {
                val currentStatus = lastStatus
                if (currentStatus?.isEnabled == null) {
                    Log.w(TAG, "Cannot toggle - current status unknown, refreshing first")
                    // Don't show loading state, just refresh silently
                    
                    // First refresh the status
                    val settings = preferencesRepository.cameraSettings.first()
                    val endpoint = "http://${settings.username}:${settings.password}@${settings.ipAddress}"
                    repository.updateEndpoint(endpoint)
                    
                    val timeoutInt = settings.timeoutSeconds.toIntOrNull() ?: 2
                    repository.updateConfiguration(
                        getConfigPath = settings.getConfigPath,
                        setConfigPath = settings.setConfigPath,
                        timeoutSeconds = timeoutInt
                    )
                    
                    val refreshedStatus = repository.getMotionDetectionStatus()
                    lastStatus = refreshedStatus
                    
                    if (refreshedStatus.isEnabled == null) {
                        Log.e(TAG, "Still cannot determine status after refresh")
                        updateWidget("❌ Failed", "Connection error")
                        return@launch
                    }
                    
                    Log.d(TAG, "Status refreshed: enabled=${refreshedStatus.isEnabled}")
                }
                
                val statusToUse = lastStatus ?: return@launch
                
                // Update widget to show toggling state
                val newState = !(statusToUse.isEnabled ?: false)
                val toggleText = if (newState) "🔴 Turning ON..." else "🟢 Turning OFF..."
                // Don't update widget here - keep current color during toggle
                Log.d(TAG, "Starting toggle to: $newState")
                
                // Load settings and configure repository (if not already done)
                val settings = preferencesRepository.cameraSettings.first()
                val endpoint = "http://${settings.username}:${settings.password}@${settings.ipAddress}"
                repository.updateEndpoint(endpoint)
                
                val timeoutInt = settings.timeoutSeconds.toIntOrNull() ?: 2
                repository.updateConfiguration(
                    getConfigPath = settings.getConfigPath,
                    setConfigPath = settings.setConfigPath,
                    timeoutSeconds = timeoutInt
                )
                
                // Perform toggle
                val result = repository.setMotionDetectionStatus(newState)
                
                when (result) {
                    is ApiResult.Success -> {
                        Log.d(TAG, "Toggle successful, updating to final state")
                        // Update directly to the expected final state without showing loading
                        val finalText = if (newState) "🔴 Motion ON" else "🟢 Motion OFF"
                        updateWidget(finalText, "Last: just now")
                        
                        // Update lastStatus to reflect the new state
                        lastStatus = MotionDetectionStatus(newState, true, null)
                    }
                    is ApiResult.Error -> {
                        Log.e(TAG, "Toggle failed: ${result.message}")
                        updateWidget("❌ Failed", result.message)
                        // Update lastStatus to mark as unavailable
                        lastStatus = MotionDetectionStatus(null, false, result.message)
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling motion", e)
                updateWidget("❌ Error", "Toggle failed")
                // Update lastStatus to mark as unavailable
                lastStatus = MotionDetectionStatus(null, false, "Toggle error: ${e.message}")
                
                // Try to refresh after a delay to recover
                serviceScope.launch {
                    kotlinx.coroutines.delay(2000)
                    Log.d(TAG, "Attempting recovery refresh after toggle error")
                    refreshCameraStatus()
                }
            }
        }
    }

    private fun updateWidget(buttonText: String, statusText: String) {
        Log.d(TAG, "Updating widget: button='$buttonText', status='$statusText'")
        
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, CameraWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        
        val views = RemoteViews(packageName, R.layout.camera_widget)
        
        // Set background color based on status
        val backgroundRes = when {
            buttonText.contains("🔴") || buttonText.contains("ON") -> {
                Log.d(TAG, "Setting widget color to RED (Motion ON)")
                R.drawable.widget_button_red
            }
            buttonText.contains("🟢") || buttonText.contains("OFF") -> {
                Log.d(TAG, "Setting widget color to GREEN (Motion OFF)")
                R.drawable.widget_button_green
            }
            else -> {
                Log.d(TAG, "Setting widget color to GRAY (Failed/Error/Unknown) - buttonText: '$buttonText'")
                R.drawable.widget_button_gray // Failed, Error, Unknown states
            }
        }
        
        Log.d(TAG, "Widget update - Text: '$buttonText', Color: ${when(backgroundRes) {
            R.drawable.widget_button_red -> "RED"
            R.drawable.widget_button_green -> "GREEN" 
            else -> "GRAY"
        }}, AppWidgetIds: ${appWidgetIds.contentToString()}")
        
        // Update the FrameLayout background
        views.setInt(R.id.widget_container, "setBackgroundResource", backgroundRes)
        
        appWidgetManager.updateAppWidget(appWidgetIds, views)
    }

    private fun showMotionDetectionAlert() {
        Log.d(TAG, "Showing motion detection alert notification")
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("🔴 Motion Detection Active")
            .setContentText("Camera is monitoring for motion")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        
        notificationManager.notify(MOTION_ALERT_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        try {
            val notificationManager = getSystemService(NotificationManager::class.java)
            
            // Foreground service channel (low importance, silent)
            val serviceChannel = NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                "Camera Widget Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background service for camera widget"
                setShowBadge(false)
                setSound(null, null)
            }
            
            // Motion alert channel (default importance, can make sound)
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Motion Detection Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts when motion detection is active"
                setShowBadge(true)
            }
            
            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(alertChannel)
            Log.d(TAG, "Notification channels created successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create notification channels", e)
        }
    }

    private fun createForegroundNotification(): Notification {
        return try {
            NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
                .setContentTitle("Camera Widget")
                .setContentText("Initializing...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .setSilent(true)
                .setShowWhen(false)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create foreground notification with channel, using fallback", e)
            // Fallback notification without channel
            NotificationCompat.Builder(this)
                .setContentTitle("Camera Widget")
                .setContentText("Initializing...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .setSilent(true)
                .setShowWhen(false)
                .build()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        unregisterBroadcastReceivers()
    }
}
