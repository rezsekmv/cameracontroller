package hu.rezsekmv.cameracontroller.data

import android.util.Log
import hu.rezsekmv.cameracontroller.util.DigestAuthUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

data class MotionDetectionStatus(
    val isEnabled: Boolean?,
    val isAvailable: Boolean,
    val errorMessage: String? = null
)

sealed class ApiResult<T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error<T>(val message: String, val data: T? = null) : ApiResult<T>()
}

class CameraRepository {
    private var apiService: CameraApiService? = null
    private var currentEndpoint: String = ""
    private var getConfigPath: String =
        "/cgi-bin/configManager.cgi?action=getConfig&name=MotionDetect"
    private var setConfigPath: String =
        "/cgi-bin/configManager.cgi?action=setConfig&MotionDetect[].Enable="
    private var timeoutSeconds: Int = 2

    companion object {
        private const val TAG = "CameraRepository"
    }

    fun updateEndpoint(endpoint: String) {
        if (endpoint != currentEndpoint) {
            currentEndpoint = endpoint
            apiService = createApiService(endpoint)
        }
    }

    fun updateConfiguration(getConfigPath: String, setConfigPath: String, timeoutSeconds: Int) {
        this.getConfigPath = getConfigPath
        this.setConfigPath = setConfigPath
        if (this.timeoutSeconds != timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds
            if (currentEndpoint.isNotEmpty()) {
                apiService = createApiService(currentEndpoint)
            }
        }
    }

    private fun createApiService(endpoint: String): CameraApiService? {
        return try {
            val urlParts = endpoint.replace("http://", "").split("@")
            if (urlParts.size != 2) {
                Log.e(TAG, "Invalid endpoint format - missing credentials: $endpoint")
                return null
            }

            val credentials = urlParts[0].split(":")
            if (credentials.size != 2) {
                Log.e(TAG, "Invalid credentials format: ${urlParts[0]}")
                return null
            }

            val baseUrl = "http://${urlParts[1]}"
            val username = credentials[0]
            val password = credentials[1]

            val client = OkHttpClient.Builder()
                .authenticator { _, response ->
                    if (response.request.header("Authorization") != null) {
                        return@authenticator null
                    }

                    val authHeader = response.header("WWW-Authenticate")

                    if (DigestAuthUtil.isDigestChallenge(authHeader)) {
                        val digestAuth = DigestAuthUtil.createDigestAuth(
                            authHeader!!,
                            username,
                            password,
                            response.request
                        )
                        response.request.newBuilder()
                            .header("Authorization", digestAuth)
                            .build()
                    } else {
                        Log.w(TAG, "No digest challenge found, using basic auth fallback")
                        response.request.newBuilder()
                            .header("Authorization", Credentials.basic(username, password))
                            .build()
                    }
                }
                .connectTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(ScalarsConverterFactory.create())
                .build()

            val service = retrofit.create(CameraApiService::class.java)
            service
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create API service", e)
            null
        }
    }

