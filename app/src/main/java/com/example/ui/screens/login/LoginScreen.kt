package com.example.ui.screens.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel

@Composable
fun LoginScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    var adminMode by remember { mutableStateOf(false) }
    var inputCode by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val isLoading by viewModel.isLoading.collectAsState()
    val loginError by viewModel.loginError.collectAsState()

    Box(modifier = modifier.fillMaxSize().background(SlateBackground).padding(horizontal = 24.dp)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Spacer(Modifier.height(28.dp))
            Box(Modifier.size(96.dp).clip(CircleShape).background(RoyalNavy), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.ic_alwissam_logo), "شعار منصة الوسام", Modifier.size(78.dp).clip(CircleShape))
            }
            Spacer(Modifier.height(16.dp))
            Text("منصة الوسام التعليمية", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = RoyalNavy)
            Text("التطبيق التجريبي 0.1", fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.height(22.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LoginModeButton("دخول المستخدمين", !adminMode, Icons.Default.Key, Modifier.weight(1f)) { adminMode = false }
                LoginModeButton("إدارة المنصة", adminMode, Icons.Default.Lock, Modifier.weight(1f)) { adminMode = true }
            }
            Spacer(Modifier.height(12.dp))

            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardSurface), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    if (!adminMode) {
                        Text("دخول الطالب / المعلم / ولي الأمر", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(Modifier.height(6.dp))
                        Text("أدخل الكود الحقيقي المسجل في قاعدة بيانات منصة الوسام. لا توجد أكواد تجريبية وهمية.", fontSize = 12.sp, color = TextSecondary)
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(value = inputCode, onValueChange = { inputCode = it.uppercase() }, modifier = Modifier.fillMaxWidth().testTag("login_code_input"), label = { Text("كود الدخول") }, placeholder = { Text("STU… أو TCH… أو PAR…") }, singleLine = true, leadingIcon = { Icon(Icons.Default.Key, null, tint = RoyalNavy) }, shape = RoundedCornerShape(12.dp))
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.login(inputCode) }, modifier = Modifier.fillMaxWidth().height(50.dp).testTag("login_button"), enabled = !isLoading && inputCode.isNotBlank(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = RoyalNavy)) {
                            if (isLoading) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp) else { Icon(Icons.Default.Login, null); Spacer(Modifier.width(8.dp)); Text("دخول إلى المنصة", fontWeight = FontWeight.Bold) }
                        }
                    } else {
                        Text("دخول إدارة منصة الوسام", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(Modifier.height(6.dp))
                        Text("الإدارة تدخل بالبريد الإلكتروني وكلمة المرور الخاصة بحساب Supabase Auth.", fontSize = 12.sp, color = TextSecondary)
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth().testTag("admin_email_input"), label = { Text("البريد الإلكتروني للإدارة") }, placeholder = { Text("بريد المدير") }, singleLine = true, leadingIcon = { Icon(Icons.Default.Email, null, tint = RoyalNavy) }, shape = RoundedCornerShape(12.dp))
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth().testTag("admin_password_input"), label = { Text("كلمة المرور") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), leadingIcon = { Icon(Icons.Default.Lock, null, tint = RoyalNavy) }, shape = RoundedCornerShape(12.dp))
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.loginAdmin(email, password) }, modifier = Modifier.fillMaxWidth().height(50.dp).testTag("admin_login_button"), enabled = !isLoading && email.isNotBlank() && password.isNotBlank(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = RoyalNavy)) {
                            if (isLoading) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp) else { Icon(Icons.Default.Lock, null); Spacer(Modifier.width(8.dp)); Text("دخول الإدارة", fontWeight = FontWeight.Bold) }
                        }
                    }
                    AnimatedVisibility(loginError != null) { Column { Spacer(Modifier.height(10.dp)); Text(loginError ?: "", color = CrimsonError, fontSize = 13.sp) } }
                }
            }

            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardSurface), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(EmeraldSuccess))
                    Spacer(Modifier.width(8.dp))
                    Column { Text("حالة الاتصال", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary); Text("Supabase • تسجيل الدخول والتحقق من الحسابات يتمان عبر الخادم", fontSize = 11.sp, color = TextSecondary) }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun RowScope.LoginModeButton(title: String, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.height(46.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = if (selected) RoyalNavy else Color.White, contentColor = if (selected) Color.White else RoyalNavy)) {
        Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
