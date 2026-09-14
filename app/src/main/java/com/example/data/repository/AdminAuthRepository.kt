package com.example.data.repository

import android.content.Context
import com.example.data.supabase.SupabaseService
import com.example.model.SessionUser
import com.example.model.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AdminAuthRepository(context: Context) {
    private val prefs = context.getSharedPreferences("alwissam_admin_auth", Context.MODE_PRIVATE)
    private val supabase = SupabaseService()

    fun restoreSession(): SessionUser? {
        if (!prefs.getBoolean("logged_in", false)) return null
        val id = prefs.getString("id", null) ?: return null
        val email = prefs.getString("email", null) ?: return null
        return SessionUser(id, "ADMIN", "مدير منصة الوسام", UserRole.ADMIN, email = email)
    }

    suspend fun signIn(email: String, password: String): Result<SessionUser> = withContext(Dispatchers.IO) {
        val auth = supabase.signInWithPassword(email, password)
        auth.map { json ->
            val user = json.optJSONObject("user")
            val id = user?.optString("id").orEmpty().ifBlank { "admin-auth" }
            val normalizedEmail = user?.optString("email").orEmpty().ifBlank { email.trim() }
            val session = SessionUser(id, "ADMIN", "مدير منصة الوسام", UserRole.ADMIN, email = normalizedEmail)
            prefs.edit()
                .putBoolean("logged_in", true)
                .putString("id", session.id)
                .putString("email", session.email)
                .apply()
            session
        }
    }

    fun logout() {
        prefs.edit().clear().apply()
        supabase.clearSession()
    }
}
