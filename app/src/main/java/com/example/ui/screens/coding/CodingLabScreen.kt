package com.example.ui.screens.coding

import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodingLabScreen(
    task: CodingTask,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val editorCode by viewModel.editorCode.collectAsState()
    val executionResult by viewModel.executionResult.collectAsState()
    val isExecuting by viewModel.isExecutingCode.collectAsState()

    var activeBottomTab by remember { mutableStateOf(0) } // 0: Output, 1: Test Cases, 2: Preview (HTML)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = task.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "مختبر ${task.language.title} • الدرجة القصوى: ${task.maxScore}",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.closeCodingTask() },
                        modifier = Modifier.testTag("coding_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "رجوع",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RoyalNavyDark)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(RoyalNavyDark)
                .padding(paddingValues)
        ) {
            // Task Description Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = task.description,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "المخرجات المتوقعة: ${task.expectedOutput}",
                        color = AmberGoldLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Code Editor Area
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(8.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Editor Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "محرر الكود البرمجي (${task.language.extension})",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        Text(
                            text = "UTF-8",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }

                    // Editable Code Text Area
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        BasicTextField(
                            value = editorCode,
                            onValueChange = { viewModel.updateEditorCode(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("code_editor_field"),
                            textStyle = TextStyle(
                                color = Color(0xFFE2E8F0),
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 20.sp
                            ),
                            cursorBrush = SolidColor(AmberGoldLight)
                        )
                    }
                }
            }

            // Actions Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Run Code
                Button(
                    onClick = {
                        activeBottomTab = 0
                        viewModel.runCode()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("run_code_button"),
                    enabled = !isExecuting,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = EmeraldLight
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "تشغيل", fontSize = 13.sp, color = Color.White)
                }

                // Check Code
                Button(
                    onClick = {
                        activeBottomTab = 1
                        viewModel.checkCodeAndGrade()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("check_code_button"),
                    enabled = !isExecuting,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "اختبار", fontSize = 13.sp, color = Color.White)
                }

                // Submit Assignment
                Button(
                    onClick = {
                        activeBottomTab = 1
                        viewModel.submitCodingTask()
                    },
                    modifier = Modifier
                        .weight(1.3f)
                        .height(44.dp)
                        .testTag("submit_code_button"),
                    enabled = !isExecuting,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess)
                ) {
                    if (isExecuting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "تسليم الواجب", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            // Bottom Output / Test Cases Panel
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(8.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Tab header for output
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B)),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        TabButton(
                            title = "شاشة المخرجات (Console)",
                            isSelected = activeBottomTab == 0
                        ) { activeBottomTab = 0 }

                        TabButton(
                            title = "نتائج الاختبارات والتقييم",
                            isSelected = activeBottomTab == 1
                        ) { activeBottomTab = 1 }

                        if (task.language == ProgrammingLanguage.HTML_CSS) {
                            TabButton(
                                title = "معاينة حية للمتصفح",
                                isSelected = activeBottomTab == 2
                            ) { activeBottomTab = 2 }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                    ) {
                        when (activeBottomTab) {
                            0 -> {
                                // Console Output
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    val res = executionResult
                                    if (res == null) {
                                        Text(
                                            text = "اضغط على زر 'تشغيل' لتنفيذ الكود ومشاهدة المخرجات هنا.",
                                            color = Color.Gray,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    } else {
                                        if (res.stdout.isNotBlank()) {
                                            Text(
                                                text = res.stdout,
                                                color = Color(0xFFE2E8F0),
                                                fontSize = 13.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        if (res.stderr != null) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = res.stderr,
                                                color = CrimsonError,
                                                fontSize = 13.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }
                            1 -> {
                                // Test Cases & Score
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    val res = executionResult
                                    if (res == null) {
                                        Text(
                                            text = "اضغط على 'اختبار' للتحقق الآلي من الكود وحساب الدرجة.",
                                            color = Color.Gray,
                                            fontSize = 12.sp
                                        )
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "الدرجة المستحقة: ${res.score.toInt()} من ${res.maxScore.toInt()}",
                                                color = if (res.score >= res.maxScore) EmeraldLight else AmberGoldLight,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "الاختبارات الناجحة: ${res.passedTests}/${res.totalTests}",
                                                color = Color.White,
                                                fontSize = 12.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        res.testSummary.forEach { eval ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (eval.isPassed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                                    contentDescription = null,
                                                    tint = if (eval.isPassed) EmeraldLight else CrimsonError,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "اختبار ${eval.testIndex}: ${eval.inputDescription}",
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = if (eval.isPassed) "ناجح (+${eval.points})" else "راسب",
                                                    color = if (eval.isPassed) EmeraldLight else CrimsonError,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            2 -> {
                                // Live HTML preview inside secure WebView
                                AndroidView(
                                    factory = { ctx ->
                                        WebView(ctx).apply {
                                            settings.javaScriptEnabled = true
                                            settings.allowFileAccess = false
                                            settings.allowContentAccess = false
                                            loadDataWithBaseURL(
                                                null,
                                                viewModel.getHtmlPreview(editorCode),
                                                "text/html",
                                                "UTF-8",
                                                null
                                            )
                                        }
                                    },
                                    update = { wv ->
                                        wv.loadDataWithBaseURL(
                                            null,
                                            viewModel.getHtmlPreview(editorCode),
                                            "text/html",
                                            "UTF-8",
                                            null
                                        )
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
fun TabButton(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (isSelected) AmberGoldLight else Color.Gray
        ),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
