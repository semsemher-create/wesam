package com.example.model

enum class UserRole(val arabicTitle: String) {
    STUDENT("طالب"),
    TEACHER("معلم"),
    PARENT("ولي أمر"),
    ADMIN("مدير النظام")
}

data class SessionUser(
    val id: String,
    val code: String,
    val name: String,
    val role: UserRole,
    val classId: String? = null,
    val className: String? = null,
    val email: String? = null,
    val linkedStudentCodes: List<String> = emptyList()
)
