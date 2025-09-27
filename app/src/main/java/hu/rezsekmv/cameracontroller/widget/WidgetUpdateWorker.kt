package hu.rezsekmv.cameracontroller.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import hu.rezsekmv.cameracontroller.R
import hu.rezsekmv.cameracontroller.data.CameraRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WidgetUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "WidgetUpdateWorker"
        const val WORK_NAME = "widget_update_work"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "WidgetUpdateWorker: Starting periodic widget update")
            
            val repository = CameraRepository()
            val status = repository.getMotionDetectionStatus()
            
            Log.d(TAG, "WidgetUpdateWorker: Got status: $status")
            
            val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
            val componentName = ComponentName(applicationContext, CameraWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            if (appWidgetIds.isNotEmpty()) {
                Log.d(TAG, "WidgetUpdateWorker: Updating ${appWidgetIds.size} widgets")
                
                val views = android.widget.RemoteViews(applicationContext.packageName, R.layout.camera_widget)
                
                val backgroundRes = when {
                    status?.isEnabled == true -> R.drawable.widget_button_red
                    status?.isEnabled == false -> R.drawable.widget_button_green
                    else -> R.drawable.widget_button_gray
                }
                
                views.setInt(R.id.widget_container, "setBackgroundResource", backgroundRes)
                appWidgetManager.updateAppWidget(appWidgetIds, views)
                
                Log.d(TAG, "WidgetUpdateWorker: Widget updated successfully with status: ${status?.isEnabled}")
            } else {
                Log.d(TAG, "WidgetUpdateWorker: No widgets found to update")
            }
            
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "WidgetUpdateWorker: Failed to update widget", e)
            Result.retry()
        }
    }
}
