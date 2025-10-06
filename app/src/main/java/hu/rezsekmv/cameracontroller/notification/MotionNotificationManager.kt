package hu.rezsekmv.cameracontroller.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import hu.rezsekmv.cameracontroller.MainActivity
import hu.rezsekmv.cameracontroller.R

class MotionNotificationManager(private val context: Context) {
    
    companion object {
        private const val CHANNEL_ID = "motion_detection_channel"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_NAME = "Motion Detection"
        private const val CHANNEL_DESCRIPTION = "Notifications for motion detection events"
    }
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    init {
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    fun showMotionDetectedNotification() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_camera_widget)
            .setContentTitle("Motion Detected!")
            .setContentText("Camera motion sensor is currently ON")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Motion detection is active on your camera. The sensor detected movement and is currently monitoring."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 1000, 500, 1000))
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    fun cancelMotionNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
