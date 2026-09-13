package com.example.model

enum class QuestionType(val arabicName: String) {
    MCQ("اختيار من متعدد"),
    TRUE_FALSE("صح أو خطأ")
}

data class Question(
    val id: String,
    val text: String,
    val type: QuestionType,
    val options: List<String> = emptyList(),
    val correctAnswerIndex: Int,
    val points: Int = 1
)

data class Assignment(
    val id: String,
    val title: String,
    val description: String,
    val classId: String? = null,
    val className: String? = null,
    val subjectId: String? = null,
    val subjectName: String? = null,
    val teacherName: String? = null,
    val dueAt: String? = null,
    val maxScore: Int = 10,
    val questions: List<Question> = emptyList(),
    val isCoding: Boolean = false,
    val codingTaskId: String? = null
)

data class AssignmentSubmission(
    val id: String,
    val assignmentId: String,
    val studentId: String,
    val studentCode: String,
    val studentName: String,
    val score: Double,
    val maxScore: Double,
    val feedback: String? = null,
    val submittedAt: String,
    val answers: Map<String, Int> = emptyMap(), // questionId to selectedIndex
    val status: String = "graded"
)
