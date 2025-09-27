package hu.rezsekmv.cameracontroller.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log

class ConnectivityChangeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ConnectivityChangeReceiver"
        
        // IP prefix for fallback detection
        private const val TARGET_WIFI_PREFIX = "192.168.1." // Or use IP prefix for more flexibility
    }

    override fun onReceive(context: Context, intent: Intent) {
        
        when (intent.action) {
            ConnectivityManager.CONNECTIVITY_ACTION -> {
                handleConnectivityChange(context)
            }
            WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                handleConnectivityChange(context)
            }
            WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                handleConnectivityChange(context)
            }
        }
    }
    
    private fun handleConnectivityChange(context: Context) {
        try {
            // Always trigger widget update on connectivity change
            // The WiFiAwareWidgetWorker will handle the WiFi check internally
            WidgetWorkManager.triggerImmediateRefresh(context)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling connectivity change", e)
        }
    }
    
}
