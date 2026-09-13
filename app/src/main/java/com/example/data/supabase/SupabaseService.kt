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

    private fun buildRequest(url: String, method: String = "GET", body: String? = null): Request {
        val builder = Request.Builder()
            .url(url)
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .addHeader("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
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

    suspend fun queryTable(table: String, queryParams: String = "select=*"): Result<JSONArray> =
        withContext(Dispatchers.IO) {
            try {
                val url = "${SupabaseConfig.REST_URL}/$table?$queryParams"
                val request = buildRequest(url, "GET")
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: "[]"

                if (response.isSuccessful) {
                    try {
                        Result.success(JSONArray(responseBody))
                    } catch (e: Exception) {
                        // Maybe single object returned
                        val arr = JSONArray()
                        if (responseBody.trim().startsWith("{")) {
                            arr.put(JSONObject(responseBody))
                        }
                        Result.success(arr)
                    }
                } else {
                    Log.w("SupabaseService", "Query $table failed [${response.code}]: $responseBody")
                    Result.failure(Exception("خطأ في قراءة البيانات من خادم Supabase: كود ${response.code}"))
                }
            } catch (e: Exception) {
                Log.e("SupabaseService", "Network exception for $table", e)
                Result.failure(Exception("تعذر الاتصال بالخادم، يرجى التحقق من اتصال الإنترنت والمحاولة ثانية."))
            }
        }

    suspend fun insertRow(table: String, jsonObject: JSONObject): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            try {
                val url = "${SupabaseConfig.REST_URL}/$table"
                val request = buildRequest(url, "POST", jsonObject.toString())
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: "{}"

                if (response.isSuccessful) {
                    try {
                        val arr = JSONArray(responseBody)
                        if (arr.length() > 0) {
                            Result.success(arr.getJSONObject(0))
                        } else {
                            Result.success(jsonObject)
                        }
                    } catch (e: Exception) {
                        try {
                            Result.success(JSONObject(responseBody))
                        } catch (ex: Exception) {
                            Result.success(jsonObject)
                        }
                    }
                } else {
                    Log.w("SupabaseService", "Insert to $table failed [${response.code}]: $responseBody")
                    Result.failure(Exception("تعذر حفظ البيانات في Supabase: $responseBody"))
                }
            } catch (e: Exception) {
                Log.e("SupabaseService", "Network exception inserting into $table", e)
                Result.failure(Exception("تعذر تسليم البيانات، يرجى التأكد من اتصال الإنترنت والمحاولة مرة أخرى."))
            }
        }
}
