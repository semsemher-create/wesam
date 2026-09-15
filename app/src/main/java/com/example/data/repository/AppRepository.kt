package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.supabase.SupabaseService
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class AppRepository(private val context: Context) {
    private val supabase = SupabaseService()
    private val prefs: SharedPreferences = context.getSharedPreferences("alwissam_prefs", Context.MODE_PRIVATE)

    private val _currentUser = MutableStateFlow<SessionUser?>(null)
    val currentUser: StateFlow<SessionUser?> = _currentUser.asStateFlow()
    private val _assignments = MutableStateFlow<List<Assignment>>(emptyList())
    val assignments: StateFlow<List<Assignment>> = _assignments.asStateFlow()
    private val _submissions = MutableStateFlow<List<AssignmentSubmission>>(emptyList())
    val submissions: StateFlow<List<AssignmentSubmission>> = _submissions.asStateFlow()
    private val _codingTasks = MutableStateFlow<List<CodingTask>>(emptyList())
    val codingTasks: StateFlow<List<CodingTask>> = _codingTasks.asStateFlow()
    private val _codingSubmissions = MutableStateFlow<List<CodingSubmission>>(emptyList())
    val codingSubmissions: StateFlow<List<CodingSubmission>> = _codingSubmissions.asStateFlow()

    init {
        restoreSession()
        loadFallbackData()
        _submissions.value = parseSubmissions(prefs.getString("saved_submissions_json", "[]"))
        _codingSubmissions.value = parseCodingSubmissions(prefs.getString("saved_coding_subs_json", "[]"))
    }

    private fun restoreSession() {
        val id = prefs.getString("user_id", null) ?: return
        val code = prefs.getString("user_code", null) ?: return
        val name = prefs.getString("user_name", null) ?: return
        val role = prefs.getString("user_role", null) ?: return
        val linked = prefs.getString("user_linked_codes", "") ?: ""
        runCatching { _currentUser.value = SessionUser(id, code, name, UserRole.valueOf(role), linkedStudentCodes = linked.split(",").filter { it.isNotBlank() }) }
    }

    private fun saveSession(user: SessionUser) {
        prefs.edit().putString("user_id", user.id).putString("user_code", user.code).putString("user_name", user.name).putString("user_role", user.role.name).putString("user_linked_codes", user.linkedStudentCodes.joinToString(",")).apply()
        _currentUser.value = user
    }

    fun logout() {
        prefs.edit().clear().apply()
        _currentUser.value = null
        _submissions.value = emptyList()
        _codingSubmissions.value = emptyList()
    }

    suspend fun refreshForUser(user: SessionUser) = withContext(Dispatchers.IO) {
        refreshRemoteData()
        when (user.role) {
            UserRole.STUDENT -> refreshStudentSubmissions(listOf(user))
            UserRole.PARENT -> refreshParentSubmissions(user.linkedStudentCodes)
            else -> Unit
        }
    }

    suspend fun refreshRemoteData() = withContext(Dispatchers.IO) {
        refreshAssignments()
        refreshCodingTasks()
    }

    private suspend fun refreshAssignments() {
        val rows = supabase.queryTable("assignments", "select=*&order=created_at.asc").getOrNull() ?: return
        if (rows.length() == 0) return
        val questions = supabase.queryTable("assignment_questions", "select=*&order=sort_order.asc").getOrNull() ?: JSONArray()
        val subjects = supabase.queryTable("subjects", "select=id,name").getOrNull() ?: JSONArray()
        val classes = supabase.queryTable("classes", "select=id,name").getOrNull() ?: JSONArray()
        val profiles = supabase.queryTable("profiles", "select=id,full_name").getOrNull() ?: JSONArray()
        val subjectNames = mutableMapOf<String, String>()
        val classNames = mutableMapOf<String, String>()
        val teacherNames = mutableMapOf<String, String>()
        for (i in 0 until subjects.length()) { val r = subjects.getJSONObject(i); subjectNames[r.optString("id")] = r.optString("name") }
        for (i in 0 until classes.length()) { val r = classes.getJSONObject(i); classNames[r.optString("id")] = r.optString("name") }
        for (i in 0 until profiles.length()) { val r = profiles.getJSONObject(i); teacherNames[r.optString("id")] = r.optString("full_name") }
        val loaded = mutableListOf<Assignment>()
        for (i in 0 until rows.length()) {
            val r = rows.getJSONObject(i)
            val id = r.optString("id")
            val qs = mutableListOf<Question>()
            for (j in 0 until questions.length()) {
                val q = questions.getJSONObject(j)
                if (q.optString("assignment_id") != id) continue
                val opts = q.optJSONArray("options") ?: JSONArray()
                qs += Question(q.optString("id"), q.optString("prompt"), if (q.optString("question_type") == "true_false") QuestionType.TRUE_FALSE else QuestionType.MCQ, List(opts.length()) { k -> opts.optString(k) }, q.optInt("correct_index", -1), q.optInt("points", 1))
            }
            loaded += Assignment(id, r.optString("title"), r.optString("description", ""), r.optString("class_id").ifBlank { null }, classNames[r.optString("class_id")], r.optString("subject_id").ifBlank { null }, subjectNames[r.optString("subject_id")], teacherNames[r.optString("teacher_user_id")], r.optString("due_at").ifBlank { null }, r.optDouble("max_score", 10.0).toInt(), qs, r.optBoolean("is_coding", false), r.optString("coding_task_id").ifBlank { null })
        }
        if (loaded.isNotEmpty()) _assignments.value = loaded
    }

    private suspend fun refreshCodingTasks() {
        val rows = supabase.queryTable("coding_tasks", "select=*&is_active=eq.true&order=created_at.asc").getOrNull() ?: return
        if (rows.length() == 0) return
        val tests = supabase.queryTable("coding_test_cases", "select=id,task_id,input,expected_output,is_hidden,points,sort_order&is_hidden=eq.false&order=sort_order.asc").getOrNull() ?: JSONArray()
        val loaded = mutableListOf<CodingTask>()
        for (i in 0 until rows.length()) {
            val r = rows.getJSONObject(i)
            val lang = when (r.optString("language")) {
                "javascript" -> ProgrammingLanguage.JAVASCRIPT
                "python" -> ProgrammingLanguage.PYTHON
                else -> ProgrammingLanguage.HTML_CSS
            }
            val taskTests = mutableListOf<TestCase>()
            for (j in 0 until tests.length()) {
                val t = tests.getJSONObject(j)
                if (t.optString("task_id") == r.optString("id")) taskTests += TestCase(t.optString("id"), t.optString("input", ""), t.optString("expected_output"), false, t.optInt("points", 1))
            }
            loaded += CodingTask(r.optString("id"), r.optString("title"), r.optString("description", ""), lang, r.optString("starter_code", ""), r.optString("expected_output", ""), r.optDouble("max_score", 10.0).toInt(), taskTests, r.optString("assignment_id").ifBlank { null })
        }
        if (loaded.isNotEmpty()) _codingTasks.value = loaded
    }

    private suspend fun refreshStudentSubmissions(users: List<SessionUser>) {
        val assignmentRows = mutableListOf<JSONObject>()
        val codingRows = mutableListOf<JSONObject>()
        users.forEach { user ->
            val byId = supabase.queryTable("assignment_submissions", "select=*&student_id=eq.${user.id}&order=submitted_at.desc").getOrNull()
            byId?.let { for (i in 0 until it.length()) assignmentRows += it.getJSONObject(i) }
            val byCode = supabase.queryTable("assignment_submissions", "select=*&student_code=eq.${user.code}&order=submitted_at.desc").getOrNull()
            byCode?.let { for (i in 0 until it.length()) assignmentRows += it.getJSONObject(i) }
            val cById = supabase.queryTable("coding_submissions", "select=*&student_id=eq.${user.id}&order=submitted_at.desc").getOrNull()
            cById?.let { for (i in 0 until it.length()) codingRows += it.getJSONObject(i) }
            val cByCode = supabase.queryTable("coding_submissions", "select=*&student_code=eq.${user.code}&order=submitted_at.desc").getOrNull()
            cByCode?.let { for (i in 0 until it.length()) codingRows += it.getJSONObject(i) }
        }
        applyRemoteSubmissions(assignmentRows.distinctBy { it.optString("id") })
        applyRemoteCodingSubmissions(codingRows.distinctBy { it.optString("id") })
    }

    private suspend fun refreshParentSubmissions(childCodes: List<String>) {
        if (childCodes.isEmpty()) {
            _submissions.value = emptyList()
            _codingSubmissions.value = emptyList()
            return
        }
        val users = mutableListOf<SessionUser>()
        childCodes.distinct().forEach { code ->
            val rows = supabase.queryTable("students", "select=*&student_code=eq.$code&limit=1").getOrNull() ?: return@forEach
            if (rows.length() > 0) {
                val r = rows.getJSONObject(0)
                users += SessionUser(r.optString("id").ifBlank { r.optString("user_id") }, code, displayName(r, "طالب $code"), UserRole.STUDENT)
            }
        }
        refreshStudentSubmissions(users)
    }

    private fun applyRemoteSubmissions(rows: List<JSONObject>) {
        if (rows.isEmpty()) return
        val loaded = rows.mapNotNull { r ->
            val id = r.optString("id").ifBlank { return@mapNotNull null }
            val assignmentId = r.optString("assignment_id")
            val studentId = r.optString("student_id")
            val code = r.optString("student_code")
            AssignmentSubmission(id, assignmentId, studentId, code, r.optString("student_name", "طالب"), r.optDouble("score", 0.0), r.optDouble("max_score", 0.0), r.optString("feedback").ifBlank { null }, r.optString("submitted_at", r.optString("created_at", now())), emptyMap(), r.optString("status", "graded"))
        }
        _submissions.value = (_submissions.value.filterNot { old -> loaded.any { it.id == old.id } } + loaded).distinctBy { it.id }
        saveSubmissions()
    }

    private fun applyRemoteCodingSubmissions(rows: List<JSONObject>) {
        if (rows.isEmpty()) return
        val loaded = rows.mapNotNull { r ->
            val id = r.optString("id").ifBlank { return@mapNotNull null }
            val taskId = r.optString("task_id")
            val task = _codingTasks.value.find { it.id == taskId }
            CodingSubmission(id, taskId, task?.title ?: r.optString("task_title", "مختبر برمجة"), r.optString("student_id"), r.optString("student_code"), r.optString("source_code"), r.optString("language"), r.optDouble("score", 0.0), r.optDouble("max_score", task?.maxScore?.toDouble() ?: 10.0), r.optInt("passed_tests", 0), r.optInt("total_tests", 0), r.optString("test_output", r.optString("output", "")), r.optString("submitted_at", r.optString("created_at", now())), r.optString("status", "Submitted"))
        }
        _codingSubmissions.value = (_codingSubmissions.value.filterNot { old -> loaded.any { it.id == old.id } } + loaded).distinctBy { it.id }
        saveCodingSubmissions()
    }

    suspend fun submitAssignmentAnswers(assignmentId: String, studentUser: SessionUser, answers: Map<String, Int>): Result<AssignmentSubmission> = withContext(Dispatchers.IO) {
        val assignment = _assignments.value.find { it.id == assignmentId } ?: return@withContext Result.failure(Exception("الواجب غير موجود"))
        if (_submissions.value.any { it.assignmentId == assignmentId && it.studentCode == studentUser.code }) return@withContext Result.failure(Exception("لقد قمت بتسليم هذا الواجب مسبقاً."))
        val answerJson = JSONObject().apply { answers.forEach { (k, v) -> put(k, v) } }
        if (runCatching { UUID.fromString(assignmentId) }.isSuccess) {
            val rpc = supabase.callRpc("submit_assignment_auto", JSONObject().apply { put("p_assignment_id", assignmentId); put("p_student_id", studentUser.id); put("p_answers", answerJson) })
            if (rpc.isFailure) return@withContext Result.failure(rpc.exceptionOrNull() ?: Exception("تعذر حفظ الواجب"))
            val r = rpc.getOrNull()?.optJSONObject(0)
            val score = r?.optDouble("score", 0.0) ?: 0.0
            val max = r?.optDouble("max_score", assignment.maxScore.toDouble()) ?: assignment.maxScore.toDouble()
            val sub = AssignmentSubmission(UUID.randomUUID().toString(), assignmentId, studentUser.id, studentUser.code, studentUser.name, score, max, r?.optString("feedback"), now(), answers, "graded")
            _submissions.value += sub
            saveSubmissions()
            return@withContext Result.success(sub)
        }
        var earned = 0
        var total = 0
        assignment.questions.forEach { q -> total += q.points; if (answers[q.id] == q.correctAnswerIndex) earned += q.points }
        val sub = AssignmentSubmission(UUID.randomUUID().toString(), assignmentId, studentUser.id, studentUser.code, studentUser.name, earned.toDouble(), total.coerceAtLeast(1).toDouble(), "تم التصحيح الآلي للواجب.", now(), answers, "graded")
        _submissions.value += sub
        saveSubmissions()
        Result.success(sub)
    }

    suspend fun submitCodingSolution(taskId: String, studentUser: SessionUser, sourceCode: String, language: String, score: Double, maxScore: Double, passedTests: Int, totalTests: Int, output: String): Result<CodingSubmission> = withContext(Dispatchers.IO) {
        val task = _codingTasks.value.find { it.id == taskId } ?: return@withContext Result.failure(Exception("التمرين البرمجي غير موجود"))
        val dbLanguage = if (language == "html_css") "html" else language
        val status = when { totalTests == 0 -> "not_tested"; passedTests == totalTests -> "passed"; else -> "failed" }
        val rpc = supabase.callRpc("record_coding_submission_v2", JSONObject().apply { put("p_student_id", studentUser.id); put("p_task_id", taskId); put("p_language", dbLanguage); put("p_source_code", sourceCode); put("p_test_status", status); put("p_test_output", output); put("p_score", score); put("p_passed_tests", passedTests); put("p_total_tests", totalTests) })
        if (rpc.isFailure) return@withContext Result.failure(rpc.exceptionOrNull() ?: Exception("تعذر حفظ تسليم البرمجة"))
        val r = rpc.getOrNull()?.optJSONObject(0)
        val sub = CodingSubmission(r?.optString("id").orEmpty().ifBlank { UUID.randomUUID().toString() }, taskId, task.title, studentUser.id, studentUser.code, sourceCode, language, r?.optDouble("score", score) ?: score, r?.optDouble("max_score", maxScore) ?: maxScore, r?.optInt("passed_tests", passedTests) ?: passedTests, r?.optInt("total_tests", totalTests) ?: totalTests, output, now(), "Submitted")
        _codingSubmissions.value = _codingSubmissions.value.filterNot { it.taskId == taskId && it.studentCode == studentUser.code } + sub
        saveCodingSubmissions()
        Result.success(sub)
    }

    suspend fun createAssignment(title: String, description: String, subjectName: String, maxScore: Int, questions: List<Question>): Result<Assignment> = withContext(Dispatchers.IO) {
        val org = supabase.queryTable("organizations", "select=id&limit=1").getOrNull()?.optJSONObject(0) ?: return@withContext Result.failure(Exception("لم يتم العثور على المؤسسة التعليمية"))
        val inserted = supabase.insertRow("assignments", JSONObject().apply { put("organization_id", org.optString("id")); put("title", title); put("description", description); put("max_score", maxScore) })
        if (inserted.isFailure) return@withContext Result.failure(inserted.exceptionOrNull() ?: Exception("تعذر إنشاء الواجب"))
        val id = inserted.getOrNull()?.optString("id") ?: return@withContext Result.failure(Exception("تعذر الحصول على رقم الواجب"))
        questions.forEachIndexed { index, q -> supabase.insertRow("assignment_questions", JSONObject().apply { put("assignment_id", id); put("question_type", if (q.type == QuestionType.TRUE_FALSE) "true_false" else "mcq"); put("prompt", q.text); put("options", JSONArray(q.options)); put("correct_index", q.correctAnswerIndex); put("points", q.points); put("sort_order", index + 1) }) }
        val a = Assignment(id, title, description, subjectName = subjectName, teacherName = currentUser.value?.name, maxScore = maxScore, questions = questions)
        _assignments.value += a
        Result.success(a)
    }

    private fun loadFallbackData() {
        _codingTasks.value = listOf(
            CodingTask("code-task-01", "طباعة الرقم 10 في JavaScript", "اكتب JavaScript يطبع الرقم 10.", ProgrammingLanguage.JAVASCRIPT, "console.log(10);", "10", 10, listOf(TestCase("local-js", "", "10", false, 10))),
            CodingTask("code-task-02", "مربع العدد في Python", "احسب مربع 5 واطبع 25.", ProgrammingLanguage.PYTHON, "x = 5\nprint(x * x)", "25", 10, listOf(TestCase("local-py", "", "25", false, 10))),
            CodingTask("code-task-03", "عنوان ترحيبي HTML/CSS", "أنشئ H1 بالنص المطلوب.", ProgrammingLanguage.HTML_CSS, "<h1>مرحباً بك في الوسام</h1>", "مرحباً بك في الوسام", 10, listOf(TestCase("local-html", "", "مرحباً بك في الوسام", false, 10)))
        )
    }

    private fun displayName(row: JSONObject, fallback: String): String {
        val direct = listOf("full_name", "name", "display_name").asSequence().map { row.optString(it).trim() }.firstOrNull { it.isNotBlank() }
        if (!direct.isNullOrBlank()) return direct
        return listOf("first_name", "middle_name", "last_name").map { row.optString(it).trim() }.filter { it.isNotBlank() }.joinToString(" ").ifBlank { fallback }
    }

    private fun now(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

    private fun saveSubmissions() {
        val arr = JSONArray()
        _submissions.value.forEach { s -> arr.put(JSONObject().apply { put("id", s.id); put("assignmentId", s.assignmentId); put("studentId", s.studentId); put("studentCode", s.studentCode); put("studentName", s.studentName); put("score", s.score); put("maxScore", s.maxScore); put("feedback", s.feedback); put("submittedAt", s.submittedAt); put("status", s.status) }) }
        prefs.edit().putString("saved_submissions_json", arr.toString()).apply()
    }

    private fun saveCodingSubmissions() {
        val arr = JSONArray()
        _codingSubmissions.value.forEach { s -> arr.put(JSONObject().apply { put("id", s.id); put("taskId", s.taskId); put("taskTitle", s.taskTitle); put("studentId", s.studentId); put("studentCode", s.studentCode); put("sourceCode", s.sourceCode); put("language", s.language); put("score", s.score); put("maxScore", s.maxScore); put("passedTests", s.passedTests); put("totalTests", s.totalTests); put("output", s.output); put("submittedAt", s.submittedAt); put("status", s.status) }) }
        prefs.edit().putString("saved_coding_subs_json", arr.toString()).apply()
    }

    private fun parseSubmissions(raw: String?): List<AssignmentSubmission> {
        if (raw.isNullOrBlank()) return emptyList()
        val out = mutableListOf<AssignmentSubmission>()
        runCatching { val a = JSONArray(raw); for (i in 0 until a.length()) { val o = a.getJSONObject(i); out += AssignmentSubmission(o.getString("id"), o.getString("assignmentId"), o.getString("studentId"), o.getString("studentCode"), o.optString("studentName", "طالب"), o.getDouble("score"), o.getDouble("maxScore"), o.optString("feedback", null), o.getString("submittedAt"), emptyMap(), o.optString("status", "graded")) } }
        return out
    }

    private fun parseCodingSubmissions(raw: String?): List<CodingSubmission> {
        if (raw.isNullOrBlank()) return emptyList()
        val out = mutableListOf<CodingSubmission>()
        runCatching { val a = JSONArray(raw); for (i in 0 until a.length()) { val o = a.getJSONObject(i); out += CodingSubmission(o.getString("id"), o.getString("taskId"), o.getString("taskTitle"), o.getString("studentId"), o.getString("studentCode"), o.getString("sourceCode"), o.getString("language"), o.getDouble("score"), o.getDouble("maxScore"), o.optInt("passedTests", 0), o.optInt("totalTests", 0), o.optString("output", ""), o.getString("submittedAt"), o.optString("status", "Submitted")) } }
        return out
    }
}