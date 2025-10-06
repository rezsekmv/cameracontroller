package hu.rezsekmv.cameracontroller.util

import android.util.Log
import okhttp3.Request
import java.security.MessageDigest

object DigestAuthUtil {
    
    private const val TAG = "DigestAuthUtil"
    
    fun createDigestAuth(authHeader: String, username: String, password: String, request: Request): String {
        val params = parseDigestHeader(authHeader)
        val realm = params["realm"] ?: ""
        val nonce = params["nonce"] ?: ""
        val qop = params["qop"]
        val algorithm = params["algorithm"] ?: "MD5"
        
        val uri = request.url.encodedPath + if (request.url.encodedQuery != null) "?${request.url.encodedQuery}" else ""
        val method = request.method
        
        val ha1 = md5("$username:$realm:$password")
        val ha2 = md5("$method:$uri")
        
        val response = if (qop == "auth") {
            val nc = "00000001"
            val cnonce = "0a4f113b"
            md5("$ha1:$nonce:$nc:$cnonce:$qop:$ha2")
        } else {
            md5("$ha1:$nonce:$ha2")
        }
        
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
    
    fun parseDigestHeader(header: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        val regex = """(\w+)="([^"]+)"""".toRegex()
        regex.findAll(header).forEach { match ->
            params[match.groupValues[1]] = match.groupValues[2]
        }
        return params
    }
    
    fun md5(input: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating MD5 hash", e)
            ""
        }
    }
    
    fun isDigestChallenge(authHeader: String?): Boolean {
        return authHeader != null && authHeader.contains("Digest", ignoreCase = true)
    }
}
