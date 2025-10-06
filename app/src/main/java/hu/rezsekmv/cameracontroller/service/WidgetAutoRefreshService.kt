package hu.rezsekmv.cameracontroller.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import hu.rezsekmv.cameracontroller.MainActivity
import hu.rezsekmv.cameracontroller.R
import hu.rezsekmv.cameracontroller.data.PreferencesRepository
import hu.rezsekmv.cameracontroller.widget.CameraWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WidgetAutoRefreshService : Service() {
    
    companion object {
        private const val TAG = "WidgetAutoRefreshService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "widget_auto_refresh_channel"
        private const val CHANNEL_NAME = "Widget Auto Refresh"
        private const val CHANNEL_DESCRIPTION = "Service for automatic widget refresh on phone unlock"
        
        const val ACTION_START_SERVICE = "hu.rezsekmv.cameracontroller.START_AUTO_REFRESH"
        const val ACTION_STOP_SERVICE = "hu.rezsekmv.cameracontroller.STOP_AUTO_REFRESH"
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var preferencesRepository: PreferencesRepository
    private var userPresentReceiver: BroadcastReceiver? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        preferencesRepository = PreferencesRepository(this)
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started with action: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START_SERVICE -> {
                startForeground(NOTIFICATION_ID, createNotification())
                registerUserPresentReceiver()
                Log.d(TAG, "Foreground service started and receiver registered")
            }
            ACTION_STOP_SERVICE -> {
                stopForegroundService()
            }
        }
        
        return START_STICKY // Restart if killed by system
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        unregisterUserPresentReceiver()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Camera Widget Auto Refresh")
            .setContentText("Monitoring phone unlock for automatic widget refresh")
            .setSmallIcon(R.drawable.ic_camera_widget)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
    
    private fun registerUserPresentReceiver() {
        if (userPresentReceiver == null) {
            userPresentReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    Log.d(TAG, "Received broadcast: ${intent.action}")
                    if (intent.action == Intent.ACTION_USER_PRESENT) {
                        Log.d(TAG, "Phone unlocked, checking WiFi and refreshing widget")
                        checkWifiAndRefreshWidget()
                    }
                }
            }
            
            val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
            registerReceiver(userPresentReceiver, filter)
            Log.d(TAG, "User present receiver registered")
        }
    }
    
    private fun unregisterUserPresentReceiver() {
        userPresentReceiver?.let {
            try {
                unregisterReceiver(it)
                Log.d(TAG, "User present receiver unregistered")
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Receiver was not registered", e)
            }
            userPresentReceiver = null
        }
    }
    
    private fun checkWifiAndRefreshWidget() {
        serviceScope.launch {
            try {
                Log.d(TAG, "Phone unlocked, refreshing widget on any WiFi connection")
                CameraWidgetProvider.refreshAllWidgets(applicationContext)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing widget", e)
            }
        }
    }
    
    private fun stopForegroundService() {
        Log.d(TAG, "Stopping foreground service")
        unregisterUserPresentReceiver()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}
