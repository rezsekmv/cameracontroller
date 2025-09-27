package hu.rezsekmv.cameracontroller.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.net.SocketTimeoutException
import java.security.MessageDigest
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
    private var getConfigPath: String = "/cgi-bin/configManager.cgi?action=getConfig&name=MotionDetect"
    private var setConfigPath: String = "/cgi-bin/configManager.cgi?action=setConfig&MotionDetect[].Enable="
    private var timeoutSeconds: Int = 2
    
    companion object {
        private const val TAG = "CameraRepository"
    }

    fun updateEndpoint(endpoint: String) {
        if (endpoint != currentEndpoint) {
            Log.d(TAG, "Updating endpoint from '$currentEndpoint' to '$endpoint'")
            currentEndpoint = endpoint
            apiService = createApiService(endpoint)
            Log.d(TAG, "API service created for endpoint: $endpoint")
        }
    }
    
    fun updateConfiguration(getConfigPath: String, setConfigPath: String, timeoutSeconds: Int) {
        Log.d(TAG, "Updating configuration - getPath: $getConfigPath, setPath: $setConfigPath, timeout: ${timeoutSeconds}s")
        this.getConfigPath = getConfigPath
        this.setConfigPath = setConfigPath
        if (this.timeoutSeconds != timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds
            if (currentEndpoint.isNotEmpty()) {
                apiService = createApiService(currentEndpoint)
                Log.d(TAG, "Recreated API service with new timeout: ${timeoutSeconds}s")
            }
        }
    }

    private fun createApiService(endpoint: String): CameraApiService? {
        return try {
            Log.d(TAG, "Creating API service for endpoint: $endpoint")
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
            
            Log.d(TAG, "Parsed endpoint - baseUrl: $baseUrl, username: $username")

            // Create HTTP logging interceptor
            val loggingInterceptor = HttpLoggingInterceptor { message ->
                Log.d("$TAG-HTTP", message)
            }.apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .authenticator { _, response ->
                    Log.d(TAG, "Authenticator called with response code: ${response.code}")
                    
                    if (response.request.header("Authorization") != null) {
                        Log.d(TAG, "Request already has Authorization header, skipping")
                        return@authenticator null
                    }
                    
                    val authHeader = response.header("WWW-Authenticate")
                    Log.d(TAG, "WWW-Authenticate header: $authHeader")
                    
                    if (authHeader != null && authHeader.contains("Digest")) {
                        Log.d(TAG, "Creating digest authentication")
                        val digestAuth = createDigestAuth(authHeader, username, password, response.request)
                        Log.d(TAG, "Generated digest auth: $digestAuth")
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
            Log.d(TAG, "Successfully created API service")
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
                    return@withContext MotionDetectionStatus(null, false, "Camera service not initialized")
                }
                
                Log.d(TAG, "Starting getMotionDetectionStatus API call with path: $getConfigPath")
                val response = service.getRequest(getConfigPath)
                Log.d(TAG, "getMotionDetectionStatus API call completed - success: ${response.isSuccessful}, code: ${response.code()}")
                
                if (response.isSuccessful) {
                    val body = response.body()
                    Log.d(TAG, "Response body: $body")
                    val isEnabled = body?.let { parseMotionDetectionStatus(it) }
                    Log.d(TAG, "Parsed motion detection status: $isEnabled")
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
                    Log.e(TAG, "API call failed with code: ${response.code()}, message: ${response.message()}")
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
                Log.d(TAG, "Starting setMotionDetectionStatus API call with path: $fullPath")
                val response = service.getRequest(fullPath)
                Log.d(TAG, "setMotionDetectionStatus API call completed - success: ${response.isSuccessful}, code: ${response.code()}")
                
                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully set motion detection to: $enable")
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
                    Log.e(TAG, "Failed to set motion detection - code: ${response.code()}, message: ${response.message()}")
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
            Log.d(TAG, "Parsing motion detection status from response")
            val lines = responseBody.split("\n")
            Log.d(TAG, "Response has ${lines.size} lines")
            
            for (line in lines) {
                Log.v(TAG, "Checking line: $line")
                if (line.startsWith("table.MotionDetect[0].Enable=")) {
                    val value = line.split("=").getOrNull(1)?.trim()
                    val lowercase = value?.lowercase()
                    val isEnabled = lowercase == "true"
                    Log.d(TAG, "Found motion detection line: $line")
                    Log.d(TAG, "Raw value: '$value', lowercase: '$lowercase', equals 'true': $isEnabled")
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

    private fun createDigestAuth(authHeader: String, username: String, password: String, request: Request): String {
        Log.d(TAG, "Creating digest auth from header: $authHeader")
        val params = parseDigestHeader(authHeader)
        val realm = params["realm"] ?: ""
        val nonce = params["nonce"] ?: ""
        val qop = params["qop"]
        val algorithm = params["algorithm"] ?: "MD5"
        
        Log.d(TAG, "Digest params - realm: $realm, nonce: $nonce, qop: $qop, algorithm: $algorithm")
        
        val uri = request.url.encodedPath + if (request.url.encodedQuery != null) "?${request.url.encodedQuery}" else ""
        val method = request.method
        
        Log.d(TAG, "Request details - method: $method, uri: $uri")
        
        val ha1 = md5("$username:$realm:$password")
        val ha2 = md5("$method:$uri")
        
        Log.d(TAG, "Hash calculations - ha1: $ha1, ha2: $ha2")
        
        val response = if (qop == "auth") {
            val nc = "00000001"
            val cnonce = "0a4f113b"
            md5("$ha1:$nonce:$nc:$cnonce:$qop:$ha2")
        } else {
            md5("$ha1:$nonce:$ha2")
        }
        
        Log.d(TAG, "Final response hash: $response")
        
        return buildString {
            append("Digest ")
            append("username=\"$username\", ")
            append("realm=\"$realm\", ")
            append("nonce=\"$nonce\", ")
            append("uri=\"$uri\", ")
            append("algorithm=\"$algorithm\", ")
            append("response=\"$response\"")
            if (qop == "auth") {
                append(", qop=\"auth\", nc=00000001, cnonce=\"0a4f113b\"")
            }
        }
    }
    
    private fun parseDigestHeader(header: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        val regex = """(\w+)="([^"]+)"""".toRegex()
        regex.findAll(header).forEach { match ->
            params[match.groupValues[1]] = match.groupValues[2]
        }
        return params
    }
    
    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
