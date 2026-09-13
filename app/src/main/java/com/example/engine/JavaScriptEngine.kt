package com.example.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.example.model.TestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class JavaScriptEngine(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    suspend fun execute(
        code: String,
        expectedOutput: String,
        testCases: List<TestCase>,
        maxScore: Int = 10
    ): ExecutionResult = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val logs = mutableListOf<String>()
            val errors = mutableListOf<String>()

            val webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.databaseEnabled = false
                settings.domStorageEnabled = false
            }

            class ConsoleBridge {
                @JavascriptInterface
                fun log(message: String) {
                    logs.add(message)
                }

                @JavascriptInterface
                fun error(message: String) {
                    errors.add(message)
                }
            }

            webView.addJavascriptInterface(ConsoleBridge(), "_AndroidLogger")

            val wrappedScript = """
                (function() {
                    const originalLog = console.log;
                    const originalError = console.error;
                    console.log = function(...args) {
                        _AndroidLogger.log(args.map(a => String(a)).join(' '));
                    };
                    console.error = function(...args) {
                        _AndroidLogger.error(args.map(a => String(a)).join(' '));
                    };
                    window.onerror = function(msg, url, line) {
                        _AndroidLogger.error('خطأ: ' + msg + ' (سطر ' + line + ')');
                    };
                    try {
                        $code
                    } catch (e) {
                        _AndroidLogger.error('خطأ برمجي: ' + e.message);
                    }
                })();
            """.trimIndent()

            webView.evaluateJavascript(wrappedScript) { _ ->
                mainHandler.postDelayed({
                    val stdoutText = logs.joinToString("\n").trim()
                    val stderrText = if (errors.isNotEmpty()) errors.joinToString("\n").trim() else null

                    // Evaluate test cases
                    val evaluations = mutableListOf<TestEvaluation>()
                    var earnedPoints = 0
                    var totalPoints = 0
                    var passedCount = 0

                    if (testCases.isEmpty()) {
                        // Compare against expected output
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

                    try {
                        webView.destroy()
                    } catch (e: Exception) {}

                    continuation.resume(
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
                    )
                }, 300)
            }
        }
    }
}
