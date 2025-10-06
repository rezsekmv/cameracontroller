package hu.rezsekmv.cameracontroller.ui

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.rezsekmv.cameracontroller.data.ApiResult
import hu.rezsekmv.cameracontroller.data.CameraRepository
import hu.rezsekmv.cameracontroller.data.MotionDetectionStatus
import hu.rezsekmv.cameracontroller.data.PreferencesRepository
import hu.rezsekmv.cameracontroller.service.WidgetAutoRefreshService
import hu.rezsekmv.cameracontroller.widget.CameraWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CameraControlUiState(
    val username: String = "ipc",
    val password: String = "pass",
    val ipAddress: String = "192.168.1.100",
    val getConfigPath: String = "/cgi-bin/configManager.cgi?action=getConfig&name=MotionDetect",
    val setConfigPath: String = "/cgi-bin/configManager.cgi?action=setConfig&MotionDetect[].Enable=",
    val timeoutSeconds: String = "2",
    val wifiName: String = "",
    val motionDetectionStatus: MotionDetectionStatus = MotionDetectionStatus(null, false),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val apiEndpoint: String
        get() = "http://$username:$password@$ipAddress"
}

class CameraControlViewModel(context: Context) : ViewModel() {
    private val repository = CameraRepository()
    private val preferencesRepository = PreferencesRepository(context)
    private val appContext = context.applicationContext
    
    private val _motionDetectionStatus = MutableStateFlow(MotionDetectionStatus(null, false))
    private val _isLoading = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)
    
    val uiState: StateFlow<CameraControlUiState> = combine(
        preferencesRepository.cameraSettings,
        _motionDetectionStatus,
        _isLoading,
        _errorMessage
    ) { settings, motionStatus, loading, error ->
        CameraControlUiState(
            username = settings.username,
            password = settings.password,
            ipAddress = settings.ipAddress,
            getConfigPath = settings.getConfigPath,
            setConfigPath = settings.setConfigPath,
            timeoutSeconds = settings.timeoutSeconds,
            wifiName = settings.wifiName,
            motionDetectionStatus = motionStatus,
            isLoading = loading,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = CameraControlUiState()
    )
    
    companion object {
        private const val TAG = "CameraControlViewModel"
    }
    
    init {
        viewModelScope.launch {
            preferencesRepository.cameraSettings.collect { settings ->
                updateRepositoryFromSettings(settings)
            }
        }
    }
    
    private fun updateRepositoryFromSettings(settings: hu.rezsekmv.cameracontroller.data.CameraSettings) {
        val endpoint = "http://${settings.username}:${settings.password}@${settings.ipAddress}"
        repository.updateEndpoint(endpoint)
        val timeoutInt = settings.timeoutSeconds.toIntOrNull() ?: 2
        repository.updateConfiguration(
            getConfigPath = settings.getConfigPath,
            setConfigPath = settings.setConfigPath,
            timeoutSeconds = timeoutInt
        )
        
        // Initialize widget with the same endpoint
        CameraWidgetProvider.initializeRepository(endpoint)
        Log.d(TAG, "Widget initialized with endpoint: $endpoint")
    }

    fun updateUsername(username: String) {
        Log.d(TAG, "Updating username to: $username")
        viewModelScope.launch {
            preferencesRepository.updateUsername(username)
        }
    }
    
    fun updatePassword(password: String) {
        Log.d(TAG, "Updating password")
        viewModelScope.launch {
            preferencesRepository.updatePassword(password)
        }
    }
    
    fun updateIpAddress(ipAddress: String) {
        Log.d(TAG, "Updating IP address to: $ipAddress")
        viewModelScope.launch {
            preferencesRepository.updateIpAddress(ipAddress)
        }
    }
    
    fun updateGetConfigPath(path: String) {
        Log.d(TAG, "Updating get config path to: $path")
        viewModelScope.launch {
            preferencesRepository.updateGetConfigPath(path)
        }
    }
    
    fun updateSetConfigPath(path: String) {
        Log.d(TAG, "Updating set config path to: $path")
        viewModelScope.launch {
            preferencesRepository.updateSetConfigPath(path)
        }
    }
    
    fun updateTimeoutSeconds(timeout: String) {
        Log.d(TAG, "Updating timeout to: $timeout seconds")
        viewModelScope.launch {
            preferencesRepository.updateTimeoutSeconds(timeout)
        }
    }
    
    fun updateWifiName(wifiName: String) {
        Log.d(TAG, "Updating WiFi name to: $wifiName")
        viewModelScope.launch {
            preferencesRepository.updateWifiName(wifiName)
        }
    }

    fun refreshMotionDetectionStatus() {
        viewModelScope.launch {
            Log.d(TAG, "Starting refresh motion detection status")
            _isLoading.value = true
            _errorMessage.value = null // Clear any previous error
            
            val status = repository.getMotionDetectionStatus()
            Log.d(TAG, "Motion detection status received: isEnabled=${status.isEnabled}, isAvailable=${status.isAvailable}")
            
            _motionDetectionStatus.value = status
            _isLoading.value = false
            
            if (!status.isAvailable && status.errorMessage != null) {
                _errorMessage.value = status.errorMessage
                Log.e(TAG, "Camera not available, setting error message: ${status.errorMessage}")
            }
            
            // Refresh widget with new status
            CameraWidgetProvider.refreshAllWidgets(appContext)
            
            Log.d(TAG, "UI state updated with new motion detection status")
        }
    }

    fun toggleMotionDetection() {
        viewModelScope.launch {
            val currentStatus = _motionDetectionStatus.value
            Log.d(TAG, "Toggle requested - current status: isEnabled=${currentStatus.isEnabled}")
            
            if (currentStatus.isEnabled != null) {
                _isLoading.value = true
                _errorMessage.value = null // Clear any previous error
                
                val newEnabled = !currentStatus.isEnabled
                Log.d(TAG, "Toggling motion detection from ${currentStatus.isEnabled} to $newEnabled")
                
                val result = repository.setMotionDetectionStatus(newEnabled)
                Log.d(TAG, "Toggle operation completed - result: $result")
                
                when (result) {
                    is ApiResult.Success -> {
                        Log.d(TAG, "Toggle successful, refreshing status")
                        refreshMotionDetectionStatus()
                    }
                    is ApiResult.Error -> {
                        Log.e(TAG, "Toggle failed: ${result.message}")
                        _motionDetectionStatus.value = MotionDetectionStatus(null, false, result.message)
                        _errorMessage.value = result.message
                        _isLoading.value = false
                    }
                }
            } else {
                Log.w(TAG, "Cannot toggle - current status is null")
                _errorMessage.value = "Motion detection status is unknown. Try refreshing first."
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
    
    fun startAutoRefreshService() {
        Log.d(TAG, "Starting auto refresh service")
        val intent = Intent(appContext, WidgetAutoRefreshService::class.java).apply {
            action = WidgetAutoRefreshService.ACTION_START_SERVICE
        }
        appContext.startForegroundService(intent)
    }
    
    fun stopAutoRefreshService() {
        Log.d(TAG, "Stopping auto refresh service")
        val intent = Intent(appContext, WidgetAutoRefreshService::class.java).apply {
            action = WidgetAutoRefreshService.ACTION_STOP_SERVICE
        }
        appContext.startService(intent)
    }
}
