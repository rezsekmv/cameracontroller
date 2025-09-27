package hu.rezsekmv.cameracontroller.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import hu.rezsekmv.cameracontroller.R
import hu.rezsekmv.cameracontroller.data.ApiResult
import hu.rezsekmv.cameracontroller.data.CameraRepository
import hu.rezsekmv.cameracontroller.data.MotionDetectionStatus
import hu.rezsekmv.cameracontroller.data.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class ImmediateWidgetWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ImmediateWidgetWorker"
        const val WORK_NAME_TOGGLE = "immediate_widget_toggle"
        const val WORK_NAME_REFRESH = "immediate_widget_refresh"
        
        const val KEY_ACTION = "action"
        const val ACTION_TOGGLE = "toggle"
        const val ACTION_REFRESH = "refresh"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val action = inputData.getString(KEY_ACTION) ?: ACTION_REFRESH
            
            val repository = CameraRepository()
            val preferencesRepository = PreferencesRepository(applicationContext)
            
            when (action) {
                ACTION_TOGGLE -> {
                    performToggle(repository, preferencesRepository)
                }
                ACTION_REFRESH -> {
                    performRefresh(repository, preferencesRepository)
                }
            }
            
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "ImmediateWidgetWorker: Failed to perform action", e)
            Result.retry()
        }
    }
    
    private suspend fun performToggle(repository: CameraRepository, preferencesRepository: PreferencesRepository) {
        try {
            // Load settings
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
            val currentStatus = repository.getMotionDetectionStatus()
            Log.d(TAG, "Current status: $currentStatus")
            
            if (currentStatus.isEnabled == null) {
                Log.w(TAG, "Cannot toggle - current status unknown")
                updateWidget(R.drawable.widget_button_gray, null)
                return
            }
            
            // Toggle the status
            val newState = !currentStatus.isEnabled
            Log.d(TAG, "Toggling to: $newState")
            
            val result = repository.setMotionDetectionStatus(newState)
            
            when (result) {
                is ApiResult.Success -> {
                    Log.d(TAG, "Toggle successful")
                    val backgroundRes = if (newState) R.drawable.widget_button_red else R.drawable.widget_button_green
                    updateWidget(backgroundRes, newState)
                    
                    // Send notification if motion detection is turned ON
                    if (newState == true) {
                        Log.d(TAG, "Motion detection turned ON - sending notification")
                        MotionNotificationManager.sendMotionOnNotification(applicationContext)
                    }
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "Toggle failed: ${result.message}")
                    updateWidget(R.drawable.widget_button_gray, null)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during toggle", e)
            updateWidget(R.drawable.widget_button_gray, null)
        }
    }
    
    private suspend fun performRefresh(repository: CameraRepository, preferencesRepository: PreferencesRepository) {
        try {
            // Load settings
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
            Log.d(TAG, "Refresh status: $status")
            
            val backgroundRes = when {
                status.isEnabled == true -> R.drawable.widget_button_red
                status.isEnabled == false -> R.drawable.widget_button_green
                else -> R.drawable.widget_button_gray
            }
            
            updateWidget(backgroundRes, status.isEnabled)
            
            // Send notification if motion detection is ON
            if (status.isEnabled == true) {
                Log.d(TAG, "Motion detection is ON - sending notification")
                MotionNotificationManager.sendMotionOnNotification(applicationContext)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during refresh", e)
            updateWidget(R.drawable.widget_button_gray, null)
        }
    }
    
    private fun updateWidget(backgroundRes: Int, isMotionEnabled: Boolean? = null) {
        try {
            CameraWidgetProvider.updateWidgetWithState(applicationContext, backgroundRes, isMotionEnabled)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update widget", e)
        }
    }
}
