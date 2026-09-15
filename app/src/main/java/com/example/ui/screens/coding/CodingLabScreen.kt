package com.example.ui.screens.coding

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.CodingTask
import com.example.model.ProgrammingLanguage
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
    var activeTab by remember { mutableStateOf(0) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(task.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("مختبر ${task.language.title} • الدرجة القصوى: ${task.maxScore}", fontSize = 11.sp, color = Color.White.copy(alpha = .8f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.closeCodingTask() }, modifier = Modifier.testTag("coding_back_button")) {
                        Icon(Icons.Default.ArrowBack, "رجوع", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RoyalNavyDark)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().background(RoyalNavyDark).padding(padding)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(12.dp, 6.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(task.description, color = Color.White, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "اكتب الحل ثم استخدم اختبار للتأكد من صحته. الإجابات الصحيحة والاختبارات المخفية لا تظهر للطالب.",
                        color = AmberGoldLight,
                        fontSize = 11.sp
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().background(Color(0xFF1E293B)).padding(12.dp, 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("محرر الكود (${task.language.extension})", color = Color.LightGray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        Text("UTF-8", color = Color.Gray, fontSize = 11.sp)
                    }
                    Box(Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState())) {
                        BasicTextField(
                            value = editorCode,
                            onValueChange = viewModel::updateEditorCode,
                            modifier = Modifier.fillMaxWidth().testTag("code_editor_field"),
                            textStyle = TextStyle(color = Color(0xFFE2E8F0), fontSize = 14.sp, fontFamily = FontFamily.Monospace, lineHeight = 20.sp),
                            cursorBrush = SolidColor(AmberGoldLight)
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { activeTab = 0; viewModel.runCode() },
                    modifier = Modifier.weight(1f).height(44.dp).testTag("run_code_button"),
                    enabled = !isExecuting,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                ) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("تشغيل", fontSize = 13.sp)
                }
                Button(
                    onClick = { activeTab = 1; viewModel.checkCodeAndGrade() },
                    modifier = Modifier.weight(1f).height(44.dp).testTag("check_code_button"),
                    enabled = !isExecuting,
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold)
                ) {
                    Icon(Icons.Default.Check, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("اختبار", fontSize = 13.sp)
                }
                Button(
                    onClick = { activeTab = 1; viewModel.submitCodingTask() },
                    modifier = Modifier.weight(1.3f).height(44.dp).testTag("submit_code_button"),
                    enabled = !isExecuting,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess)
                ) {
                    if (isExecuting) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    else { Icon(Icons.Default.Send, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("تسليم الواجب", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                }
            }

            Card(
                Modifier.fillMaxWidth().height(200.dp).padding(horizontal = 12.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().background(Color(0xFF1E293B))) {
                        TabButton("المخرجات", activeTab == 0) { activeTab = 0 }
                        TabButton("نتيجة الاختبار", activeTab == 1) { activeTab = 1 }
                        if (task.language == ProgrammingLanguage.HTML_CSS) TabButton("معاينة HTML", activeTab == 2) { activeTab = 2 }
                    }
                    Box(Modifier.fillMaxSize().padding(10.dp)) {
                        when (activeTab) {
                            0 -> {
                                val res = executionResult
                                if (res == null) Text("اضغط تشغيل لمشاهدة المخرجات.", color = Color.Gray, fontSize = 12.sp)
                                else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                    if (res.stdout.isNotBlank()) Text(res.stdout, color = Color(0xFFE2E8F0), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                                    if (res.stderr != null) { Spacer(Modifier.height(6.dp)); Text(res.stderr, color = CrimsonError, fontSize = 13.sp, fontFamily = FontFamily.Monospace) }
                                }
                            }
                            1 -> {
                                val res = executionResult
                                if (res == null) Text("اضغط اختبار للتحقق من الحل وحساب الدرجة.", color = Color.Gray, fontSize = 12.sp)
                                else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("الدرجة: ${res.score.toInt()} من ${res.maxScore.toInt()}", color = if (res.score >= res.maxScore) EmeraldLight else AmberGoldLight, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text("${res.passedTests}/${res.totalTests} اختبارات ناجحة", color = Color.White, fontSize = 12.sp)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    res.testSummary.forEach { eval ->
                                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(if (eval.isPassed) Icons.Default.CheckCircle else Icons.Default.Cancel, null, tint = if (eval.isPassed) EmeraldLight else CrimsonError, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("اختبار ${eval.testIndex}", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                            Text(if (eval.isPassed) "ناجح (+${eval.points})" else "راسب", color = if (eval.isPassed) EmeraldLight else CrimsonError, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            else -> {
                                AndroidView(
                                    factory = { ctx -> WebView(ctx).apply { settings.javaScriptEnabled = true; settings.allowFileAccess = false; settings.allowContentAccess = false } },
                                    update = { it.loadDataWithBaseURL(null, viewModel.getHtmlPreview(editorCode), "text/html", "UTF-8", null) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun TabButton(title: String, isSelected: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = if (isSelected) AmberGoldLight else Color.Gray),
        shape = RoundedCornerShape(4.dp)
    ) { Text(title, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) }
}
