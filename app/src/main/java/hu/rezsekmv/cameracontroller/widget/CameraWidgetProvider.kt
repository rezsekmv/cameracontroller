package hu.rezsekmv.cameracontroller.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import hu.rezsekmv.cameracontroller.MainActivity
import hu.rezsekmv.cameracontroller.R

class CameraWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "CameraWidgetProvider"
        const val ACTION_TOGGLE_MOTION = "hu.rezsekmv.cameracontroller.TOGGLE_MOTION"
        const val ACTION_REFRESH_WIDGET = "hu.rezsekmv.cameracontroller.REFRESH_WIDGET"
        
        fun updateWidgetWithState(context: Context, backgroundRes: Int, isMotionEnabled: Boolean?) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, CameraWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                
                if (appWidgetIds.isNotEmpty()) {
                    val views = RemoteViews(context.packageName, R.layout.camera_widget)
                    
                    // Set background
                    views.setInt(R.id.widget_container, "setBackgroundResource", backgroundRes)
                    
                    // Set click intent based on state
                    val intent = Intent(context, CameraWidgetProvider::class.java)
                    val action = if (isMotionEnabled == null) {
                        // Gray state (unknown) - use refresh
                        ACTION_REFRESH_WIDGET
                    } else {
                        // Known state (red/green) - use toggle
                        ACTION_TOGGLE_MOTION
                    }
                    intent.action = action
                    
                    // Use first widget ID for the pending intent
                    val pendingIntent = PendingIntent.getBroadcast(
                        context, appWidgetIds[0], intent, 
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
                    
                    appWidgetManager.updateAppWidget(appWidgetIds, views)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update widget with state", e)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetWorkManager.startPeriodicUpdates(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetWorkManager.stopPeriodicUpdates(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        when (intent.action) {
            ACTION_TOGGLE_MOTION -> {
                WidgetWorkManager.triggerImmediateToggle(context)
            }
            ACTION_REFRESH_WIDGET -> {
                WidgetWorkManager.triggerImmediateRefresh(context)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                // No action needed on boot
            }
            else -> {
                // Unknown action
            }
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        
        val views = RemoteViews(context.packageName, R.layout.camera_widget)
        
        // Set up click intent - default to refresh for initial gray state
        val refreshIntent = Intent(context, CameraWidgetProvider::class.java)
        refreshIntent.action = ACTION_REFRESH_WIDGET
        refreshIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context, appWidgetId, refreshIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_container, refreshPendingIntent)
        
        // Initial state - show camera icon with gray background
        views.setInt(R.id.widget_container, "setBackgroundResource", R.drawable.widget_button_gray)
        
        appWidgetManager.updateAppWidget(appWidgetId, views)
        
        // Don't start service here - Android restricts background service starts
        // Service will be started when widget is actually used (tapped)
    }
}
