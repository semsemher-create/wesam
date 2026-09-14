package com.example.data.supabase

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SupabaseService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    @Volatile private var accessToken: String? = null

    fun setAccessToken(token: String?) { accessToken = token }

    private fun buildRequest(url: String, method: String = "GET", body: String? = null): Request {
        val token = accessToken ?: SupabaseConfig.ANON_KEY
        val builder = Request.Builder()
            .url(url)
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=representation")
        when (method) {
            "POST" -> builder.post((body ?: "{}").toRequestBody(jsonMediaType))
            "PATCH" -> builder.patch((body ?: "{}").toRequestBody(jsonMediaType))
            "DELETE" -> builder.delete()
            else -> builder.get()
        }
        return builder.build()
    }

    suspend fun signInWithPassword(email: String, password: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply { put("email", email.trim()); put("password", password) }
            val response = client.newCall(
                Request.Builder()
                    .url("${SupabaseConfig.BASE_URL}/auth/v1/token?grant_type=password")
                    .addHeader("apikey", SupabaseConfig.ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(body.toString().toRequestBody(jsonMediaType))
                    .build()
            ).execute()
            val responseBody = response.body?.string() ?: "{}"
            if (!response.isSuccessful) {
                val message = runCatching {
                    val errorJson = JSONObject(responseBody)
                    errorJson.optString("msg").ifBlank { errorJson.optString("error_description") }
                }.getOrNull().orEmpty()
                return@withContext Result.failure(Exception(message.ifBlank { "بيانات الإدارة غير صحيحة" }))
            }
            val json = JSONObject(responseBody)
            val token = json.optString("access_token")
            if (token.isBlank()) return@withContext Result.failure(Exception("لم يتم استلام جلسة دخول صالحة من Supabase"))
            accessToken = token
            Result.success(json)
        } catch (e: Exception) {
            Log.e("SupabaseService", "Admin authentication failed", e)
            Result.failure(Exception("تعذر الاتصال بخدمة تسجيل الدخول. تحقق من الإنترنت."))
        }
    }

    fun clearSession() { accessToken = null }

    suspend fun queryTable(table: String, queryParams: String = "select=*"): Result<JSONArray> = withContext(Dispatchers.IO) {
        try {
            val response = client.newCall(buildRequest("${SupabaseConfig.REST_URL}/$table?$queryParams")).execute()
            val responseBody = response.body?.string() ?: "[]"
            if (!response.isSuccessful) {
                Log.w("SupabaseService", "Query $table failed [${response.code}]: $responseBody")
                return@withContext Result.failure(Exception("خطأ في قراءة البيانات من Supabase: ${response.code}"))
            }
            try { Result.success(JSONArray(responseBody)) }
            catch (_: Exception) { Result.success(JSONArray().apply { if (responseBody.trim().startsWith("{")) put(JSONObject(responseBody)) }) }
        } catch (e: Exception) {
            Log.e("SupabaseService", "Network exception for $table", e)
            Result.failure(Exception("تعذر الاتصال بخادم الوسام."))
        }
    }

    suspend fun insertRow(table: String, jsonObject: JSONObject): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val response = client.newCall(buildRequest("${SupabaseConfig.REST_URL}/$table", "POST", jsonObject.toString())).execute()
            val responseBody = response.body?.string() ?: "{}"
            if (!response.isSuccessful) return@withContext Result.failure(Exception("تعذر حفظ البيانات في Supabase."))
            try {
                val arr = JSONArray(responseBody)
                Result.success(if (arr.length() > 0) arr.getJSONObject(0) else jsonObject)
            } catch (_: Exception) { Result.success(JSONObject(responseBody.ifBlank { jsonObject.toString() })) }
        } catch (e: Exception) {
            Result.failure(Exception("تعذر حفظ البيانات، تحقق من اتصال الإنترنت."))
        }
    }

    suspend fun callRpc(functionName: String, payload: JSONObject): Result<JSONArray> = withContext(Dispatchers.IO) {
        try {
            val response = client.newCall(buildRequest("${SupabaseConfig.REST_URL}/rpc/$functionName", "POST", payload.toString())).execute()
            val body = response.body?.string() ?: "[]"
            if (!response.isSuccessful) return@withContext Result.failure(Exception(body.ifBlank { "فشل تنفيذ العملية على الخادم." }))
            try { Result.success(JSONArray(body)) }
            catch (_: Exception) { Result.success(JSONArray().apply { if (body.trim().startsWith("{")) put(JSONObject(body)) }) }
        } catch (e: Exception) {
            Result.failure(Exception("تعذر الاتصال بخادم الوسام."))
        }
    }
}
