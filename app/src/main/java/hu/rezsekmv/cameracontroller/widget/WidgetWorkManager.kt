package hu.rezsekmv.cameracontroller.widget

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WidgetWorkManager {
    
    private const val TAG = "WidgetWorkManager"
    
    fun startPeriodicUpdates(context: Context) {
        try {
            Log.d(TAG, "Starting WiFi-aware periodic widget updates")
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val periodicWork = PeriodicWorkRequestBuilder<WiFiAwareWidgetWorker>(
                5, TimeUnit.MINUTES,
                2, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WiFiAwareWidgetWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                periodicWork
            )
            
            Log.d(TAG, "WiFi-aware periodic work enqueued successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start WiFi-aware periodic updates", e)
        }
    }
    
    
    fun stopPeriodicUpdates(context: Context) {
        try {
            Log.d(TAG, "Stopping WiFi-aware periodic widget updates")
            WorkManager.getInstance(context).cancelUniqueWork(WiFiAwareWidgetWorker.WORK_NAME)
            Log.d(TAG, "WiFi-aware periodic work cancelled successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop WiFi-aware periodic updates", e)
        }
    }
    
    fun triggerImmediateToggle(context: Context) {
        try {
            Log.d(TAG, "Triggering immediate toggle")
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val data = Data.Builder()
                .putString(ImmediateWidgetWorker.KEY_ACTION, ImmediateWidgetWorker.ACTION_TOGGLE)
                .build()
            
            val immediateWork = OneTimeWorkRequestBuilder<ImmediateWidgetWorker>()
                .setConstraints(constraints)
                .setInputData(data)
                .build()
            
            WorkManager.getInstance(context).enqueueUniqueWork(
                ImmediateWidgetWorker.WORK_NAME_TOGGLE,
                ExistingWorkPolicy.REPLACE,
                immediateWork
            )
            
            Log.d(TAG, "Immediate toggle work enqueued successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger immediate toggle", e)
        }
    }
    
    fun triggerImmediateRefresh(context: Context) {
        try {
            Log.d(TAG, "Triggering immediate refresh")
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val data = Data.Builder()
                .putString(ImmediateWidgetWorker.KEY_ACTION, ImmediateWidgetWorker.ACTION_REFRESH)
                .build()
            
            val immediateWork = OneTimeWorkRequestBuilder<ImmediateWidgetWorker>()
                .setConstraints(constraints)
                .setInputData(data)
                .build()
            
            WorkManager.getInstance(context).enqueueUniqueWork(
                ImmediateWidgetWorker.WORK_NAME_REFRESH,
                ExistingWorkPolicy.REPLACE,
                immediateWork
            )
            
            Log.d(TAG, "Immediate refresh work enqueued successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger immediate refresh", e)
        }
    }
}
