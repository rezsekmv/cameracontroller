package hu.rezsekmv.cameracontroller.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
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
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.d(TAG, "onUpdate called with ${appWidgetIds.size} widgets")
        
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "Widget enabled - registering receivers")
        
        // Start the widget service when first widget is added
        val intent = Intent(context, CameraWidgetService::class.java)
        intent.action = CameraWidgetService.ACTION_REGISTER_RECEIVERS
        context.startForegroundService(intent)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d(TAG, "Widget disabled - unregistering receivers")
        
        // Stop the widget service when last widget is removed
        val intent = Intent(context, CameraWidgetService::class.java)
        intent.action = CameraWidgetService.ACTION_UNREGISTER_RECEIVERS
        context.startService(intent)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        when (intent.action) {
            ACTION_TOGGLE_MOTION -> {
                Log.d(TAG, "Toggle motion action received")
                val serviceIntent = Intent(context, CameraWidgetService::class.java)
                serviceIntent.action = CameraWidgetService.ACTION_TOGGLE_MOTION
                context.startForegroundService(serviceIntent)
            }
            ACTION_REFRESH_WIDGET -> {
                Log.d(TAG, "Refresh widget action received")
                val serviceIntent = Intent(context, CameraWidgetService::class.java)
                serviceIntent.action = CameraWidgetService.ACTION_REFRESH_STATUS
                context.startForegroundService(serviceIntent)
            }
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        Log.d(TAG, "Updating widget $appWidgetId")
        
        val views = RemoteViews(context.packageName, R.layout.camera_widget)
        
        // Set up click intent for toggle button
        val toggleIntent = Intent(context, CameraWidgetProvider::class.java)
        toggleIntent.action = ACTION_TOGGLE_MOTION
        val togglePendingIntent = PendingIntent.getBroadcast(
            context, 0, toggleIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_toggle_button, togglePendingIntent)
        
        // Set up click intent for opening main app (long press)
        val appIntent = Intent(context, MainActivity::class.java)
        val appPendingIntent = PendingIntent.getActivity(
            context, 0, appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Initial state - always show "C"
        views.setTextViewText(R.id.widget_toggle_button, "C")
        views.setInt(R.id.widget_toggle_button, "setBackgroundResource", R.drawable.widget_button_gray)
        
        appWidgetManager.updateAppWidget(appWidgetId, views)
        
        // Trigger initial refresh
        val refreshIntent = Intent(context, CameraWidgetService::class.java)
        refreshIntent.action = CameraWidgetService.ACTION_REFRESH_STATUS
        context.startForegroundService(refreshIntent)
    }
}
