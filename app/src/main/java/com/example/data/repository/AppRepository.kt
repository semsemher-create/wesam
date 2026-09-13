package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
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
import java.util.*

class AppRepository(private val context: Context) {

    private val supabaseService = SupabaseService()
    private val prefs: SharedPreferences =
        context.getSharedPreferences("alwissam_prefs", Context.MODE_PRIVATE)

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
        loadInitialData()
    }

    private fun restoreSession() {
        val id = prefs.getString("user_id", null)
        val code = prefs.getString("user_code", null)
        val name = prefs.getString("user_name", null)
        val roleStr = prefs.getString("user_role", null)
        val linkedCodesStr = prefs.getString("user_linked_codes", "")

        if (id != null && code != null && name != null && roleStr != null) {
            try {
                val role = UserRole.valueOf(roleStr)
                val linkedCodes = linkedCodesStr?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                _currentUser.value = SessionUser(
                    id = id,
                    code = code,
                    name = name,
                    role = role,
                    linkedStudentCodes = linkedCodes
                )
            } catch (e: Exception) {
                Log.e("AppRepository", "Error restoring session", e)
            }
        }
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

    suspend fun loginWithCode(codeRaw: String): Result<SessionUser> = withContext(Dispatchers.IO) {
        val code = codeRaw.trim().uppercase()
        if (code.isEmpty()) {
            return@withContext Result.failure(Exception("يرجى إدخال كود المستخدم"))
        }

        // 1. Check if Admin
        if (code == "ADMIN" || code.startsWith("ADM")) {
            val adminUser = SessionUser(
                id = "admin-1",
                code = "ADMIN01",
                name = "الأستاذ المشرف (إدارة المنصة)",
                role = UserRole.ADMIN,
                email = "admin@alwissam.edu"
            )
            saveSession(adminUser)
            return@withContext Result.success(adminUser)
        }

        // 2. Check Student (STUxxx)
        if (code.startsWith("STU")) {
            // First check live Supabase students table
            var studentName = when (code) {
                "STU003" -> "أحمد خالد المنصوري"
                "STU004" -> "عمر سامي الزهراني"
                "STU005" -> "سارة عبد الله القحطاني"
                "STU006" -> "يوسف محمد الشهري"
                else -> "طالب $code"
            }
            var studentId = "student-$code"

            val sbResult = supabaseService.queryTable("students", "student_code=eq.$code")
            if (sbResult.isSuccess) {
                val jsonArr = sbResult.getOrNull()
                if (jsonArr != null && jsonArr.length() > 0) {
                    val row = jsonArr.getJSONObject(0)
                    studentId = row.optString("id", studentId)
                    val fn = row.optString("first_name", "")
                    val ln = row.optString("last_name", "")
                    if (fn.isNotBlank() || ln.isNotBlank()) {
                        studentName = "$fn $ln".trim()
                    }
                }
            }

            val studentUser = SessionUser(
                id = studentId,
                code = code,
                name = studentName,
                role = UserRole.STUDENT,
                className = "الصف الأول الثانوي - أ"
            )
            saveSession(studentUser)
            return@withContext Result.success(studentUser)
        }

        // 3. Check Teacher (TCHxxx)
        if (code.startsWith("TCH")) {
            val teacherName = when (code) {
                "TCH001" -> "د. عادل عبد الرحمن (حاسب وبرمجة)"
                "TCH002" -> "أ. طارق عبد العزيز (علوم ورياضيات)"
                else -> "الأستاذ المعلم ($code)"
            }
            val teacherUser = SessionUser(
                id = "teacher-$code",
                code = code,
                name = teacherName,
                role = UserRole.TEACHER,
                className = "قسم البرمجيات والتقنية"
            )
            saveSession(teacherUser)
            return@withContext Result.success(teacherUser)
        }

        // 4. Check Parent (PARxxx)
        if (code.startsWith("PAR")) {
            // Associated children, as specified in requirement 9: PAR001 -> STU004, STU005
            val linkedStudents = when (code) {
                "PAR001" -> listOf("STU004", "STU005")
                "PAR002" -> listOf("STU003")
                else -> listOf("STU004")
            }
            val parentName = when (code) {
                "PAR001" -> "سامي عبد الله الزهراني"
                "PAR002" -> "خالد المنصوري"
                else -> "ولي الأمر ($code)"
            }

            val parentUser = SessionUser(
                id = "parent-$code",
                code = code,
                name = parentName,
                role = UserRole.PARENT,
                linkedStudentCodes = linkedStudents
            )
            saveSession(parentUser)
            return@withContext Result.success(parentUser)
        }

        Result.failure(Exception("الكود المدخل غير معروف. يرجى استخدام STUxxx للطلاب، TCHxxx للمعلمين، PARxxx لأولياء الأمور."))
    }

    private fun loadInitialData() {
        // Prepare standard curriculum assignments
        val defaultAssignments = listOf(
            Assignment(
                id = "asg-01",
                title = "واجب الرياضيات: العمليات الحسابية والمنطق",
                description = "حل الأسئلة التالية المتعلقة بالعمليات الحسابية الأساسية والمنطق الرياضي.",
                subjectName = "الرياضيات",
                teacherName = "أ. طارق عبد العزيز",
                dueAt = "2026-09-20T23:59:00",
                maxScore = 10,
                questions = listOf(
                    Question(
                        id = "q1",
                        text = "ما ناتج حاصل جمع 5 + 3؟",
                        type = QuestionType.MCQ,
                        options = listOf("6", "7", "8", "9"),
                        correctAnswerIndex = 2,
                        points = 2
                    ),
                    Question(
                        id = "q2",
                        text = "العدد 17 هو عدد أولي لا يقبل القسمة إلا على نفسه وعلى الواحد الصحيح.",
                        type = QuestionType.TRUE_FALSE,
                        options = listOf("صح", "خطأ"),
                        correctAnswerIndex = 0,
                        points = 2
                    ),
                    Question(
                        id = "q3",
                        text = "ما هو ناتج ضرب 6 في 7؟",
                        type = QuestionType.MCQ,
                        options = listOf("40", "42", "48", "36"),
                        correctAnswerIndex = 1,
                        points = 2
                    ),
                    Question(
                        id = "q4",
                        text = "مجموع زوايا المثلث الداخلية يساوي 360 درجة.",
                        type = QuestionType.TRUE_FALSE,
                        options = listOf("صح", "خطأ"),
                        correctAnswerIndex = 1,
                        points = 2
                    ),
                    Question(
                        id = "q5",
                        text = "إذا كان س + 4 = 10، فما هي قيمة س؟",
                        type = QuestionType.MCQ,
                        options = listOf("4", "5", "6", "14"),
                        correctAnswerIndex = 2,
                        points = 2
                    )
                )
            ),
            Assignment(
                id = "asg-02",
                title = "واجب الحاسب الآلي: مدخل إلى أساسيات الخوارزميات",
                description = "اختبار قصير في مفاهيم الخوارزميات والمنطق البرمجي.",
                subjectName = "الحاسب والبرمجة",
                teacherName = "د. عادل عبد الرحمن",
                dueAt = "2026-09-25T23:59:00",
                maxScore = 10,
                questions = listOf(
                    Question(
                        id = "q21",
                        text = "أي مما يلي يُعد لغة برمجة تُستخدم بشكل رئيسي لتطوير صفحات الويب التفاعلية؟",
                        type = QuestionType.MCQ,
                        options = listOf("HTML", "CSS", "JavaScript", "SQL"),
                        correctAnswerIndex = 2,
                        points = 3
                    ),
                    Question(
                        id = "q22",
                        text = "الخوارزمية هي مجموعة من الخطوات الرياضية والمنطقية المتسلسلة لحل مشكلة ما.",
                        type = QuestionType.TRUE_FALSE,
                        options = listOf("صح", "خطأ"),
                        correctAnswerIndex = 0,
                        points = 3
                    ),
                    Question(
                        id = "q23",
                        text = "في البرمجة، المتغير (Variable) هو مساحة مخصصة في الذاكرة لتخزين قيمة قابلة للتغيير.",
                        type = QuestionType.TRUE_FALSE,
                        options = listOf("صح", "خطأ"),
                        correctAnswerIndex = 0,
                        points = 4
                    )
                )
            ),
            Assignment(
                id = "asg-03",
                title = "تحدي البرمجة: طباعة الأرقام والعمليات في JavaScript",
                description = "اكتب برنامج JavaScript يطبع الرقم 10 في شاشة الكونسول.",
                subjectName = "معمل البرمجة",
                teacherName = "د. عادل عبد الرحمن",
                dueAt = "2026-09-30T23:59:00",
                maxScore = 10,
                isCoding = true,
                codingTaskId = "code-task-01"
            )
        )

        // Load saved submissions from prefs
        val savedSubmissionsJson = prefs.getString("saved_submissions_json", "[]")
        val savedSubmissions = parseSubmissions(savedSubmissionsJson)

        _assignments.value = defaultAssignments
        _submissions.value = savedSubmissions

        // Prepare Coding Tasks
        val defaultCodingTasks = listOf(
            CodingTask(
                id = "code-task-01",
                title = "طباعة الرقم 10 في JavaScript",
                description = "اكتب برنامج JavaScript يقوم بطباعة الرقم 10 بدقة في المخرجات.",
                language = ProgrammingLanguage.JAVASCRIPT,
                starterCode = "// اكتب كود JavaScript هنا\nconsole.log(10);",
                expectedOutput = "10",
                maxScore = 10,
                testCases = listOf(
                    TestCase("tc-js-1", "", "10", isHidden = false, points = 5),
                    TestCase("tc-js-2", "", "10", isHidden = true, points = 5)
                ),
                relatedAssignmentId = "asg-03"
            ),
            CodingTask(
                id = "code-task-02",
                title = "مربع العدد في Python",
                description = "اكتب كود Python يحسب مربع العدد 5 (أي 5 * 5) ويطبع النتيجة 25.",
                language = ProgrammingLanguage.PYTHON,
                starterCode = "# برنامج بايثون لحساب مربع العدد 5\nx = 5\nprint(x * x)",
                expectedOutput = "25",
                maxScore = 10,
                testCases = listOf(
                    TestCase("tc-py-1", "5", "25", isHidden = false, points = 5),
                    TestCase("tc-py-2", "10", "100", isHidden = true, points = 5)
                )
            ),
            CodingTask(
                id = "code-task-03",
                title = "تصميم عنوان ترحيبي في HTML/CSS",
                description = "قم بكتابة عنوان H1 بالنص 'مرحباً بك في الوسام' مع تنسيق حجم الخط ليصبح 30px ولون أزرق داكن.",
                language = ProgrammingLanguage.HTML_CSS,
                starterCode = "<!-- HTML -->\n<h1 class=\"title\">مرحباً بك في الوسام</h1>\n\n<style>\n.title {\n  font-size: 30px;\n  color: #1E3A8A;\n  text-align: center;\n}\n</style>",
                expectedOutput = "مرحباً بك في الوسام",
                maxScore = 10,
                testCases = listOf(
                    TestCase("tc-html-1", "", "مرحباً بك في الوسام", isHidden = false, points = 10)
                )
            )
        )

        val savedCodingSubsJson = prefs.getString("saved_coding_subs_json", "[]")
        val savedCodingSubs = parseCodingSubmissions(savedCodingSubsJson)

        _codingTasks.value = defaultCodingTasks
        _codingSubmissions.value = savedCodingSubs
    }

    suspend fun submitAssignmentAnswers(
        assignmentId: String,
        studentUser: SessionUser,
        answers: Map<String, Int>
    ): Result<AssignmentSubmission> = withContext(Dispatchers.IO) {
        val assignment = _assignments.value.find { it.id == assignmentId }
            ?: return@withContext Result.failure(Exception("الواجب غير موجود"))

        // Check if already submitted
        val existing = _submissions.value.find {
            it.assignmentId == assignmentId && it.studentCode == studentUser.code
        }
        if (existing != null) {
            return@withContext Result.failure(Exception("لقد قمت بتسليم هذا الواجب مسبقاً! درجتك: ${existing.score}/${existing.maxScore}"))
        }

        // Automatic Grading
        var earnedPoints = 0.0
        var totalPoints = 0.0
        for (q in assignment.questions) {
            totalPoints += q.points
            val studentAns = answers[q.id]
            if (studentAns != null && studentAns == q.correctAnswerIndex) {
                earnedPoints += q.points
            }
        }

        val finalScore = if (totalPoints > 0) earnedPoints else 10.0
        val finalMaxScore = if (totalPoints > 0) totalPoints else 10.0

        val nowFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val submission = AssignmentSubmission(
            id = UUID.randomUUID().toString(),
            assignmentId = assignmentId,
            studentId = studentUser.id,
            studentCode = studentUser.code,
            studentName = studentUser.name,
            score = finalScore,
            maxScore = finalMaxScore,
            feedback = if (finalScore == finalMaxScore) "ممتاز! إجابات نموذجية متكاملة." else "تم التصحيح الآلي للواجب بنجاح.",
            submittedAt = nowFormatted,
            answers = answers,
            status = "graded"
        )

        // Save to Supabase assignment_submissions table
        try {
            val jsonObject = JSONObject().apply {
                put("assignment_id", assignmentId)
                put("student_id", studentUser.id)
                put("score", finalScore)
                put("feedback", submission.feedback)
                put("submitted_at", nowFormatted)
            }
            supabaseService.insertRow("assignment_submissions", jsonObject)
        } catch (e: Exception) {
            Log.w("AppRepository", "Failed to sync submission to Supabase directly", e)
        }

        // Persist locally in SharedPreferences & StateFlow
        val updatedList = _submissions.value + submission
        _submissions.value = updatedList
        saveSubmissionsToPrefs(updatedList)

        Result.success(submission)
    }

    suspend fun submitCodingSolution(
        taskId: String,
        studentUser: SessionUser,
        sourceCode: String,
        language: String,
        score: Double,
        maxScore: Double,
        passedTests: Int,
        totalTests: Int,
        output: String
    ): Result<CodingSubmission> = withContext(Dispatchers.IO) {
        val task = _codingTasks.value.find { it.id == taskId }
            ?: return@withContext Result.failure(Exception("التمرين البرمجي غير موجود"))

        val nowFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val submission = CodingSubmission(
            id = UUID.randomUUID().toString(),
            taskId = taskId,
            taskTitle = task.title,
            studentId = studentUser.id,
            studentCode = studentUser.code,
            sourceCode = sourceCode,
            language = language,
            score = score,
            maxScore = maxScore,
            passedTests = passedTests,
            totalTests = totalTests,
            output = output,
            submittedAt = nowFormatted,
            status = "Submitted"
        )

        // Save to Supabase coding_submissions table
        try {
            val jsonObject = JSONObject().apply {
                put("task_id", taskId)
                put("student_id", studentUser.id)
                put("source_code", sourceCode)
                put("language", language)
                put("score", score)
                put("submitted_at", nowFormatted)
            }
            supabaseService.insertRow("coding_submissions", jsonObject)
        } catch (e: Exception) {
            Log.w("AppRepository", "Failed to sync coding submission to Supabase directly", e)
        }

        // If related to an assignment, also update assignment submission!
        if (task.relatedAssignmentId != null) {
            val asgSubmission = AssignmentSubmission(
                id = UUID.randomUUID().toString(),
                assignmentId = task.relatedAssignmentId,
                studentId = studentUser.id,
                studentCode = studentUser.code,
                studentName = studentUser.name,
                score = score,
                maxScore = maxScore,
                feedback = "تم تصحيح تمرين البرمجة آلياً: $passedTests/$totalTests اختبارات ناجحة",
                submittedAt = nowFormatted,
                status = "graded"
            )
            val updatedAsgSubs = _submissions.value.filter {
                !(it.assignmentId == task.relatedAssignmentId && it.studentCode == studentUser.code)
            } + asgSubmission
            _submissions.value = updatedAsgSubs
            saveSubmissionsToPrefs(updatedAsgSubs)
        }

        val updatedCodingList = _codingSubmissions.value.filter {
            !(it.taskId == taskId && it.studentCode == studentUser.code)
        } + submission
        _codingSubmissions.value = updatedCodingList
        saveCodingSubmissionsToPrefs(updatedCodingList)

        Result.success(submission)
    }

    suspend fun createAssignment(
        title: String,
        description: String,
        subjectName: String,
        maxScore: Int,
        questions: List<Question>
    ): Result<Assignment> = withContext(Dispatchers.IO) {
        val newAsg = Assignment(
            id = "asg-" + UUID.randomUUID().toString().take(8),
            title = title,
            description = description,
            subjectName = subjectName,
            teacherName = _currentUser.value?.name ?: "المعلم",
            dueAt = "2026-10-15T23:59:00",
            maxScore = maxScore,
            questions = questions
        )

        // Try to insert into Supabase assignments table
        try {
            val jsonObject = JSONObject().apply {
                put("title", title)
                put("description", description)
                put("max_score", maxScore)
            }
            supabaseService.insertRow("assignments", jsonObject)
        } catch (e: Exception) {
            Log.w("AppRepository", "Supabase insert assignment warning", e)
        }

        val updated = _assignments.value + newAsg
        _assignments.value = updated
        Result.success(newAsg)
    }

    private fun saveSubmissionsToPrefs(list: List<AssignmentSubmission>) {
        val arr = JSONArray()
        for (sub in list) {
            val obj = JSONObject().apply {
                put("id", sub.id)
                put("assignmentId", sub.assignmentId)
                put("studentId", sub.studentId)
                put("studentCode", sub.studentCode)
                put("studentName", sub.studentName)
                put("score", sub.score)
                put("maxScore", sub.maxScore)
                put("feedback", sub.feedback)
                put("submittedAt", sub.submittedAt)
                put("status", sub.status)
            }
            arr.put(obj)
        }
        prefs.edit().putString("saved_submissions_json", arr.toString()).apply()
    }

    private fun parseSubmissions(jsonStr: String?): List<AssignmentSubmission> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        val list = mutableListOf<AssignmentSubmission>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    AssignmentSubmission(
                        id = obj.getString("id"),
                        assignmentId = obj.getString("assignmentId"),
                        studentId = obj.getString("studentId"),
                        studentCode = obj.getString("studentCode"),
                        studentName = obj.optString("studentName", "طالب"),
                        score = obj.getDouble("score"),
                        maxScore = obj.getDouble("maxScore"),
                        feedback = if (obj.has("feedback") && !obj.isNull("feedback")) obj.getString("feedback") else null,
                        submittedAt = obj.getString("submittedAt"),
                        status = obj.optString("status", "graded")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("AppRepository", "Error parsing submissions", e)
        }
        return list
    }

    private fun saveCodingSubmissionsToPrefs(list: List<CodingSubmission>) {
        val arr = JSONArray()
        for (sub in list) {
            val obj = JSONObject().apply {
                put("id", sub.id)
                put("taskId", sub.taskId)
                put("taskTitle", sub.taskTitle)
                put("studentId", sub.studentId)
                put("studentCode", sub.studentCode)
                put("sourceCode", sub.sourceCode)
                put("language", sub.language)
                put("score", sub.score)
                put("maxScore", sub.maxScore)
                put("passedTests", sub.passedTests)
                put("totalTests", sub.totalTests)
                put("output", sub.output)
                put("submittedAt", sub.submittedAt)
                put("status", sub.status)
            }
            arr.put(obj)
        }
        prefs.edit().putString("saved_coding_subs_json", arr.toString()).apply()
    }

    private fun parseCodingSubmissions(jsonStr: String?): List<CodingSubmission> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        val list = mutableListOf<CodingSubmission>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    CodingSubmission(
                        id = obj.getString("id"),
                        taskId = obj.getString("taskId"),
                        taskTitle = obj.getString("taskTitle"),
                        studentId = obj.getString("studentId"),
                        studentCode = obj.getString("studentCode"),
                        sourceCode = obj.getString("sourceCode"),
                        language = obj.getString("language"),
                        score = obj.getDouble("score"),
                        maxScore = obj.getDouble("maxScore"),
                        passedTests = obj.getInt("passedTests"),
                        totalTests = obj.getInt("totalTests"),
                        output = obj.optString("output", ""),
                        submittedAt = obj.getString("submittedAt"),
                        status = obj.optString("status", "Submitted")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("AppRepository", "Error parsing coding submissions", e)
        }
        return list
    }
}
