package hu.rezsekmv.cameracontroller.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "camera_settings")

data class CameraSettings(
    val username: String,
    val password: String,
    val ipAddress: String,
    val getConfigPath: String,
    val setConfigPath: String,
    val timeoutSeconds: String,
    val wifiName: String
)

class PreferencesRepository(private val context: Context) {
    
    companion object {
        private val USERNAME_KEY = stringPreferencesKey("username")
        private val PASSWORD_KEY = stringPreferencesKey("password")
        private val IP_ADDRESS_KEY = stringPreferencesKey("ip_address")
        private val GET_CONFIG_PATH_KEY = stringPreferencesKey("get_config_path")
        private val SET_CONFIG_PATH_KEY = stringPreferencesKey("set_config_path")
        private val TIMEOUT_SECONDS_KEY = stringPreferencesKey("timeout_seconds")
        private val WIFI_NAME_KEY = stringPreferencesKey("wifi_name")
    }
    
    val cameraSettings: Flow<CameraSettings> = context.dataStore.data.map { preferences ->
        CameraSettings(
            username = preferences[USERNAME_KEY] ?: "ipc",
            password = preferences[PASSWORD_KEY] ?: "pass",
            ipAddress = preferences[IP_ADDRESS_KEY] ?: "192.168.1.100",
            getConfigPath = preferences[GET_CONFIG_PATH_KEY] ?: "/cgi-bin/configManager.cgi?action=getConfig&name=MotionDetect",
            setConfigPath = preferences[SET_CONFIG_PATH_KEY] ?: "/cgi-bin/configManager.cgi?action=setConfig&MotionDetect[].Enable=",
            timeoutSeconds = preferences[TIMEOUT_SECONDS_KEY] ?: "2",
            wifiName = preferences[WIFI_NAME_KEY] ?: ""
        )
    }
    
    suspend fun updateUsername(username: String) {
        context.dataStore.edit { preferences ->
            preferences[USERNAME_KEY] = username
        }
    }
    
    suspend fun updatePassword(password: String) {
        context.dataStore.edit { preferences ->
            preferences[PASSWORD_KEY] = password
        }
    }
    
    suspend fun updateIpAddress(ipAddress: String) {
        context.dataStore.edit { preferences ->
            preferences[IP_ADDRESS_KEY] = ipAddress
        }
    }
    
    suspend fun updateGetConfigPath(path: String) {
        context.dataStore.edit { preferences ->
            preferences[GET_CONFIG_PATH_KEY] = path
        }
    }
    
    suspend fun updateSetConfigPath(path: String) {
        context.dataStore.edit { preferences ->
            preferences[SET_CONFIG_PATH_KEY] = path
        }
    }
    
    suspend fun updateTimeoutSeconds(timeout: String) {
        context.dataStore.edit { preferences ->
            preferences[TIMEOUT_SECONDS_KEY] = timeout
        }
    }
    
    suspend fun updateWifiName(wifiName: String) {
        context.dataStore.edit { preferences ->
            preferences[WIFI_NAME_KEY] = wifiName
        }
    }
}
