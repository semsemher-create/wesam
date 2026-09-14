package com.example.ui.screens.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentDashboardScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val assignments by viewModel.assignments.collectAsState()
    val submissions by viewModel.submissions.collectAsState()
    val codingSubs by viewModel.codingSubmissions.collectAsState()
    val selectedChildCode by viewModel.selectedChildCode.collectAsState()

    // Never invent children locally. Only children returned by the real parent record are shown.
    val linkedCodes = currentUser?.linkedStudentCodes.orEmpty()
    val selectedCode = selectedChildCode.takeIf { it.isNotBlank() && linkedCodes.contains(it) }
        ?: linkedCodes.firstOrNull().orEmpty()

    val childSubmissions = remember(submissions, selectedCode) {
        submissions.filter { it.studentCode == selectedCode }
    }

    val childCodingSubs = remember(codingSubs, selectedCode) {
        codingSubs.filter { it.studentCode == selectedCode }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentUser?.name ?: "بوابة ولي الأمر",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "كود ولي الأمر: ${currentUser?.code ?: ""} • متابعة الأبناء",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.logout() },
                        modifier = Modifier.testTag("parent_logout_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "تسجيل الخروج",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AmberGold)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(SlateBackground)
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "أبنائي المسجلون في المنصة",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "يتم عرض الأبناء المرتبطين فعليًا بحساب ولي الأمر من قاعدة بيانات الوسام.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (linkedCodes.isEmpty()) {
                            Text(
                                text = "لا يوجد أبناء مرتبطون بهذا الحساب حتى الآن.",
                                fontSize = 13.sp,
                                color = TextMuted
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                linkedCodes.forEach { code ->
                                    val isSelected = code == selectedCode
                                    Button(
                                        onClick = { viewModel.selectChild(code) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .testTag("select_child_$code"),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isSelected) AmberGold else Color(0xFFE2E8F0),
                                            contentColor = if (isSelected) Color.White else TextPrimary
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Face,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = code,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

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
                            text = if (selectedCode.isBlank()) "لم يتم اختيار طالب" else "الطالب: $selectedCode",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val totalEarned = childSubmissions.sumOf { it.score } + childCodingSubs.sumOf { it.score }
                        val totalMax = childSubmissions.sumOf { it.maxScore } + childCodingSubs.sumOf { it.maxScore }
                        val avgPercent = if (totalMax > 0) (totalEarned / totalMax * 100).toInt() else 0

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            StatItem(title = "النسبة العامة", value = "$avgPercent%")
                            StatItem(title = "الواجبات المنجزة", value = "${childSubmissions.size}/${assignments.size}")
                            StatItem(title = "تحديات البرمجة", value = "${childCodingSubs.size}")
                        }
                    }
                }
            }

            item {
                Text(
                    text = "سجل الواجبات والدرجات",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            if (selectedCode.isBlank() || (childSubmissions.isEmpty() && childCodingSubs.isEmpty())) {
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
                                text = if (selectedCode.isBlank()) "لا توجد بيانات طالب مرتبطة بهذا الحساب." else "لم يقم الطالب بتسليم واجبات حتى الآن.",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            items(childSubmissions) { sub ->
                val asg = assignments.find { it.id == sub.assignmentId }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = asg?.title ?: "واجب مدرسي (${sub.assignmentId})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "تاريخ التسليم: ${sub.submittedAt}", fontSize = 11.sp, color = TextMuted)
                            sub.feedback?.let {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "ملاحظات المعلم: $it", fontSize = 12.sp, color = EmeraldSuccess)
                            }
                        }
                        Surface(color = EmeraldSuccess.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
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

            items(childCodingSubs) { csub ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "مختبر البرمجة: ${csub.taskTitle} (${csub.language})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "اجتاز ${csub.passedTests} من أصل ${csub.totalTests} اختبارات برمجية",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                        Surface(color = AmberGold.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
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

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = AmberGold, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "بوابة ولي الأمر مخصصة للمتابعة والاطلاع فقط، ولا يمكن تعديل أو تقديم الإجابات من خلالها.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatItem(title: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AmberGoldLight)
        Text(text = title, fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
    }
}
