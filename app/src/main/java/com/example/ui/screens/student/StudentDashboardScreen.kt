package com.example.ui.screens.student

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDashboardScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val assignments by viewModel.assignments.collectAsState()
    val submissions by viewModel.submissions.collectAsState()
    val codingTasks by viewModel.codingTasks.collectAsState()
    val codingSubs by viewModel.codingSubmissions.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("الواجبات والمهام", "معامل البرمجة", "سجل الدرجات")

    val studentSubmissions = remember(submissions, currentUser) {
        submissions.filter { it.studentCode == (currentUser?.code ?: "") }
    }

    val studentCodingSubs = remember(codingSubs, currentUser) {
        codingSubs.filter { it.studentCode == (currentUser?.code ?: "") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentUser?.name ?: "بوابة الطالب",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "كود الطالب: ${currentUser?.code ?: ""} • ${currentUser?.className ?: "الصف الأول"}",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.logout() },
                        modifier = Modifier.testTag("logout_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "تسجيل الخروج",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RoyalNavy)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(SlateBackground)
                .padding(paddingValues)
        ) {
            // Tab Selector
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = CardSurface,
                contentColor = RoyalNavy
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> AssignmentsTab(
                    assignments = assignments,
                    submissions = studentSubmissions,
                    onOpenAssignment = { viewModel.openAssignment(it) },
                    onOpenCodingTask = { task -> viewModel.openCodingTask(task) },
                    allCodingTasks = codingTasks
                )
                1 -> CodingLabsTab(
                    codingTasks = codingTasks,
                    submissions = studentCodingSubs,
                    onSelectTask = { viewModel.openCodingTask(it) }
                )
                2 -> GradesTab(
                    submissions = studentSubmissions,
                    codingSubmissions = studentCodingSubs
                )
            }
        }
    }
}

@Composable
fun AssignmentsTab(
    assignments: List<Assignment>,
    submissions: List<AssignmentSubmission>,
    onOpenAssignment: (Assignment) -> Unit,
    onOpenCodingTask: (CodingTask) -> Unit,
    allCodingTasks: List<CodingTask>
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "قائمة الواجبات الدراسية والتحديات",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        items(assignments) { asg ->
            val submission = submissions.find { it.assignmentId == asg.id }
            val isSubmitted = submission != null

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (asg.isCoding && asg.codingTaskId != null) {
                            val cTask = allCodingTasks.find { it.id == asg.codingTaskId }
                            if (cTask != null) {
                                onOpenCodingTask(cTask)
                            } else {
                                onOpenAssignment(asg)
                            }
                        } else {
                            onOpenAssignment(asg)
                        }
                    },
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = if (asg.isCoding) AmberGold.copy(alpha = 0.15f) else RoyalNavy.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = asg.subjectName ?: "عام",
                                color = if (asg.isCoding) AmberGold else RoyalNavy,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        if (isSubmitted) {
                            Surface(
                                color = EmeraldSuccess.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = EmeraldSuccess,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "تم التسليم: ${submission?.score?.toInt()} / ${submission?.maxScore?.toInt()}",
                                        color = EmeraldSuccess,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            Surface(
                                color = CrimsonError.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "بانتظار الحل",
                                    color = CrimsonError,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = asg.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = asg.description,
                        fontSize = 13.sp,
                        color = TextSecondary,
                        maxLines = 2
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "المعلم: ${asg.teacherName ?: "الإدارة"}",
                            fontSize = 12.sp,
                            color = TextMuted
                        )

                        Text(
                            text = if (isSubmitted) "عرض النتيجة والإجابات ←" else "ابدأ حل الواجب الآن ←",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSubmitted) EmeraldSuccess else RoyalNavy
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CodingLabsTab(
    codingTasks: List<CodingTask>,
    submissions: List<CodingSubmission>,
    onSelectTask: (CodingTask) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(
                    text = "معامل البرمجة التفاعلية (Coding Labs)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "بيئة تشغيل واختبار آلي للغات البرمجة (JS - Python - HTML/CSS)",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        items(codingTasks) { task ->
            val sub = submissions.find { it.taskId == task.id }
            val isCompleted = sub != null

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectTask(task) },
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val langColor = when (task.language) {
                            ProgrammingLanguage.JAVASCRIPT -> AmberGold
                            ProgrammingLanguage.PYTHON -> RoyalNavyLight
                            ProgrammingLanguage.HTML_CSS -> EmeraldSuccess
                        }

                        Surface(
                            color = langColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = task.language.title,
                                color = langColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        if (isCompleted) {
                            Text(
                                text = "مكتمل (${sub?.score?.toInt()} / ${sub?.maxScore?.toInt()})",
                                color = EmeraldSuccess,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = "غير منجز",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = task.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = task.description,
                        fontSize = 13.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { onSelectTask(task) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalNavy)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "فتح المختبر البرمجي", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun GradesTab(
    submissions: List<AssignmentSubmission>,
    codingSubmissions: List<CodingSubmission>
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = RoyalNavy),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "السجل الأكاديمي والتقييم الآلي",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val totalEarned = submissions.sumOf { it.score } + codingSubmissions.sumOf { it.score }
                    val totalMax = submissions.sumOf { it.maxScore } + codingSubmissions.sumOf { it.maxScore }
                    val percent = if (totalMax > 0) (totalEarned / totalMax * 100).toInt() else 100

                    Text(
                        text = "$percent%",
                        color = AmberGoldLight,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "إجمالي المهام المسلمة: ${submissions.size + codingSubmissions.size}",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
            }
        }

        item {
            Text(
                text = "تفاصيل تسليمات الواجبات",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        if (submissions.isEmpty() && codingSubmissions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "لم تقم بتسليم أي واجب بعد. يمكنك البدء في حل الواجبات من تبويب المهام.",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        items(submissions) { sub ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "واجب: ${sub.assignmentId}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "تاريخ التسليم: ${sub.submittedAt}",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                        if (sub.feedback != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "الملاحظات: ${sub.feedback}",
                                fontSize = 12.sp,
                                color = EmeraldSuccess
                            )
                        }
                    }

                    Surface(
                        color = EmeraldSuccess.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${sub.score.toInt()} / ${sub.maxScore.toInt()}",
                            color = EmeraldSuccess,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        items(codingSubmissions) { csub ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "مختبر برمجي: ${csub.taskTitle}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "لغة البرمجة: ${csub.language} • نجاح ${csub.passedTests}/${csub.totalTests} اختبار",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    Surface(
                        color = AmberGold.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${csub.score.toInt()} / ${csub.maxScore.toInt()}",
                            color = AmberGold,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}
