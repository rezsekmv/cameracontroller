package hu.rezsekmv.cameracontroller.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import hu.rezsekmv.cameracontroller.R
import hu.rezsekmv.cameracontroller.data.CameraRepository
import hu.rezsekmv.cameracontroller.data.MotionDetectionStatus
import hu.rezsekmv.cameracontroller.data.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class WiFiAwareWidgetWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "WiFiAwareWidgetWorker"
        const val WORK_NAME = "wifi_aware_widget_work"
        
        // IP prefix for fallback detection
        private const val TARGET_WIFI_PREFIX = "192.168.1." // Or use IP prefix for more flexibility
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "WiFiAwareWidgetWorker: Starting WiFi-aware widget update")
            
            val repository = CameraRepository()
            val preferencesRepository = PreferencesRepository(applicationContext)
            
            // Load settings
            val settings = preferencesRepository.cameraSettings.first()
            
            // Check if we're on the target WiFi network
            if (!isOnTargetWiFi(settings.wifiName)) {
                Log.d(TAG, "Not on target WiFi network - skipping widget update")
                return@withContext Result.success()
            }
            
            Log.d(TAG, "On target WiFi network - proceeding with widget update")
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
            Log.d(TAG, "WiFi-aware refresh status: $status")
            
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
            
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "WiFiAwareWidgetWorker: Failed to update widget", e)
            Result.retry()
        }
    }
    
    private fun isOnTargetWiFi(targetWifiName: String): Boolean {
        return try {
            val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            // Check if we're connected to WiFi
            if (!networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                Log.d(TAG, "Not connected to WiFi")
                return false
            }
            
            // Get WiFi info
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            
            // Check SSID (remove quotes if present)
            val currentSSID = wifiInfo.ssid?.removeSurrounding("\"") ?: ""
            Log.d(TAG, "Current WiFi SSID: '$currentSSID'")
            
            // Method 1: Check SSID starts with target (if WiFi name is configured)
            if (targetWifiName.isNotEmpty() && currentSSID.startsWith(targetWifiName, ignoreCase = true)) {
                Log.d(TAG, "SSID starts with target: $targetWifiName")
                return true
            }
            
            // Method 2: Check IP range (more flexible)
            val ipAddress = getCurrentIPAddress()
            if (ipAddress != null && ipAddress.startsWith(TARGET_WIFI_PREFIX)) {
                Log.d(TAG, "IP address matches target range: $ipAddress")
                return true
            }
            
            Log.d(TAG, "WiFi network does not match target criteria")
            false
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking WiFi status", e)
            false
        }
    }
    
    private fun getCurrentIPAddress(): String? {
        return try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val ipInt = wifiInfo.ipAddress
            
            if (ipInt == 0) return null
            
            // Convert int IP to string
            String.format(
                "%d.%d.%d.%d",
                ipInt and 0xff,
                ipInt shr 8 and 0xff,
                ipInt shr 16 and 0xff,
                ipInt shr 24 and 0xff
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP address", e)
            null
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
