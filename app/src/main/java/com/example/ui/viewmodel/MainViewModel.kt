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

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _activeCodingTask = MutableStateFlow<CodingTask?>(null)
    val activeCodingTask: StateFlow<CodingTask?> = _activeCodingTask.asStateFlow()
    private val _editorCode = MutableStateFlow("")
    val editorCode: StateFlow<String> = _editorCode.asStateFlow()
    private val _executionResult = MutableStateFlow<ExecutionResult?>(null)
    val executionResult: StateFlow<ExecutionResult?> = _executionResult.asStateFlow()
    private val _isExecutingCode = MutableStateFlow(false)
    val isExecutingCode: StateFlow<Boolean> = _isExecutingCode.asStateFlow()
    private val _activeAssignment = MutableStateFlow<Assignment?>(null)
    val activeAssignment: StateFlow<Assignment?> = _activeAssignment.asStateFlow()
    private val _studentAnswers = MutableStateFlow<Map<String, Int>>(emptyMap())
    val studentAnswers: StateFlow<Map<String, Int>> = _studentAnswers.asStateFlow()
    private val _submissionMessage = MutableStateFlow<String?>(null)
    val submissionMessage: StateFlow<String?> = _submissionMessage.asStateFlow()
    private val _selectedChildCode = MutableStateFlow("STU004")
    val selectedChildCode: StateFlow<String> = _selectedChildCode.asStateFlow()

    init {
        viewModelScope.launch { repository.refreshRemoteData() }
    }

    fun selectChild(code: String) { _selectedChildCode.value = code }

    fun login(code: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _loginError.value = null
            val result = repository.loginWithCode(code)
            _isLoading.value = false
            result.onFailure { _loginError.value = it.message }
            result.onSuccess { user -> if (user.role == UserRole.PARENT && user.linkedStudentCodes.isNotEmpty()) _selectedChildCode.value = user.linkedStudentCodes.first() }
        }
    }

    fun logout() {
        repository.logout()
        _activeAssignment.value = null
        _activeCodingTask.value = null
        _executionResult.value = null
        _studentAnswers.value = emptyMap()
    }

    fun openAssignment(assignment: Assignment) { _activeAssignment.value = assignment; _studentAnswers.value = emptyMap(); _submissionMessage.value = null }
    fun closeAssignment() { _activeAssignment.value = null; _studentAnswers.value = emptyMap(); _submissionMessage.value = null }
    fun answerQuestion(questionId: String, optionIndex: Int) { _studentAnswers.value = _studentAnswers.value + (questionId to optionIndex) }

    fun submitAssignment(assignment: Assignment) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.submitAssignmentAnswers(assignment.id, user, _studentAnswers.value)
            _isLoading.value = false
            result.onSuccess { _submissionMessage.value = "تم تسليم الواجب وحساب الدرجة بنجاح! درجتك: ${it.score} / ${it.maxScore}" }
            result.onFailure { _submissionMessage.value = it.message ?: "تعذر تسليم الواجب" }
        }
    }

    fun openCodingTask(task: CodingTask) {
        _activeCodingTask.value = task
        val user = currentUser.value
        val existing = codingSubmissions.value.find { it.taskId == task.id && it.studentCode == user?.code }
        _editorCode.value = existing?.sourceCode ?: task.starterCode
        _executionResult.value = null
    }
    fun closeCodingTask() { _activeCodingTask.value = null; _executionResult.value = null }
    fun updateEditorCode(code: String) { _editorCode.value = code }

    fun runCode() {
        val task = _activeCodingTask.value ?: return
        viewModelScope.launch {
            _isExecutingCode.value = true
            _executionResult.value = when (task.language) {
                ProgrammingLanguage.JAVASCRIPT -> jsEngine.execute(_editorCode.value, task.expectedOutput, emptyList(), task.maxScore)
                ProgrammingLanguage.PYTHON -> pyEngine.execute(_editorCode.value, task.expectedOutput, emptyList(), task.maxScore)
                ProgrammingLanguage.HTML_CSS -> htmlEngine.evaluate(_editorCode.value, task.expectedOutput, emptyList(), task.maxScore)
            }
            _isExecutingCode.value = false
        }
    }

    fun checkCodeAndGrade() {
        val task = _activeCodingTask.value ?: return
        viewModelScope.launch {
            _isExecutingCode.value = true
            _executionResult.value = when (task.language) {
                ProgrammingLanguage.JAVASCRIPT -> jsEngine.execute(_editorCode.value, task.expectedOutput, task.testCases, task.maxScore)
                ProgrammingLanguage.PYTHON -> pyEngine.execute(_editorCode.value, task.expectedOutput, task.testCases, task.maxScore)
                ProgrammingLanguage.HTML_CSS -> htmlEngine.evaluate(_editorCode.value, task.expectedOutput, task.testCases, task.maxScore)
            }
            _isExecutingCode.value = false
        }
    }

    fun submitCodingTask() {
        val task = _activeCodingTask.value ?: return
        val user = currentUser.value ?: return
        viewModelScope.launch {
            _isExecutingCode.value = true
            val result = when (task.language) {
                ProgrammingLanguage.JAVASCRIPT -> jsEngine.execute(_editorCode.value, task.expectedOutput, task.testCases, task.maxScore)
                ProgrammingLanguage.PYTHON -> pyEngine.execute(_editorCode.value, task.expectedOutput, task.testCases, task.maxScore)
                ProgrammingLanguage.HTML_CSS -> htmlEngine.evaluate(_editorCode.value, task.expectedOutput, task.testCases, task.maxScore)
            }
            val save = repository.submitCodingSolution(task.id, user, _editorCode.value, task.language.id, result.score, result.maxScore, result.passedTests, result.totalTests, result.stdout)
            _isExecutingCode.value = false
            if (save.isFailure) _executionResult.value = result.copy(stderr = save.exceptionOrNull()?.message ?: "تعذر حفظ التسليم") else _executionResult.value = result
        }
    }

    fun createTeacherAssignment(title: String, description: String, subjectName: String, maxScore: Int, questions: List<Question>, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.createAssignment(title, description, subjectName, maxScore, questions)
            _isLoading.value = false
            result.onSuccess { onSuccess() }
        }
    }

    fun getHtmlPreview(code: String): String = htmlEngine.buildHtmlDocument(code)
}
