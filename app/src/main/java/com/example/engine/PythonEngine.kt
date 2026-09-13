package com.example.engine

import com.example.model.TestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

class PythonEngine {

    suspend fun execute(
        code: String,
        expectedOutput: String,
        testCases: List<TestCase>,
        maxScore: Int = 10
    ): ExecutionResult = withContext(Dispatchers.Default) {
        val logs = mutableListOf<String>()
        val errors = mutableListOf<String>()

        // Safety check against dangerous keywords
        val forbidden = listOf("import os", "import sys", "import subprocess", "import socket", "open(", "eval(", "exec(")
        for (f in forbidden) {
            if (code.contains(f)) {
                return@withContext ExecutionResult(
                    isSuccess = false,
                    stdout = "",
                    stderr = "خطأ أمني: استخدام الدوال النظامية '$f' محظور في بيئة المختبر التعليمي.",
                    score = 0.0,
                    maxScore = maxScore.toDouble()
                )
            }
        }

        try {
            interpretPythonSafe(code, logs)
        } catch (e: Exception) {
            errors.add(e.message ?: "خطأ أثناء تنفيذ كود Python")
        }

        val stdoutText = logs.joinToString("\n").trim()
        val stderrText = if (errors.isNotEmpty()) errors.joinToString("\n").trim() else null

        // Evaluate test cases
        val evaluations = mutableListOf<TestEvaluation>()
        var earnedPoints = 0
        var totalPoints = 0
        var passedCount = 0

        if (testCases.isEmpty()) {
            val passed = stdoutText.contains(expectedOutput.trim()) || stdoutText == expectedOutput.trim()
            evaluations.add(
                TestEvaluation(
                    testIndex = 1,
                    isPassed = passed,
                    isHidden = false,
                    inputDescription = "التحقق الأساسي",
                    actualOutput = stdoutText,
                    expectedOutput = expectedOutput,
                    points = maxScore
                )
            )
            if (passed) {
                earnedPoints = maxScore
                passedCount = 1
            }
            totalPoints = maxScore
        } else {
            testCases.forEachIndexed { index, tc ->
                totalPoints += tc.points
                val isPassed = stdoutText.contains(tc.expectedOutput.trim())
                if (isPassed) {
                    earnedPoints += tc.points
                    passedCount++
                }
                evaluations.add(
                    TestEvaluation(
                        testIndex = index + 1,
                        isPassed = isPassed,
                        isHidden = tc.isHidden,
                        inputDescription = if (tc.input.isNotBlank()) "المدخل: ${tc.input}" else "اختبار الإخراج",
                        actualOutput = if (tc.isHidden && !isPassed) "مخفي لسرية الاختبار" else stdoutText,
                        expectedOutput = if (tc.isHidden) "مخفي" else tc.expectedOutput,
                        points = tc.points
                    )
                )
            }
        }

        val finalScore = if (totalPoints > 0) {
            (earnedPoints.toDouble() / totalPoints.toDouble()) * maxScore.toDouble()
        } else {
            if (errors.isEmpty()) maxScore.toDouble() else 0.0
        }

        ExecutionResult(
            isSuccess = errors.isEmpty() && passedCount > 0,
            stdout = stdoutText,
            stderr = stderrText,
            score = finalScore,
            maxScore = maxScore.toDouble(),
            passedTests = passedCount,
            totalTests = if (testCases.isNotEmpty()) testCases.size else 1,
            testSummary = evaluations
        )
    }

    private fun interpretPythonSafe(code: String, output: MutableList<String>) {
        val lines = code.lines()
        val variables = mutableMapOf<String, Any>()

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            // Check for assignment: var = expr
            val assignMatch = Regex("""^([a-zA-Z_][a-zA-Z0-9_]*)\s*=\s*(.+)$""").find(line)
            if (assignMatch != null && !line.startsWith("print(")) {
                val varName = assignMatch.groupValues[1]
                val expression = assignMatch.groupValues[2].trim()
                val evaluatedValue = evaluateExpression(expression, variables)
                variables[varName] = evaluatedValue
                continue
            }

            // Check for print statement: print(...)
            val printMatch = Regex("""^print\s*\((.*)\)$""").find(line)
            if (printMatch != null) {
                val inner = printMatch.groupValues[1].trim()
                val evaluated = evaluatePrintArgs(inner, variables)
                output.add(evaluated)
                continue
            }
        }
    }

    private fun evaluatePrintArgs(argsStr: String, variables: Map<String, Any>): String {
        if (argsStr.isEmpty()) return ""

        // Handle string literals or expressions
        if ((argsStr.startsWith("\"") && argsStr.endsWith("\"")) ||
            (argsStr.startsWith("'") && argsStr.endsWith("'"))
        ) {
            return argsStr.substring(1, argsStr.length - 1)
        }

        // Try evaluating expression
        return evaluateExpression(argsStr, variables).toString()
    }

    private fun evaluateExpression(expr: String, variables: Map<String, Any>): Any {
        val trimmed = expr.trim()

        // String literal
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
            (trimmed.startsWith("'") && trimmed.endsWith("'"))
        ) {
            return trimmed.substring(1, trimmed.length - 1)
        }

        // Integer or Double
        trimmed.toLongOrNull()?.let { return it }
        trimmed.toDoubleOrNull()?.let { return it }

        // Variable lookup
        if (variables.containsKey(trimmed)) {
            return variables[trimmed]!!
        }

        // Simple arithmetic with variables or numbers: e.g. x * x, a + b, 5 * 5
        val operators = listOf("+", "-", "*", "/", "%")
        for (op in operators) {
            val parts = trimmed.split(op)
            if (parts.size == 2) {
                val left = evaluateExpression(parts[0].trim(), variables)
                val right = evaluateExpression(parts[1].trim(), variables)

                if (left is Number && right is Number) {
                    val l = left.toDouble()
                    val r = right.toDouble()
                    return when (op) {
                        "+" -> if (left is Long && right is Long) left + right else l + r
                        "-" -> if (left is Long && right is Long) left - right else l - r
                        "*" -> if (left is Long && right is Long) left * right else l * r
                        "/" -> if (r != 0.0) l / r else throw ArithmeticException("القسمة على صفر غير جائزة")
                        "%" -> if (left is Long && right is Long) left % right else l % r
                        else -> l
                    }
                } else if (op == "+" && (left is String || right is String)) {
                    return left.toString() + right.toString()
                }
            }
        }

        return trimmed
    }
}
