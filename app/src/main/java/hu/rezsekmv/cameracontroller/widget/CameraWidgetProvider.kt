package hu.rezsekmv.cameracontroller.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import hu.rezsekmv.cameracontroller.R
import hu.rezsekmv.cameracontroller.data.CameraRepository
import hu.rezsekmv.cameracontroller.data.MotionDetectionStatus
import hu.rezsekmv.cameracontroller.notification.MotionNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CameraWidgetProvider : AppWidgetProvider() {
    
    companion object {
        private const val TAG = "CameraWidgetProvider"
        const val ACTION_WIDGET_CLICK = "hu.rezsekmv.cameracontroller.WIDGET_CLICK"
        const val ACTION_REFRESH_WIDGET = "hu.rezsekmv.cameracontroller.REFRESH_WIDGET"
        
        private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        private var cameraRepository: CameraRepository? = null
        private var cameraEndpoint: String? = null
        private var lastMotionState: Boolean? = null
        
        fun initializeRepository(endpoint: String) {
            cameraEndpoint = endpoint
            cameraRepository = CameraRepository().apply {
                updateEndpoint(endpoint)
            }
        }
        
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, status: MotionDetectionStatus) {
            val views = RemoteViews(context.packageName, R.layout.camera_widget)
            
            // Set background based on motion detection status
            val backgroundRes = when {
                status.isEnabled == null -> R.drawable.widget_button_gray
                status.isEnabled == true -> R.drawable.widget_button_red
                else -> R.drawable.widget_button_green
            }
            views.setInt(R.id.widget_container, "setBackgroundResource", backgroundRes)
            
            // Set click intent
            val clickIntent = Intent(context, CameraWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_CLICK
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val clickPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, clickPendingIntent)
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
        
        fun refreshAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, CameraWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            appWidgetIds.forEach { appWidgetId ->
                refreshWidget(context, appWidgetManager, appWidgetId)
            }
        }
        
        private fun refreshWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val repository = cameraRepository
            val endpoint = cameraEndpoint
            
            if (repository == null || endpoint == null) {
                Log.w(TAG, "Repository or endpoint not initialized, showing gray state")
                updateWidget(context, appWidgetManager, appWidgetId, MotionDetectionStatus(null, false, "Not initialized"))
                return
            }
            
            scope.launch {
                try {
                    val status = repository.getMotionDetectionStatus()
                    updateWidget(context, appWidgetManager, appWidgetId, status)
                    Log.d(TAG, "Widget refreshed with status: ${status.isEnabled}")
                    
                    // Check for motion state changes and send notification
                    checkMotionStateChange(context, status.isEnabled)
                } catch (e: Exception) {
                    Log.e(TAG, "Error refreshing widget", e)
                    updateWidget(context, appWidgetManager, appWidgetId, MotionDetectionStatus(null, false, e.message))
                }
            }
        }
        
        private fun checkMotionStateChange(context: Context, currentMotionState: Boolean?) {
            // Only send notification if motion is detected as ON and it's a state change
            if (currentMotionState == true && lastMotionState != true) {
                Log.d(TAG, "Motion detected! Sending notification")
                val notificationManager = MotionNotificationManager(context)
                notificationManager.showMotionDetectedNotification()
            }
            
            // Update the last known state
            lastMotionState = currentMotionState
        }
    }
    
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.d(TAG, "onUpdate called for ${appWidgetIds.size} widgets")
        appWidgetIds.forEach { appWidgetId ->
            refreshWidget(context, appWidgetManager, appWidgetId)
        }
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        when (intent.action) {
            ACTION_WIDGET_CLICK -> {
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    handleWidgetClick(context, appWidgetId)
                }
            }
            ACTION_REFRESH_WIDGET -> {
                refreshAllWidgets(context)
            }
        }
    }
    
    private fun handleWidgetClick(context: Context, appWidgetId: Int) {
        Log.d(TAG, "handleWidgetClick")
        val repository = cameraRepository
        val endpoint = cameraEndpoint
        
        if (repository == null || endpoint == null) {
            Log.w(TAG, "Repository or endpoint not initialized, cannot handle click")
            return
        }
        
        val appWidgetManager = AppWidgetManager.getInstance(context)
        
        scope.launch {
            try {
                val currentState = lastMotionState
                if (currentState == null) {
                    // Unknown state - just refresh
                    Log.d(TAG, "Unknown state, refreshing widget")
                    refreshWidget(context, appWidgetManager, appWidgetId)
                } else {
                    // Known state - toggle and then refresh
                    Log.d(TAG, "Toggling motion detection from ${currentState}")
                    val toggleResult = repository.setMotionDetectionStatus(!currentState)
                    
                    if (toggleResult is hu.rezsekmv.cameracontroller.data.ApiResult.Success) {
                        // Toggle successful, refresh to get updated status
                        refreshWidget(context, appWidgetManager, appWidgetId)
                    } else {
                        // Toggle failed, refresh to show current state
                        Log.e(TAG, "Failed to toggle motion detection: ${(toggleResult as hu.rezsekmv.cameracontroller.data.ApiResult.Error).message}")
                        refreshWidget(context, appWidgetManager, appWidgetId)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling widget click", e)
                refreshWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }
    
    override fun onEnabled(context: Context) {
        Log.d(TAG, "Widget enabled")
    }
    
    override fun onDisabled(context: Context) {
        Log.d(TAG, "Widget disabled")
    }
}
