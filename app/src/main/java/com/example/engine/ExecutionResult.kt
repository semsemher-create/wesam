package com.example.engine

data class ExecutionResult(
    val isSuccess: Boolean,
    val stdout: String,
    val stderr: String? = null,
    val score: Double = 0.0,
    val maxScore: Double = 10.0,
    val passedTests: Int = 0,
    val totalTests: Int = 0,
    val testSummary: List<TestEvaluation> = emptyList()
)

data class TestEvaluation(
    val testIndex: Int,
    val isPassed: Boolean,
    val isHidden: Boolean,
    val inputDescription: String,
    val actualOutput: String,
    val expectedOutput: String,
    val points: Int
)
