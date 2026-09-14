package com.example.data.repository

import com.example.data.supabase.SupabaseService
import com.example.model.SessionUser
import com.example.model.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Real code authentication for students, teachers and parents.
 * No demo/fake identities are accepted here.
 */
class CodeAuthService {
    private val supabase = SupabaseService()

    suspend fun login(rawCode: String): Result<SessionUser> = withContext(Dispatchers.IO) {
        val code = rawCode.trim().uppercase()
        if (code.isBlank()) return@withContext Result.failure(Exception("يرجى إدخال كود الدخول"))

        when {
            code.startsWith("STU") -> loginStudent(code)
            code.startsWith("TCH") -> loginTeacher(code)
            code.startsWith("PAR") -> loginParent(code)
            else -> Result.failure(Exception("الكود غير صحيح. استخدم كود الطالب أو المعلم أو ولي الأمر المسجل في المنصة."))
        }
    }

    private suspend fun loginStudent(code: String): Result<SessionUser> {
        val rows = supabase.queryTable("students", "select=*&student_code=eq.$code&limit=1")
            .getOrElse { return Result.failure(Exception("تعذر الاتصال بقاعدة بيانات الطلاب: ${it.message}")) }
        if (rows.length() == 0) return Result.failure(Exception("كود الطالب $code غير موجود في قاعدة بيانات الوسام."))
        val row = rows.getJSONObject(0)
        val name = displayName(row, "طالب $code")
        val id = row.optString("id").ifBlank { row.optString("user_id") }
        if (id.isBlank()) return Result.failure(Exception("سجل الطالب $code لا يحتوي على رقم تعريف صالح."))
        val className = row.optString("class_name").ifBlank { row.optString("grade").ifBlank { row.optString("grade_name") } }
        return Result.success(SessionUser(id, code, name, UserRole.STUDENT, className = className.ifBlank { null }, email = row.optString("email").ifBlank { null }))
    }

    private suspend fun loginTeacher(code: String): Result<SessionUser> {
        val rows = supabase.queryTable("teachers", "select=*&teacher_code=eq.$code&limit=1")
            .getOrElse { return Result.failure(Exception("تعذر الاتصال بقاعدة بيانات المعلمين: ${it.message}")) }
        if (rows.length() == 0) return Result.failure(Exception("كود المعلم $code غير موجود في قاعدة بيانات الوسام."))
        val row = rows.getJSONObject(0)
        val name = displayName(row, "المعلم $code")
        val id = row.optString("id").ifBlank { row.optString("user_id") }
        if (id.isBlank()) return Result.failure(Exception("سجل المعلم $code لا يحتوي على رقم تعريف صالح."))
        return Result.success(SessionUser(id, code, name, UserRole.TEACHER, className = row.optString("class_name").ifBlank { null }, email = row.optString("email").ifBlank { null }))
    }

    private suspend fun loginParent(code: String): Result<SessionUser> {
        val rows = supabase.queryTable("parents", "select=*&parent_code=eq.$code&limit=1")
            .getOrElse { return Result.failure(Exception("تعذر الاتصال بقاعدة بيانات أولياء الأمور: ${it.message}")) }
        if (rows.length() == 0) return Result.failure(Exception("كود ولي الأمر $code غير موجود في قاعدة بيانات الوسام."))
        val row = rows.getJSONObject(0)
        val name = displayName(row, "ولي الأمر $code")
        val id = row.optString("id").ifBlank { row.optString("user_id") }
        if (id.isBlank()) return Result.failure(Exception("سجل ولي الأمر $code لا يحتوي على رقم تعريف صالح."))
        val linked = linkedChildren(row)
        return Result.success(SessionUser(id, code, name, UserRole.PARENT, linkedStudentCodes = linked, email = row.optString("email").ifBlank { null }))
    }

    private fun displayName(row: JSONObject, fallback: String): String {
        val direct = listOf("full_name", "name", "display_name").asSequence()
            .map { row.optString(it).trim() }.firstOrNull { it.isNotBlank() }
        if (!direct.isNullOrBlank()) return direct
        val parts = listOf("first_name", "middle_name", "last_name")
            .map { row.optString(it).trim() }.filter { it.isNotBlank() }
        return parts.joinToString(" ").ifBlank { fallback }
    }

    private fun linkedChildren(row: JSONObject): List<String> {
        val keys = listOf(
            "linked_student_codes", "student_codes", "children_codes", "linked_children",
            "child_codes", "children", "students"
        )
        for (key in keys) {
            val raw = row.opt(key) ?: continue
            val result = extractStudentCodes(raw)
            if (result.isNotEmpty()) return result
        }

        val one = listOf("student_code", "child_code", "linked_student_code")
            .asSequence()
            .map { row.optString(it).trim().uppercase() }
            .firstOrNull { it.startsWith("STU") }
        return one?.let { listOf(it) } ?: emptyList()
    }

    private fun extractStudentCodes(raw: Any): List<String> {
        return when (raw) {
            is JSONArray -> {
                buildList {
                    for (i in 0 until raw.length()) {
                        val item = raw.opt(i)
                        when (item) {
                            is JSONObject -> {
                                val code = listOf("student_code", "code", "child_code")
                                    .asSequence().map { item.optString(it).trim().uppercase() }
                                    .firstOrNull { it.startsWith("STU") }
                                if (code != null) add(code)
                            }
                            else -> {
                                val code = item?.toString()?.trim()?.uppercase().orEmpty()
                                if (code.startsWith("STU")) add(code)
                            }
                        }
                    }
                }.distinct()
            }
            is String -> raw.split(',', ';', '|').map { it.trim().uppercase() }.filter { it.startsWith("STU") }.distinct()
            else -> emptyList()
        }
    }
}
