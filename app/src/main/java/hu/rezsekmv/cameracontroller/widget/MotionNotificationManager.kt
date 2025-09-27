package hu.rezsekmv.cameracontroller.widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import hu.rezsekmv.cameracontroller.MainActivity
import hu.rezsekmv.cameracontroller.R

object MotionNotificationManager {
    
    private const val TAG = "MotionNotificationManager"
    private const val CHANNEL_ID = "motion_detection_alerts"
    private const val NOTIFICATION_ID = 2001
    
    fun sendMotionOnNotification(context: Context) {
        try {
            Log.d(TAG, "Attempting to send motion ON notification")
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Check if notifications are enabled
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val areNotificationsEnabled = notificationManager.areNotificationsEnabled()
                Log.d(TAG, "Notifications enabled: $areNotificationsEnabled")
                if (!areNotificationsEnabled) {
                    Log.w(TAG, "Notifications are disabled for this app")
                    return
                }
            }
            
            // Create notification channel for Android 8.0+
            createNotificationChannel(context, notificationManager)
            
            // Create intent to open the app
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Create notification
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_camera_widget)
                .setContentTitle("Motion Detection Active")
                .setContentText("Camera motion detection is currently ON")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            
            // Show notification
            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "Motion ON notification sent successfully with ID: $NOTIFICATION_ID")
            
            // Additional debugging
            Log.d(TAG, "Notification channel ID: $CHANNEL_ID")
            Log.d(TAG, "Notification title: Motion Detection Active")
            Log.d(TAG, "Notification text: Camera motion detection is currently ON")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending motion notification", e)
        }
    }
    
    private fun createNotificationChannel(context: Context, notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Motion Detection Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when motion detection is active"
                enableLights(true)
                enableVibration(true)
            }
            
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created: $CHANNEL_ID")
        }
    }
    
    fun cancelNotification(context: Context) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
            Log.d(TAG, "Motion notification cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling notification", e)
        }
    }
    
    fun sendTestNotification(context: Context) {
        try {
            Log.d(TAG, "Sending test notification")
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Check if notifications are enabled
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val areNotificationsEnabled = notificationManager.areNotificationsEnabled()
                Log.d(TAG, "Test notification - Notifications enabled: $areNotificationsEnabled")
                if (!areNotificationsEnabled) {
                    Log.w(TAG, "Test notification - Notifications are disabled for this app")
                    return
                }
            }
            
            // Create notification channel for Android 8.0+
            createNotificationChannel(context, notificationManager)
            
            // Create intent to open the app
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            
            val pendingIntent = PendingIntent.getActivity(
                context,
                999,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Create test notification
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_camera_widget)
                .setContentTitle("Test Notification")
                .setContentText("This is a test notification to verify the system works")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            
            // Show test notification
            notificationManager.notify(9999, notification)
            Log.d(TAG, "Test notification sent successfully with ID: 9999")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending test notification", e)
        }
    }
}
