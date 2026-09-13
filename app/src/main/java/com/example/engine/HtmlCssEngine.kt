package com.example.engine

import com.example.model.TestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HtmlCssEngine {

    suspend fun evaluate(
        code: String,
        expectedOutput: String,
        testCases: List<TestCase>,
        maxScore: Int = 10
    ): ExecutionResult = withContext(Dispatchers.Default) {
        val evaluations = mutableListOf<TestEvaluation>()
        var earnedPoints = 0
        var totalPoints = 0
        var passedCount = 0

        // Check if H1 exists and has expected text or classes
        val hasH1 = code.contains("<h1", ignoreCase = true) && code.contains("</h1>", ignoreCase = true)
        val containsText = code.contains(expectedOutput.trim(), ignoreCase = true)
        val hasStyle = code.contains("<style>", ignoreCase = true) || code.contains("style=", ignoreCase = true)

        val isPassed = hasH1 && containsText

        if (testCases.isEmpty()) {
            evaluations.add(
                TestEvaluation(
                    testIndex = 1,
                    isPassed = isPassed,
                    isHidden = false,
                    inputDescription = "التحقق من وسوم HTML والنص المطلوب",
                    actualOutput = if (isPassed) "تم العثور على الوسوم والتنسيقات المطلوبة بنجاح" else "لم يتم العثور على الوسوم المطلوبة بدقة",
                    expectedOutput = expectedOutput,
                    points = maxScore
                )
            )
            if (isPassed) {
                earnedPoints = maxScore
                passedCount = 1
            }
            totalPoints = maxScore
        } else {
            testCases.forEachIndexed { index, tc ->
                totalPoints += tc.points
                val passed = code.contains(tc.expectedOutput.trim(), ignoreCase = true)
                if (passed) {
                    earnedPoints += tc.points
                    passedCount++
                }
                evaluations.add(
                    TestEvaluation(
                        testIndex = index + 1,
                        isPassed = passed,
                        isHidden = tc.isHidden,
                        inputDescription = "فحص عناصر ومحتوى الصفحة",
                        actualOutput = if (passed) "مطابق للمواصفات المطلوبة" else "غير مطابق",
                        expectedOutput = tc.expectedOutput,
                        points = tc.points
                    )
                )
            }
        }

        val finalScore = if (totalPoints > 0) {
            (earnedPoints.toDouble() / totalPoints.toDouble()) * maxScore.toDouble()
        } else {
            if (isPassed) maxScore.toDouble() else 0.0
        }

        ExecutionResult(
            isSuccess = isPassed,
            stdout = "تم فحص كود HTML & CSS وتجهيز المعاينة الحية.",
            stderr = if (!isPassed) "يرجى التأكد من كتابة النص المطلوب داخل وسم H1 وإضافة التنسيق اللازم." else null,
            score = finalScore,
            maxScore = maxScore.toDouble(),
            passedTests = passedCount,
            totalTests = if (testCases.isNotEmpty()) testCases.size else 1,
            testSummary = evaluations
        )
    }

    fun buildHtmlDocument(userCode: String): String {
        return """
            <!DOCTYPE html>
            <html dir="rtl" lang="ar">
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        font-family: system-ui, -apple-system, sans-serif;
                        margin: 0;
                        padding: 16px;
                        background-color: #F8FAFC;
                        color: #0F172A;
                    }
                </style>
            </head>
            <body>
                $userCode
            </body>
            </html>
        """.trimIndent()
    }
}
