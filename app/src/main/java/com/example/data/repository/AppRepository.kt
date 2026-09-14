package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.supabase.SupabaseService
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        loadLocalFallbacks()
        _submissions.value = parseSubmissions(prefs.getString("saved_submissions_json", "[]"))
        _codingSubmissions.value = parseCodingSubmissions(prefs.getString("saved_coding_subs_json", "[]"))
    }

    private fun restoreSession() {
        val id = prefs.getString("user_id", null) ?: return
        val code = prefs.getString("user_code", null) ?: return
        val name = prefs.getString("user_name", null) ?: return
        val role = prefs.getString("user_role", null) ?: return
        val linked = prefs.getString("user_linked_codes", "") ?: ""
        runCatching {
            _currentUser.value = SessionUser(
                id = id, code = code, name = name, role = UserRole.valueOf(role),
                linkedStudentCodes = linked.split(",").filter { it.isNotBlank() }
            )
        }.onFailure { Log.w("AppRepository", "Could not restore session", it) }
    }

    private fun saveSession(user: SessionUser) {
        prefs.edit()
            .putString("user_id", user.id)
            .putString("user_code", user.code)
            .putString("user_name", user.name)
            .putString("user_role", user.role.name)
            .putString("user_linked_codes", user.linkedStudentCodes.joinToString(","))
            .apply()
        _currentUser.value = user
    }

    fun logout() {
        prefs.edit().clear().apply()
        _currentUser.value = null
    }

    suspend fun loginWithCode(raw: String): Result<SessionUser> = withContext(Dispatchers.IO) {
        val code = raw.trim().uppercase()
        if (code.isBlank()) return@withContext Result.failure(Exception("يرجى إدخال كود المستخدم"))

        if (code == "ADMIN" || code.startsWith("ADM")) {
            val user = SessionUser("admin-1", "ADMIN01", "الأستاذ المشرف (إدارة المنصة)", UserRole.ADMIN, email = "admin@alwissam.edu")
            saveSession(user)
            return@withContext Result.success(user)
        }

        if (code.startsWith("STU")) {
            val result = supabase.queryTable("students", "select=*&student_code=eq.$code&limit=1")
            if (result.isSuccess) {
                val rows = result.getOrNull() ?: JSONArray()
                if (rows.length() > 0) {
                    val row = rows.getJSONObject(0)
                    val id = row.optString("id")
                    val name = listOf(row.optString("first_name"), row.optString("last_name"))
                        .filter { it.isNotBlank() }.joinToString(" ").ifBlank { "طالب $code" }
                    val user = SessionUser(id, code, name, UserRole.STUDENT)
                    saveSession(user)
                    return@withContext Result.success(user)
                }
            }
            return@withContext Result.failure(Exception("كود الطالب غير موجود في قاعدة بيانات الوسام"))
        }

        // Keep the currently working teacher/parent code flow intact until their code records are migrated to Auth.
        if (code.startsWith("TCH")) {
            val user = SessionUser("teacher-$code", code, when (code) {
                "TCH001" -> "د. عادل عبد الرحمن (حاسب وبرمجة)"
                "TCH002" -> "أ. طارق عبد العزيز (علوم ورياضيات)"
                else -> "الأستاذ المعلم ($code)"
            }, UserRole.TEACHER, className = "قسم البرمجيات والتقنية")
            saveSession(user)
            return@withContext Result.success(user)
        }

        if (code.startsWith("PAR")) {
            val linked = when (code) {
                "PAR001" -> listOf("STU004", "STU005")
                "PAR002" -> listOf("STU003")
                else -> listOf("STU004")
            }
            val user = SessionUser("parent-$code", code, when (code) {
                "PAR001" -> "سامي عبد الله الزهراني"
                "PAR002" -> "خالد المنصوري"
                else -> "ولي الأمر ($code)"
            }, UserRole.PARENT, linkedStudentCodes = linked)
            saveSession(user)
            return@withContext Result.success(user)
        }

        Result.failure(Exception("الكود المدخل غير معروف"))
    }

    suspend fun refreshRemoteData() = withContext(Dispatchers.IO) {
        refreshAssignments()
        refreshCodingTasks()
    }

    private suspend fun refreshAssignments() {
        val rowsResult = supabase.queryTable("assignments", "select=*&order=created_at.asc")
        if (rowsResult.isFailure) return
        val rows = rowsResult.getOrNull() ?: return
        if (rows.length() == 0) return

        val questionsResult = supabase.queryTable("assignment_questions", "select=*&order=sort_order.asc")
        val questionRows = questionsResult.getOrNull() ?: JSONArray()
        val subjects = supabase.queryTable("subjects", "select=id,name").getOrNull() ?: JSONArray()
        val classes = supabase.queryTable("classes", "select=id,name").getOrNull() ?: JSONArray()
        val profiles = supabase.queryTable("profiles", "select=id,full_name").getOrNull() ?: JSONArray()

        val subjectNames = mutableMapOf<String, String>()
        for (i in 0 until subjects.length()) {
            val r = subjects.getJSONObject(i); subjectNames[r.optString("id")] = r.optString("name")
        }
        val classNames = mutableMapOf<String, String>()
        for (i in 0 until classes.length()) {
            val r = classes.getJSONObject(i); classNames[r.optString("id")] = r.optString("name")
        }
        val teacherNames = mutableMapOf<String, String>()
        for (i in 0 until profiles.length()) {
            val r = profiles.getJSONObject(i); teacherNames[r.optString("id")] = r.optString("full_name")
        }

        val loaded = mutableListOf<Assignment>()
        for (i in 0 until rows.length()) {
            val r = rows.getJSONObject(i)
            val id = r.optString("id")
            val qs = mutableListOf<Question>()
            for (j in 0 until questionRows.length()) {
                val q = questionRows.getJSONObject(j)
                if (q.optString("assignment_id") != id) continue
                val optionsJson = q.optJSONArray("options") ?: JSONArray()
                val options = List(optionsJson.length()) { k -> optionsJson.optString(k) }
                val type = if (q.optString("question_type") == "true_false") QuestionType.TRUE_FALSE else QuestionType.MCQ
                qs += Question(q.optString("id"), q.optString("prompt"), type, options, q.optInt("correct_index", -1), q.optInt("points", 1))
            }
            loaded += Assignment(
                id = id,
                title = r.optString("title"),
                description = r.optString("description", ""),
                classId = r.optString("class_id").ifBlank { null },
                className = classNames[r.optString("class_id")],
                subjectId = r.optString("subject_id").ifBlank { null },
                subjectName = subjectNames[r.optString("subject_id")],
                teacherName = teacherNames[r.optString("teacher_user_id")],
                dueAt = r.optString("due_at").ifBlank { null },
                maxScore = r.optDouble("max_score", 10.0).toInt(),
                questions = qs
            )
        }
        if (loaded.isNotEmpty()) _assignments.value = loaded
    }

    private suspend fun refreshCodingTasks() {
        val taskRows = supabase.queryTable("coding_tasks", "select=*&is_active=eq.true&order=created_at.asc").getOrNull() ?: return
        if (taskRows.length() == 0) return
        val testRows = supabase.queryTable("coding_test_cases", "select=*&order=sort_order.asc").getOrNull() ?: JSONArray()
        val loaded = mutableListOf<CodingTask>()
        for (i in 0 until taskRows.length()) {
            val r = taskRows.getJSONObject(i)
            val id = r.optString("id")
            val language = when (r.optString("language")) {
                "javascript" -> ProgrammingLanguage.JAVASCRIPT
                "python" -> ProgrammingLanguage.PYTHON
                else -> ProgrammingLanguage.HTML_CSS
            }
            val tests = mutableListOf<TestCase>()
            for (j in 0 until testRows.length()) {
                val t = testRows.getJSONObject(j)
                if (t.optString("task_id") == id) {
                    tests += TestCase(t.optString("id"), t.optString("input", ""), t.optString("expected_output"), t.optBoolean("is_hidden", true), t.optInt("points", 1))
                }
            }
            loaded += CodingTask(
                id = id,
                title = r.optString("title"),
                description = r.optString("description", ""),
                language = language,
                starterCode = r.optString("starter_code", ""),
                expectedOutput = r.optString("expected_output", ""),
                maxScore = r.optDouble("max_score", 10.0).toInt(),
                testCases = tests
            )
        }
        if (loaded.isNotEmpty()) _codingTasks.value = loaded
    }

    suspend fun submitAssignmentAnswers(assignmentId: String, studentUser: SessionUser, answers: Map<String, Int>): Result<AssignmentSubmission> = withContext(Dispatchers.IO) {
        val assignment = _assignments.value.find { it.id == assignmentId }
            ?: return@withContext Result.failure(Exception("الواجب غير موجود"))
        val existing = _submissions.value.find { it.assignmentId == assignmentId && it.studentCode == studentUser.code }
        if (existing != null) return@withContext Result.failure(Exception("لقد تم تسليم هذا الواجب من قبل. الدرجة: ${existing.score}/${existing.maxScore}"))

        val now = now()
        val answerJson = JSONObject().apply { answers.forEach { (k, v) -> put(k, v) } }
        val remoteId = runCatching { UUID.fromString(assignmentId) }.getOrNull()
        if (remoteId != null) {
            val rpc = supabase.callRpc("submit_assignment_auto", JSONObject().apply {
                put("p_assignment_id", assignmentId)
                put("p_student_id", studentUser.id)
                put("p_answers", answerJson)
            })
            if (rpc.isFailure) return@withContext Result.failure(rpc.exceptionOrNull() ?: Exception("تعذر حفظ الواجب"))
            val result = rpc.getOrNull()?.optJSONObject(0)
            val score = result?.optDouble("score", 0.0) ?: 0.0
            val max = result?.optDouble("max_score", assignment.maxScore.toDouble()) ?: assignment.maxScore.toDouble()
            val submission = AssignmentSubmission(UUID.randomUUID().toString(), assignmentId, studentUser.id, studentUser.code, studentUser.name, score, max, result?.optString("feedback"), now, answers, "graded")
            _submissions.value += submission
            saveSubmissions()
            return@withContext Result.success(submission)
        }

        var earned = 0
        var total = 0
        assignment.questions.forEach { q -> total += q.points; if (answers[q.id] == q.correctAnswerIndex) earned += q.points }
        val submission = AssignmentSubmission(UUID.randomUUID().toString(), assignmentId, studentUser.id, studentUser.code, studentUser.name, earned.toDouble(), total.toDouble().coerceAtLeast(1.0), "تم التصحيح الآلي للواجب.", now, answers, "graded")
        _submissions.value += submission
        saveSubmissions()
        Result.success(submission)
    }

    suspend fun submitCodingSolution(taskId: String, studentUser: SessionUser, sourceCode: String, language: String, score: Double, maxScore: Double, passedTests: Int, totalTests: Int, output: String): Result<CodingSubmission> = withContext(Dispatchers.IO) {
        val task = _codingTasks.value.find { it.id == taskId } ?: return@withContext Result.failure(Exception("التمرين البرمجي غير موجود"))
        val status = when {
            totalTests == 0 -> "not_tested"
            passedTests == totalTests -> "passed"
            else -> "failed"
        }
        val rpc = runCatching {
            supabase.callRpc("record_coding_submission_v2", JSONObject().apply {
                put("p_student_id", studentUser.id)
                put("p_task_id", taskId)
                put("p_language", language)
                put("p_source_code", sourceCode)
                put("p_test_status", status)
                put("p_test_output", output)
                put("p_score", score)
                put("p_passed_tests", passedTests)
                put("p_total_tests", totalTests)
            })
        }.getOrElse { Result.failure(it) }
        if (rpc.isFailure) return@withContext Result.failure(rpc.exceptionOrNull() ?: Exception("تعذر حفظ تسليم البرمجة"))

        val remote = rpc.getOrNull()?.optJSONObject(0)
        val submission = CodingSubmission(
            id = remote?.optString("id").orEmpty().ifBlank { UUID.randomUUID().toString() },
            taskId = taskId,
            taskTitle = task.title,
            studentId = studentUser.id,
            studentCode = studentUser.code,
            sourceCode = sourceCode,
            language = language,
            score = remote?.optDouble("score", score) ?: score,
            maxScore = remote?.optDouble("max_score", maxScore) ?: maxScore,
            passedTests = remote?.optInt("passed_tests", passedTests) ?: passedTests,
            totalTests = remote?.optInt("total_tests", totalTests) ?: totalTests,
            output = output,
            submittedAt = now(),
            status = "Submitted"
        )
        _codingSubmissions.value = _codingSubmissions.value.filterNot { it.taskId == taskId && it.studentCode == studentUser.code } + submission
        saveCodingSubmissions()
        Result.success(submission)
    }

    suspend fun createAssignment(title: String, description: String, subjectName: String, maxScore: Int, questions: List<Question>): Result<Assignment> = withContext(Dispatchers.IO) {
        val org = supabase.queryTable("organizations", "select=id&limit=1").getOrNull()?.optJSONObject(0)
            ?: return@withContext Result.failure(Exception("لم يتم العثور على المؤسسة التعليمية"))
        val inserted = supabase.insertRow("assignments", JSONObject().apply {
            put("organization_id", org.optString("id"))
            put("title", title)
            put("description", description)
            put("max_score", maxScore)
        })
        if (inserted.isFailure) return@withContext Result.failure(inserted.exceptionOrNull() ?: Exception("تعذر إنشاء الواجب"))
        val id = inserted.getOrNull()?.optString("id") ?: return@withContext Result.failure(Exception("لم يتم الحصول على رقم الواجب"))
        questions.forEachIndexed { index, q ->
            supabase.insertRow("assignment_questions", JSONObject().apply {
                put("assignment_id", id)
                put("question_type", if (q.type == QuestionType.TRUE_FALSE) "true_false" else "mcq")
                put("prompt", q.text)
                put("options", JSONArray(q.options))
                put("correct_index", q.correctAnswerIndex)
                put("points", q.points)
                put("sort_order", index + 1)
            })
        }
        val newAssignment = Assignment(id, title, description, subjectName = subjectName, teacherName = _currentUser.value?.name, maxScore = maxScore, questions = questions)
        _assignments.value += newAssignment
        Result.success(newAssignment)
    }

    private fun loadLocalFallbacks() {
        if (_assignments.value.isEmpty()) {
            _assignments.value = listOf(
                Assignment("asg-local-01", "واجب تجريبي: العمليات الحسابية", "نسخة احتياطية تعمل حتى عند انقطاع الشبكة.", subjectName = "الرياضيات", maxScore = 10, questions = listOf(
                    Question("q1", "ما ناتج 5 + 3؟", QuestionType.MCQ, listOf("6", "7", "8", "9"), 2, 2),
                    Question("q2", "العدد 17 عدد أولي.", QuestionType.TRUE_FALSE, listOf("صح", "خطأ"), 0, 2),
                    Question("q3", "ما ناتج 6 × 7؟", QuestionType.MCQ, listOf("40", "42", "48", "36"), 1, 2),
                    Question("q4", "مجموع زوايا المثلث الداخلية 360 درجة.", QuestionType.TRUE_FALSE, listOf("صح", "خطأ"), 1, 2),
                    Question("q5", "إذا كان س + 4 = 10، فما قيمة س؟", QuestionType.MCQ, listOf("4", "5", "6", "14"), 2, 2)
                )
            )
        }
        if (_codingTasks.value.isEmpty()) {
            _codingTasks.value = listOf(
                CodingTask("code-task-01", "طباعة الرقم 10 في JavaScript", "اكتب برنامج JavaScript يطبع الرقم 10.", ProgrammingLanguage.JAVASCRIPT, "console.log(10);", "10", 10, listOf(TestCase("tc1", "", "10", false, 5), TestCase("tc2", "", "10", true, 5))),
                CodingTask("code-task-02", "مربع العدد في Python", "احسب مربع العدد 5 واطبع 25.", ProgrammingLanguage.PYTHON, "x = 5\nprint(x * x)", "25", 10, listOf(TestCase("tc3", "5", "25", false, 5), TestCase("tc4", "10", "100", true, 5))),
                CodingTask("code-task-03", "عنوان ترحيبي في HTML/CSS", "أنشئ عنوان H1 ترحيبي مع CSS.", ProgrammingLanguage.HTML_CSS, "<h1>مرحباً بك في الوسام</h1>", "مرحباً بك في الوسام", 10, listOf(TestCase("tc5", "", "مرحباً بك في الوسام", false, 10)))
            )
        }
    }

    private fun now(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

    private fun saveSubmissions() {
        val arr = JSONArray()
        _submissions.value.forEach { s -> arr.put(JSONObject().apply {
            put("id", s.id); put("assignmentId", s.assignmentId); put("studentId", s.studentId); put("studentCode", s.studentCode); put("studentName", s.studentName); put("score", s.score); put("maxScore", s.maxScore); put("feedback", s.feedback); put("submittedAt", s.submittedAt); put("status", s.status)
        }) }
        prefs.edit().putString("saved_submissions_json", arr.toString()).apply()
    }

    private fun saveCodingSubmissions() {
        val arr = JSONArray()
        _codingSubmissions.value.forEach { s -> arr.put(JSONObject().apply {
            put("id", s.id); put("taskId", s.taskId); put("taskTitle", s.taskTitle); put("studentId", s.studentId); put("studentCode", s.studentCode); put("sourceCode", s.sourceCode); put("language", s.language); put("score", s.score); put("maxScore", s.maxScore); put("passedTests", s.passedTests); put("totalTests", s.totalTests); put("output", s.output); put("submittedAt", s.submittedAt); put("status", s.status)
        }) }
        prefs.edit().putString("saved_coding_subs_json", arr.toString()).apply()
    }

    private fun parseSubmissions(raw: String?): List<AssignmentSubmission> {
        if (raw.isNullOrBlank()) return emptyList()
        val out = mutableListOf<AssignmentSubmission>()
        runCatching {
            val a = JSONArray(raw)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out += AssignmentSubmission(o.getString("id"), o.getString("assignmentId"), o.getString("studentId"), o.getString("studentCode"), o.optString("studentName", "طالب"), o.getDouble("score"), o.getDouble("maxScore"), o.optString("feedback", null), o.getString("submittedAt"), emptyMap(), o.optString("status", "graded"))
            }
        }
        return out
    }

    private fun parseCodingSubmissions(raw: String?): List<CodingSubmission> {
        if (raw.isNullOrBlank()) return emptyList()
        val out = mutableListOf<CodingSubmission>()
        runCatching {
            val a = JSONArray(raw)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out += CodingSubmission(o.getString("id"), o.getString("taskId"), o.getString("taskTitle"), o.getString("studentId"), o.getString("studentCode"), o.getString("sourceCode"), o.getString("language"), o.getDouble("score"), o.getDouble("maxScore"), o.optInt("passedTests", 0), o.optInt("totalTests", 0), o.optString("output", ""), o.getString("submittedAt"), o.optString("status", "Submitted"))
            }
        }
        return out
    }
}
