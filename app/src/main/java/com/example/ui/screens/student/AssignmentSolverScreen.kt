package com.example.ui.screens.student

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
fun AssignmentSolverScreen(
    assignment: Assignment,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val submissions by viewModel.submissions.collectAsState()
    val studentAnswers by viewModel.studentAnswers.collectAsState()
    val submissionMessage by viewModel.submissionMessage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val existingSubmission = remember(submissions, assignment, currentUser) {
        submissions.find { it.assignmentId == assignment.id && it.studentCode == (currentUser?.code ?: "") }
    }
    val isSubmitted = existingSubmission != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = assignment.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.closeAssignment() },
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "رجوع",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RoyalNavy)
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
                            text = assignment.description,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "المادة: ${assignment.subjectName ?: "عام"}",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            Text(
                                text = "الدرجة الكلية: ${assignment.maxScore}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AmberGold
                            )
                        }
                    }
                }
            }

            if (isSubmitted) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = EmeraldSuccess.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(12.dp),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldSuccess,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "تم تسليم هذا الواجب بنجاح",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = EmeraldSuccess
                                )
                                Text(
                                    text = "الدرجة المحتسبة: ${existingSubmission?.score?.toInt()} من ${existingSubmission?.maxScore?.toInt()}",
                                    fontSize = 13.sp,
                                    color = TextPrimary
                                )
                                if (existingSubmission?.feedback != null) {
                                    Text(
                                        text = existingSubmission.feedback,
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            itemsIndexed(assignment.questions) { index, q ->
                QuestionCard(
                    questionIndex = index + 1,
                    question = q,
                    selectedOptionIndex = studentAnswers[q.id],
                    isReadOnly = isSubmitted,
                    onSelectOption = { optIndex ->
                        if (!isSubmitted) {
                            viewModel.answerQuestion(q.id, optIndex)
                        }
                    }
                )
            }

            item {
                AnimatedVisibility(visible = submissionMessage != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AmberGold.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = submissionMessage ?: "",
                            modifier = Modifier.padding(16.dp),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = RoyalNavy,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            if (!isSubmitted) {
                item {
                    val allAnswered = assignment.questions.all { studentAnswers.containsKey(it.id) }

                    Button(
                        onClick = { viewModel.submitAssignment(assignment) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("submit_assignment_button"),
                        enabled = !isLoading && allAnswered,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "تسليم الواجب واحتساب الدرجة آلياً",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun QuestionCard(
    questionIndex: Int,
    question: Question,
    selectedOptionIndex: Int?,
    isReadOnly: Boolean,
    onSelectOption: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                Text(
                    text = "السؤال $questionIndex (${question.type.arabicName})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = RoyalNavy
                )

                Text(
                    text = "${question.points} درجات",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = question.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                question.options.forEachIndexed { optIndex, optionText ->
                    val isSelected = selectedOptionIndex == optIndex
                    val isCorrect = isReadOnly && optIndex == question.correctAnswerIndex

                    val borderColor = when {
                        isSelected && isCorrect -> EmeraldSuccess
                        isSelected -> RoyalNavy
                        isCorrect -> EmeraldSuccess
                        else -> CardBorder
                    }

                    val bgColor = when {
                        isSelected && isCorrect -> EmeraldSuccess.copy(alpha = 0.1f)
                        isSelected -> RoyalNavy.copy(alpha = 0.08f)
                        isCorrect -> EmeraldSuccess.copy(alpha = 0.1f)
                        else -> CardSurface
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(bgColor)
                            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                            .clickable(enabled = !isReadOnly) { onSelectOption(optIndex) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = if (!isReadOnly) { { onSelectOption(optIndex) } } else null,
                            colors = RadioButtonDefaults.colors(selectedColor = RoyalNavy)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = optionText,
                            fontSize = 14.sp,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )

                        if (isCorrect) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "الإجابة الصحيحة",
                                tint = EmeraldSuccess,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
