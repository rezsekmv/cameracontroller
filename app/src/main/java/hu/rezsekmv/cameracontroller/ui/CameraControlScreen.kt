package hu.rezsekmv.cameracontroller.ui

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import android.widget.Toast

@Composable
fun CameraControlScreen(
    modifier: Modifier = Modifier,
    viewModel: CameraControlViewModel = viewModel(factory = CameraControlViewModelFactory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    
    // Local state for input fields
    var localUsername by remember { mutableStateOf(uiState.username) }
    var localPassword by remember { mutableStateOf(uiState.password) }
    var localIpAddress by remember { mutableStateOf(uiState.ipAddress) }
    var localGetConfigPath by remember { mutableStateOf(uiState.getConfigPath) }
    var localSetConfigPath by remember { mutableStateOf(uiState.setConfigPath) }
    var localTimeoutSeconds by remember { mutableStateOf(uiState.timeoutSeconds) }
    var localGatewayIp by remember { mutableStateOf(uiState.gatewayIp) }
    
    // Update local state when uiState changes (from DataStore)
    LaunchedEffect(uiState.username) { localUsername = uiState.username }
    LaunchedEffect(uiState.password) { localPassword = uiState.password }
    LaunchedEffect(uiState.ipAddress) { localIpAddress = uiState.ipAddress }
    LaunchedEffect(uiState.getConfigPath) { localGetConfigPath = uiState.getConfigPath }
    LaunchedEffect(uiState.setConfigPath) { localSetConfigPath = uiState.setConfigPath }
    LaunchedEffect(uiState.timeoutSeconds) { localTimeoutSeconds = uiState.timeoutSeconds }
    LaunchedEffect(uiState.gatewayIp) { localGatewayIp = uiState.gatewayIp }

    // Removed automatic API call on startup - user can manually refresh if needed
    
    // Show toast for errors
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { errorMessage ->
            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    val scrollState = rememberScrollState()
    
    // Helper function to save on focus out
    fun saveFieldOnFocusOut(
        currentValue: String,
        savedValue: String,
        saveAction: (String) -> Unit
    ) {
        if (currentValue != savedValue) {
            saveAction(currentValue)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { focusManager.clearFocus() }
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Camera Controller",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        val usernameInteractionSource = remember { MutableInteractionSource() }
        val isUsernameFocused by usernameInteractionSource.collectIsFocusedAsState()
        
        LaunchedEffect(isUsernameFocused) {
            if (!isUsernameFocused) {
                saveFieldOnFocusOut(localUsername, uiState.username, viewModel::updateUsername)
            }
        }
        
        OutlinedTextField(
            value = localUsername,
            onValueChange = { localUsername = it },
            label = { Text("Username") },
            placeholder = { Text("ipc") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            interactionSource = usernameInteractionSource
        )

        Spacer(modifier = Modifier.height(16.dp))

        val passwordInteractionSource = remember { MutableInteractionSource() }
        val isPasswordFocused by passwordInteractionSource.collectIsFocusedAsState()
        
        LaunchedEffect(isPasswordFocused) {
            if (!isPasswordFocused) {
                saveFieldOnFocusOut(localPassword, uiState.password, viewModel::updatePassword)
            }
        }
        
        OutlinedTextField(
            value = localPassword,
            onValueChange = { localPassword = it },
            label = { Text("Password") },
            placeholder = { Text("password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            interactionSource = passwordInteractionSource,
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        val ipAddressInteractionSource = remember { MutableInteractionSource() }
        val isIpAddressFocused by ipAddressInteractionSource.collectIsFocusedAsState()
        
        LaunchedEffect(isIpAddressFocused) {
            if (!isIpAddressFocused) {
                saveFieldOnFocusOut(localIpAddress, uiState.ipAddress, viewModel::updateIpAddress)
            }
        }
        
        OutlinedTextField(
            value = localIpAddress,
            onValueChange = { localIpAddress = it },
            label = { Text("IP Address") },
            placeholder = { Text("192.168.1.100:8081") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            interactionSource = ipAddressInteractionSource
        )

        Spacer(modifier = Modifier.height(16.dp))

        val getConfigPathInteractionSource = remember { MutableInteractionSource() }
        val isGetConfigPathFocused by getConfigPathInteractionSource.collectIsFocusedAsState()
        
        LaunchedEffect(isGetConfigPathFocused) {
            if (!isGetConfigPathFocused) {
                saveFieldOnFocusOut(localGetConfigPath, uiState.getConfigPath, viewModel::updateGetConfigPath)
            }
        }
        
        OutlinedTextField(
            value = localGetConfigPath,
            onValueChange = { localGetConfigPath = it },
            label = { Text("Get Config Path") },
            placeholder = { Text("/cgi-bin/configManager.cgi?action=getConfig&name=MotionDetect") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            interactionSource = getConfigPathInteractionSource
        )

        Spacer(modifier = Modifier.height(16.dp))

        val setConfigPathInteractionSource = remember { MutableInteractionSource() }
        val isSetConfigPathFocused by setConfigPathInteractionSource.collectIsFocusedAsState()
        
        LaunchedEffect(isSetConfigPathFocused) {
            if (!isSetConfigPathFocused) {
                saveFieldOnFocusOut(localSetConfigPath, uiState.setConfigPath, viewModel::updateSetConfigPath)
            }
        }
        
        OutlinedTextField(
            value = localSetConfigPath,
            onValueChange = { localSetConfigPath = it },
            label = { Text("Set Config Path") },
            placeholder = { Text("/cgi-bin/configManager.cgi?action=setConfig&MotionDetect[].Enable=") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            interactionSource = setConfigPathInteractionSource
        )

        Spacer(modifier = Modifier.height(16.dp))

        val timeoutInteractionSource = remember { MutableInteractionSource() }
        val isTimeoutFocused by timeoutInteractionSource.collectIsFocusedAsState()
        
        LaunchedEffect(isTimeoutFocused) {
            if (!isTimeoutFocused) {
                saveFieldOnFocusOut(localTimeoutSeconds, uiState.timeoutSeconds, viewModel::updateTimeoutSeconds)
            }
        }
        
        OutlinedTextField(
            value = localTimeoutSeconds,
            onValueChange = { localTimeoutSeconds = it },
            label = { Text("Timeout (seconds)") },
            placeholder = { Text("2") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            interactionSource = timeoutInteractionSource
        )

        Spacer(modifier = Modifier.height(16.dp))

        val gatewayIpInteractionSource = remember { MutableInteractionSource() }
        val isGatewayIpFocused by gatewayIpInteractionSource.collectIsFocusedAsState()
        
        LaunchedEffect(isGatewayIpFocused) {
            if (!isGatewayIpFocused) {
                saveFieldOnFocusOut(localGatewayIp, uiState.gatewayIp, viewModel::updateGatewayIp)
            }
        }
        
        OutlinedTextField(
            value = localGatewayIp,
            onValueChange = { localGatewayIp = it },
            label = { Text("Gateway IP") },
            placeholder = { Text("192.168.1.1") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            interactionSource = gatewayIpInteractionSource
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Endpoint: ${uiState.apiEndpoint}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { 
                focusManager.clearFocus()
                viewModel.refreshMotionDetectionStatus() 
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Text("Refresh Status")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { 
                focusManager.clearFocus()
                viewModel.startAutoRefreshService() 
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Start Auto Refresh on Unlock")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { 
                focusManager.clearFocus()
                viewModel.stopAutoRefreshService() 
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("Stop Auto Refresh")
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            val buttonColor = when {
                !uiState.motionDetectionStatus.isAvailable -> {
                    Log.d("CameraControlScreen", "Button color: Gray (not available)")
                    Color.Gray
                }
                uiState.motionDetectionStatus.isEnabled == true -> {
                    Log.d("CameraControlScreen", "Button color: Red (motion ON)")
                    Color.Red
                }
                uiState.motionDetectionStatus.isEnabled == false -> {
                    Log.d("CameraControlScreen", "Button color: Green (motion OFF)")
                    Color(0xFF059605)
                }
                else -> {
                    Log.d("CameraControlScreen", "Button color: Gray (unknown status)")
                    Color.Gray
                }
            }
            
            Log.d("CameraControlScreen", "Current UI state - isAvailable: ${uiState.motionDetectionStatus.isAvailable}, isEnabled: ${uiState.motionDetectionStatus.isEnabled}, isLoading: ${uiState.isLoading}")

            val buttonText = when {
                !uiState.motionDetectionStatus.isAvailable -> "Connection Failed"
                uiState.motionDetectionStatus.isEnabled == true -> "Motion Detection: ON"
                uiState.motionDetectionStatus.isEnabled == false -> "Motion Detection: OFF"
                else -> "Unknown Status"
            }

            Button(
                onClick = { 
                    if (uiState.motionDetectionStatus.isAvailable && 
                        uiState.motionDetectionStatus.isEnabled != null) {
                        focusManager.clearFocus()
                        Log.d("CameraControlScreen", "Button clicked - current status: ${uiState.motionDetectionStatus.isEnabled}")
                        viewModel.toggleMotionDetection()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor
                ),
                shape = RoundedCornerShape(12.dp),
                enabled = uiState.motionDetectionStatus.isAvailable && 
                         uiState.motionDetectionStatus.isEnabled != null
            ) {
                Text(
                    text = buttonText,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = when {
                !uiState.motionDetectionStatus.isAvailable -> "Camera connection failed - check network settings"
                uiState.motionDetectionStatus.isEnabled == true -> "Click to turn OFF motion detection"
                uiState.motionDetectionStatus.isEnabled == false -> "Click to turn ON motion detection"
                else -> "Status unknown"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}