    suspend fun getMotionDetectionStatus(): MotionDetectionStatus {
        return withContext(Dispatchers.IO) {
            try {
                val service = apiService ?: run {
                    Log.e(TAG, "API service is null - cannot get motion detection status")
                    return@withContext MotionDetectionStatus(
                        null,
                        false,
                        "Camera service not initialized"
                    )
                }

                val response = service.getRequest(getConfigPath)

                if (response.isSuccessful) {
                    val body = response.body()
                    val isEnabled = body?.let { parseMotionDetectionStatus(it) }
                    MotionDetectionStatus(isEnabled, true)
                } else {
                    val errorMessage = when (response.code()) {
                        401 -> "Authentication failed - check username and password"
                        403 -> "Access forbidden - check camera permissions"
                        404 -> "Camera endpoint not found - check IP address and API path"
                        500 -> "Camera server error - check camera status"
                        503 -> "Camera service unavailable - camera may be busy"
                        else -> "Camera responded with error ${response.code()}: ${response.message()}"
                    }
                    Log.e(
                        TAG,
                        "API call failed with code: ${response.code()}, message: ${response.message()}"
                    )
                    MotionDetectionStatus(null, false, errorMessage)
                }
            } catch (e: Exception) {
                val errorMessage = when (e) {
                    is SocketTimeoutException, is TimeoutException -> {
                        Log.w(TAG, "Timeout during getMotionDetectionStatus - camera unavailable")
                        "Connection timeout - camera not responding (check IP address and network)"
                    }

                    is java.net.UnknownHostException -> {
                        Log.e(TAG, "Unknown host during getMotionDetectionStatus", e)
                        "Camera not found - check IP address and network connection"
                    }

                    is java.net.ConnectException -> {
                        Log.e(TAG, "Connection refused during getMotionDetectionStatus", e)
                        "Connection refused - camera may be offline or wrong port"
                    }

                    is javax.net.ssl.SSLException -> {
                        Log.e(TAG, "SSL error during getMotionDetectionStatus", e)
                        "SSL/TLS error - check camera security settings"
                    }

                    else -> {
                        Log.e(TAG, "Exception during getMotionDetectionStatus", e)
                        "Network error: ${e.message ?: "Unknown error"}"
                    }
                }
                MotionDetectionStatus(null, false, errorMessage)
            }
        }
    }

    suspend fun setMotionDetectionStatus(enable: Boolean): ApiResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val service = apiService ?: run {
                    Log.e(TAG, "API service is null - cannot set motion detection status")
                    return@withContext ApiResult.Error("Camera service not initialized")
                }

                val fullPath = "$setConfigPath$enable"
                val response = service.getRequest(fullPath)

                if (response.isSuccessful) {
                    ApiResult.Success(true)
                } else {
                    val errorMessage = when (response.code()) {
                        401 -> "Authentication failed - check username and password"
                        403 -> "Access forbidden - check camera permissions"
                        404 -> "Camera endpoint not found - check IP address and API path"
                        500 -> "Camera server error - check camera status"
                        503 -> "Camera service unavailable - camera may be busy"
                        else -> "Camera responded with error ${response.code()}: ${response.message()}"
                    }
                    Log.e(
                        TAG,
                        "Failed to set motion detection - code: ${response.code()}, message: ${response.message()}"
                    )
                    ApiResult.Error(errorMessage)
                }
            } catch (e: Exception) {
                val errorMessage = when (e) {
                    is SocketTimeoutException, is TimeoutException -> {
                        Log.w(TAG, "Timeout during setMotionDetectionStatus - camera unavailable")
                        "Connection timeout - camera not responding (check IP address and network)"
                    }

                    is java.net.UnknownHostException -> {
                        Log.e(TAG, "Unknown host during setMotionDetectionStatus", e)
                        "Camera not found - check IP address and network connection"
                    }

                    is java.net.ConnectException -> {
                        Log.e(TAG, "Connection refused during setMotionDetectionStatus", e)
                        "Connection refused - camera may be offline or wrong port"
                    }

                    is javax.net.ssl.SSLException -> {
                        Log.e(TAG, "SSL error during setMotionDetectionStatus", e)
                        "SSL/TLS error - check camera security settings"
                    }

                    else -> {
                        Log.e(TAG, "Exception during setMotionDetectionStatus", e)
                        "Network error: ${e.message ?: "Unknown error"}"
                    }
                }
                ApiResult.Error(errorMessage)
            }
        }
    }

    private fun parseMotionDetectionStatus(responseBody: String): Boolean? {
        return try {
            val lines = responseBody.split("\n")

            for (line in lines) {
                Log.v(TAG, "Checking line: $line")
                if (line.startsWith("table.MotionDetect[0].Enable=")) {
                    val value = line.split("=").getOrNull(1)?.trim()
                    val lowercase = value?.lowercase()
                    val isEnabled = lowercase == "true"
                    return isEnabled
                }
            }
            Log.w(TAG, "Motion detection status line not found in response")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Exception during parseMotionDetectionStatus", e)
            null
        }
    }

}
