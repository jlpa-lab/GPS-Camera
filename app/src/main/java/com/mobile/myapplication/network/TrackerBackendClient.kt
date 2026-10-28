package com.mobile.myapplication.network

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.mobile.myapplication.ads.RemoteConfigUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class TrackerBackendClient(context: Context) {
    private class BackendHttpException(val status: Int, message: String) : IllegalStateException(message)
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("tracker_backend", Context.MODE_PRIVATE)
    private val deviceId = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
    private val deviceModel = Build.MODEL.trim().ifBlank { "Android device" }
    private val appId = "phone-tracker-0012"
    private suspend fun call(path: String, method: String, body: JSONObject? = null, retryAuth: Boolean = true): JSONObject {
        val response = withContext(Dispatchers.IO) {
            val connection = (URL(RemoteConfigUtils.getBackendBaseUrl().trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method; connectTimeout = 10_000; readTimeout = 10_000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("X-Device-ID", deviceId); setRequestProperty("X-App-ID", appId)
                prefs.getString("session_token", null)?.let { setRequestProperty("X-Session-Token", it) }
                if (body != null) { doOutput = true; outputStream.use { it.write(body.toString().toByteArray()) } }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            status to text
        }
        val (status, text) = response
        if (status in 200..299) return JSONObject(text)

        // A previous app session can be invalidated by a reinstall, a cleared backend,
        // or an older screen that registered the same device again. Refresh once and retry.
        if (status == HttpURLConnection.HTTP_UNAUTHORIZED && retryAuth && path != "/api/v1/installations") {
            refreshSession(prefs.getString("session_token", null))
            return call(path, method, body, retryAuth = false)
        }
        throw BackendHttpException(
            status,
            runCatching { JSONObject(text).optString("error") }.getOrNull().orEmpty().ifBlank { "backend request failed ($status)" }
        )
    }

    private suspend fun refreshSession(invalidToken: String?) = registerMutex.withLock {
        // If another request already refreshed the same invalid token, reuse its result.
        val current = prefs.getString("session_token", null)
        if (!current.isNullOrBlank() && current != invalidToken) return@withLock
        prefs.edit().remove("session_token").apply()
        registerLocked()
    }

    suspend fun register(name: String? = null, avatar: String? = null) = registerMutex.withLock {
        // Reuse the existing session so multiple screens/services do not invalidate each other.
        if (!prefs.getString("session_token", null).isNullOrBlank()) return@withLock
        registerLocked(name, avatar)
    }

    private suspend fun registerLocked(name: String? = null, avatar: String? = null) {
        // Registration is automatic: use the Android model as the profile name.
        // Avatar is intentionally omitted unless a caller explicitly provides one.
        val profileName = name?.takeIf { it.isNotBlank() } ?: deviceModel
        val body = JSONObject().put("device_id", deviceId).put("app_id", appId).put("name", profileName).put("device_model", deviceModel)
        avatar?.takeIf { it.isNotBlank() }?.let { body.put("avatar", it) }
        val result = call("/api/v1/installations", "POST", body)
        prefs.edit().putString("session_token", result.getString("session_token")).apply()
    }
    suspend fun updateProfile(name: String) = call("/api/v1/profile", "PUT", JSONObject().put("name", name))
    suspend fun createFriendInvite() = call("/api/v1/friends/invite", "POST")
    suspend fun sendFriendRequest(code: String) = call("/api/v1/friends/requests", "POST", JSONObject().put("code", code))
    suspend fun listRequests() = call("/api/v1/friends/requests", "GET")
    suspend fun listSentRequests() = call("/api/v1/friends/requests/sent", "GET")
    suspend fun listFriends() = call("/api/v1/friends", "GET")
    suspend fun listFriendsOrLegacy(): JSONObject {
        return try {
            listFriends()
        } catch (endpointError: BackendHttpException) {
            if (endpointError.status != HttpURLConnection.HTTP_NOT_FOUND) throw endpointError
            // Older deployed backends do not have /friends yet. Reconstruct the
            // accepted-friend list from the request endpoints so the map never
            // turns a backend version mismatch into a false empty state.
            val fallback = JSONArray()
            val seen = mutableSetOf<String>()
            var loaded = false

            runCatching { listRequests() }.getOrNull()?.optJSONArray("requests")?.let { requests ->
                loaded = true
                for (index in 0 until requests.length()) {
                    val row = requests.optJSONObject(index) ?: continue
                    if (row.optString("status") != "accepted") continue
                    val id = row.optString("from_id").trim()
                    if (id.isBlank() || !seen.add(id)) continue
                    fallback.put(JSONObject()
                        .put("friend_id", id)
                        .put("name", row.optString("from_name").ifBlank { id })
                        .put("device_model", row.optString("from_model").ifBlank { row.optString("from_name").ifBlank { "Android device" } })
                        .put("permission_granted", false)
                        .put("sharing_enabled", false)
                        .put("location_request_status", "none"))
                }
            }
            runCatching { listSentRequests() }.getOrNull()?.optJSONArray("requests")?.let { requests ->
                loaded = true
                for (index in 0 until requests.length()) {
                    val row = requests.optJSONObject(index) ?: continue
                    if (row.optString("status") != "accepted") continue
                    val id = row.optString("to_id").trim()
                    if (id.isBlank() || !seen.add(id)) continue
                    fallback.put(JSONObject()
                        .put("friend_id", id)
                        .put("name", row.optString("to_name").ifBlank { id })
                        .put("device_model", row.optString("to_model").ifBlank { row.optString("to_name").ifBlank { "Android device" } })
                        .put("permission_granted", false)
                        .put("sharing_enabled", false)
                        .put("location_request_status", "none"))
                }
            }
            if (!loaded) throw endpointError
            JSONObject().put("friends", fallback).put("legacy_fallback", true)
        }
    }
    suspend fun requestLocation(friendId: String) = call("/api/v1/friends/location-requests", "POST", JSONObject().put("friend_id", friendId))
    suspend fun listLocationRequests() = call("/api/v1/friends/location-requests", "GET")
    suspend fun decideLocationRequest(id: String, action: String) = call("/api/v1/friends/location-requests/$id", "POST", JSONObject().put("action", action))
    suspend fun hasAcceptedFriends(): Boolean {
        register()
        val friends = runCatching { listFriends().optJSONArray("friends") }.getOrNull()
        if (friends != null) return friends.length() > 0
        val incoming = listRequests().optJSONArray("requests")
        val outgoing = listSentRequests().optJSONArray("requests")
        fun containsAccepted(array: JSONArray?): Boolean {
            if (array == null) return false
            for (index in 0 until array.length()) {
                if (array.optJSONObject(index)?.optString("status") == "accepted") return true
            }
            return false
        }
        return containsAccepted(incoming) || containsAccepted(outgoing)
    }
    suspend fun decideRequest(id: String, action: String) = call("/api/v1/friends/requests/$id", "POST", JSONObject().put("action", action))
    suspend fun cancelFriendRequest(id: String) = decideRequest(id, "cancel")
    suspend fun submitFeedback(rating: Int, feedback: String) = call("/api/v1/feedback", "POST", JSONObject().put("rating", rating).put("feedback", feedback))

    companion object {
        private val registerMutex = Mutex()
    }
}
