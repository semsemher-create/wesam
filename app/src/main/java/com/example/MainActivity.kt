package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.model.UserRole
import com.example.ui.screens.admin.AdminDashboardScreen
import com.example.ui.screens.coding.CodingLabScreen
import com.example.ui.screens.login.LoginScreen
import com.example.ui.screens.parent.ParentDashboardScreen
import com.example.ui.screens.student.AssignmentSolverScreen
import com.example.ui.screens.student.StudentDashboardScreen
import com.example.ui.screens.teacher.TeacherDashboardScreen
import com.example.ui.theme.AlWissamTheme
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AlWissamTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AlWissamApp(viewModel = mainViewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun AlWissamApp(viewModel: MainViewModel) {
    val currentUser by viewModel.currentUser.collectAsState()
    val activeAssignment by viewModel.activeAssignment.collectAsState()
    val activeCodingTask by viewModel.activeCodingTask.collectAsState()

    when {
        currentUser == null -> {
            LoginScreen(viewModel = viewModel)
        }
        activeCodingTask != null -> {
            CodingLabScreen(task = activeCodingTask!!, viewModel = viewModel)
        }
        activeAssignment != null -> {
            AssignmentSolverScreen(assignment = activeAssignment!!, viewModel = viewModel)
        }
        else -> {
            when (currentUser?.role) {
                UserRole.STUDENT -> StudentDashboardScreen(viewModel = viewModel)
                UserRole.TEACHER -> TeacherDashboardScreen(viewModel = viewModel)
                UserRole.PARENT -> ParentDashboardScreen(viewModel = viewModel)
                UserRole.ADMIN -> AdminDashboardScreen(viewModel = viewModel)
                null -> LoginScreen(viewModel = viewModel)
            }
        }
    }
}
