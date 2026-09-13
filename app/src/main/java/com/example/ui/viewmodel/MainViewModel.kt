package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AppRepository
import com.example.engine.*
import com.example.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application.applicationContext)
    private val jsEngine = JavaScriptEngine(application.applicationContext)
    private val pyEngine = PythonEngine()
    private val htmlEngine = HtmlCssEngine()

    val currentUser: StateFlow<SessionUser?> = repository.currentUser
    val assignments: StateFlow<List<Assignment>> = repository.assignments
    val submissions: StateFlow<List<AssignmentSubmission>> = repository.submissions
    val codingTasks: StateFlow<List<CodingTask>> = repository.codingTasks
    val codingSubmissions: StateFlow<List<CodingSubmission>> = repository.codingSubmissions

    // Login state
    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Coding Lab active state
    private val _activeCodingTask = MutableStateFlow<CodingTask?>(null)
    val activeCodingTask: StateFlow<CodingTask?> = _activeCodingTask.asStateFlow()

    private val _editorCode = MutableStateFlow("")
    val editorCode: StateFlow<String> = _editorCode.asStateFlow()

    private val _executionResult = MutableStateFlow<ExecutionResult?>(null)
    val executionResult: StateFlow<ExecutionResult?> = _executionResult.asStateFlow()

    private val _isExecutingCode = MutableStateFlow(false)
    val isExecutingCode: StateFlow<Boolean> = _isExecutingCode.asStateFlow()

    // Assignment Solver active state
    private val _activeAssignment = MutableStateFlow<Assignment?>(null)
    val activeAssignment: StateFlow<Assignment?> = _activeAssignment.asStateFlow()

    private val _studentAnswers = MutableStateFlow<Map<String, Int>>(emptyMap())
    val studentAnswers: StateFlow<Map<String, Int>> = _studentAnswers.asStateFlow()

    private val _submissionMessage = MutableStateFlow<String?>(null)
    val submissionMessage: StateFlow<String?> = _submissionMessage.asStateFlow()

    // Parent Selected Child Code
    private val _selectedChildCode = MutableStateFlow("STU004")
    val selectedChildCode: StateFlow<String> = _selectedChildCode.asStateFlow()

    fun selectChild(code: String) {
        _selectedChildCode.value = code
    }

    fun login(code: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _loginError.value = null
            val result = repository.loginWithCode(code)
            _isLoading.value = false
            result.onFailure {
                _loginError.value = it.message
            }
            result.onSuccess { user ->
                if (user.role == UserRole.PARENT && user.linkedStudentCodes.isNotEmpty()) {
                    _selectedChildCode.value = user.linkedStudentCodes.first()
                }
            }
        }
    }

    fun logout() {
        repository.logout()
        _activeAssignment.value = null
        _activeCodingTask.value = null
        _executionResult.value = null
        _studentAnswers.value = emptyMap()
    }

    fun openAssignment(assignment: Assignment) {
        _activeAssignment.value = assignment
        _studentAnswers.value = emptyMap()
        _submissionMessage.value = null
    }

    fun closeAssignment() {
        _activeAssignment.value = null
        _studentAnswers.value = emptyMap()
        _submissionMessage.value = null
    }

    fun answerQuestion(questionId: String, optionIndex: Int) {
        _studentAnswers.value = _studentAnswers.value + (questionId to optionIndex)
    }

    fun submitAssignment(assignment: Assignment) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.submitAssignmentAnswers(
                assignmentId = assignment.id,
                studentUser = user,
                answers = _studentAnswers.value
            )
            _isLoading.value = false
            result.onSuccess {
                _submissionMessage.value = "تم تسليم الواجب وحساب الدرجة بنجاح! درجتك: ${it.score} / ${it.maxScore}"
            }
            result.onFailure {
                _submissionMessage.value = it.message ?: "تعذر تسليم الواجب"
            }
        }
    }

    fun openCodingTask(task: CodingTask) {
        _activeCodingTask.value = task
        // check if user has existing submission
        val user = currentUser.value
        val existingSub = codingSubmissions.value.find {
            it.taskId == task.id && it.studentCode == (user?.code ?: "")
        }
        _editorCode.value = existingSub?.sourceCode ?: task.starterCode
        _executionResult.value = null
    }

    fun closeCodingTask() {
        _activeCodingTask.value = null
        _executionResult.value = null
    }

    fun updateEditorCode(code: String) {
        _editorCode.value = code
    }

    fun runCode() {
        val task = _activeCodingTask.value ?: return
        val code = _editorCode.value
        viewModelScope.launch {
            _isExecutingCode.value = true
            val result = when (task.language) {
                ProgrammingLanguage.JAVASCRIPT -> jsEngine.execute(code, task.expectedOutput, emptyList(), task.maxScore)
                ProgrammingLanguage.PYTHON -> pyEngine.execute(code, task.expectedOutput, emptyList(), task.maxScore)
                ProgrammingLanguage.HTML_CSS -> htmlEngine.evaluate(code, task.expectedOutput, emptyList(), task.maxScore)
            }
            _isExecutingCode.value = false
            _executionResult.value = result
        }
    }

    fun checkCodeAndGrade() {
        val task = _activeCodingTask.value ?: return
        val code = _editorCode.value
        viewModelScope.launch {
            _isExecutingCode.value = true
            val result = when (task.language) {
                ProgrammingLanguage.JAVASCRIPT -> jsEngine.execute(code, task.expectedOutput, task.testCases, task.maxScore)
                ProgrammingLanguage.PYTHON -> pyEngine.execute(code, task.expectedOutput, task.testCases, task.maxScore)
                ProgrammingLanguage.HTML_CSS -> htmlEngine.evaluate(code, task.expectedOutput, task.testCases, task.maxScore)
            }
            _isExecutingCode.value = false
            _executionResult.value = result
        }
    }

    fun submitCodingTask() {
        val task = _activeCodingTask.value ?: return
        val user = currentUser.value ?: return
        val code = _editorCode.value

        viewModelScope.launch {
            _isExecutingCode.value = true
            val result = when (task.language) {
                ProgrammingLanguage.JAVASCRIPT -> jsEngine.execute(code, task.expectedOutput, task.testCases, task.maxScore)
                ProgrammingLanguage.PYTHON -> pyEngine.execute(code, task.expectedOutput, task.testCases, task.maxScore)
                ProgrammingLanguage.HTML_CSS -> htmlEngine.evaluate(code, task.expectedOutput, task.testCases, task.maxScore)
            }

            repository.submitCodingSolution(
                taskId = task.id,
                studentUser = user,
                sourceCode = code,
                language = task.language.id,
                score = result.score,
                maxScore = result.maxScore,
                passedTests = result.passedTests,
                totalTests = result.totalTests,
                output = result.stdout
            )

            _isExecutingCode.value = false
            _executionResult.value = result
        }
    }

    fun createTeacherAssignment(
        title: String,
        description: String,
        subjectName: String,
        maxScore: Int,
        questions: List<Question>,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = repository.createAssignment(title, description, subjectName, maxScore, questions)
            _isLoading.value = false
            res.onSuccess {
                onSuccess()
            }
        }
    }

    fun getHtmlPreview(code: String): String {
        return htmlEngine.buildHtmlDocument(code)
    }
}
