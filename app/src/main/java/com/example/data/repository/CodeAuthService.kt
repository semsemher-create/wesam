package com.example.data.repository

import com.example.data.supabase.SupabaseService
import com.example.model.SessionUser
import com.example.model.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Real code authentication for students, teachers and parents.
 * The code is sent as a scoped request credential; RLS decides which row is visible.
 */
class CodeAuthService {
    private val supabase = SupabaseService()

    suspend fun login(rawCode: String): Result<SessionUser> = withContext(Dispatchers.IO) {
        val code = rawCode.trim().uppercase()
        if (code.isBlank()) return@withContext Result.failure(Exception("يرجى إدخال كود الدخول"))
        SupabaseService.setUserCode(code)

        val result = when {
            code.startsWith("STU") -> loginStudent(code)
            code.startsWith("TCH") -> loginTeacher(code)
            code.startsWith("PAR") -> loginParent(code)
            else -> Result.failure(Exception("الكود غير صحيح. استخدم كود الطالب أو المعلم أو ولي الأمر المسجل في المنصة."))
        }
        if (result.isFailure) SupabaseService.clearUserCode()
        result
    }

    private suspend fun loginStudent(code: String): Result<SessionUser> {
        val rows = supabase.queryTable("students", "select=id,full_name,student_code,grade_level,user_id&student_code=eq.$code&limit=1")
            .getOrElse { return Result.failure(Exception("تعذر الاتصال بقاعدة بيانات الطلاب: ${it.message}")) }
        if (rows.length() == 0) return Result.failure(Exception("كود الطالب $code غير موجود في قاعدة بيانات الوسام."))
        val row = rows.getJSONObject(0)
        val id = row.optString("id").ifBlank { row.optString("user_id") }
        if (id.isBlank()) return Result.failure(Exception("سجل الطالب $code لا يحتوي على رقم تعريف صالح."))
        return Result.success(SessionUser(id, code, row.optString("full_name", "طالب $code"), UserRole.STUDENT, className = row.optString("grade_level").ifBlank { null }))
    }

    private suspend fun loginTeacher(code: String): Result<SessionUser> {
        val rows = supabase.queryTable("teachers", "select=id,full_name,teacher_code,subject,user_id&teacher_code=eq.$code&limit=1")
            .getOrElse { return Result.failure(Exception("تعذر الاتصال بقاعدة بيانات المعلمين: ${it.message}")) }
        if (rows.length() == 0) return Result.failure(Exception("كود المعلم $code غير موجود في قاعدة بيانات الوسام."))
        val row = rows.getJSONObject(0)
        val id = row.optString("id").ifBlank { row.optString("user_id") }
        if (id.isBlank()) return Result.failure(Exception("سجل المعلم $code لا يحتوي على رقم تعريف صالح."))
        return Result.success(SessionUser(id, code, row.optString("full_name", "المعلم $code"), UserRole.TEACHER, className = row.optString("subject").ifBlank { null }))
    }

    private suspend fun loginParent(code: String): Result<SessionUser> {
        val rows = supabase.queryTable("parents", "select=id,full_name,parent_code,user_id&parent_code=eq.$code&limit=1")
            .getOrElse { return Result.failure(Exception("تعذر الاتصال بقاعدة بيانات أولياء الأمور: ${it.message}")) }
        if (rows.length() == 0) return Result.failure(Exception("كود ولي الأمر $code غير موجود في قاعدة بيانات الوسام."))
        val row = rows.getJSONObject(0)
        val id = row.optString("id").ifBlank { row.optString("user_id") }
        if (id.isBlank()) return Result.failure(Exception("سجل ولي الأمر $code لا يحتوي على رقم تعريف صالح."))
        val linked = loadLinkedChildren(row.optString("id"))
        return Result.success(SessionUser(id, code, row.optString("full_name", "ولي الأمر $code"), UserRole.PARENT, linkedStudentCodes = linked))
    }

    private suspend fun loadLinkedChildren(parentId: String): List<String> {
        if (parentId.isBlank()) return emptyList()
        val rows = supabase.queryTable("parent_students", "select=student_id&parent_id=eq.$parentId").getOrNull() ?: return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until rows.length()) {
            val studentId = rows.getJSONObject(i).optString("student_id")
            if (studentId.isBlank()) continue
            val students = supabase.queryTable("students", "select=student_code&id=eq.$studentId&limit=1").getOrNull() ?: continue
            if (students.length() > 0) {
                val code = students.getJSONObject(0).optString("student_code").uppercase()
                if (code.startsWith("STU")) result += code
            }
        }
        return result.distinct()
    }
}
