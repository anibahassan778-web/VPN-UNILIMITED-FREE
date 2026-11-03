package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object IpLookupService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    suspend fun fetchCurrentIp(): String? = withContext(Dispatchers.IO) {
        // Try fast IP services in order of speed
        val endpoints = listOf(
            "https://api64.ipify.org?format=json",
            "https://api.ipify.org?format=json",
            "https://icanhazip.com"
        )

        for (url in endpoints) {
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()?.trim() ?: continue
                    if (body.startsWith("{")) {
                        val json = JSONObject(body)
                        val ip = json.optString("ip")
                        if (ip.isNotBlank()) return@withContext ip
                    } else if (body.isNotBlank() && body.length < 45) {
                        return@withContext body
                    }
                }
            } catch (_: Exception) {
                // Try next endpoint
            }
        }
        null
    }
}
