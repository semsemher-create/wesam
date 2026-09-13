package com.example.model

enum class ProgrammingLanguage(val id: String, val title: String, val extension: String) {
    JAVASCRIPT("javascript", "JavaScript", ".js"),
    PYTHON("python", "Python", ".py"),
    HTML_CSS("html_css", "HTML & CSS", ".html")
}

data class TestCase(
    val id: String,
    val input: String = "",
    val expectedOutput: String,
    val isHidden: Boolean = true,
    val points: Int = 1
)

data class CodingTask(
    val id: String,
    val title: String,
    val description: String,
    val language: ProgrammingLanguage,
    val starterCode: String,
    val expectedOutput: String,
    val maxScore: Int = 10,
    val testCases: List<TestCase> = emptyList(),
    val relatedAssignmentId: String? = null
)

data class CodingSubmission(
    val id: String,
    val taskId: String,
    val taskTitle: String,
    val studentId: String,
    val studentCode: String,
    val sourceCode: String,
    val language: String,
    val score: Double,
    val maxScore: Double,
    val passedTests: Int,
    val totalTests: Int,
    val output: String = "",
    val submittedAt: String,
    val status: String = "Submitted"
)
