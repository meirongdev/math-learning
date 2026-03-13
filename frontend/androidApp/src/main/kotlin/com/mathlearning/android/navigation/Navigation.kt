package com.mathlearning.android.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mathlearning.android.cache.NetworkMonitor
import com.mathlearning.android.ui.components.OfflineBanner
import com.mathlearning.android.ui.components.StudentSelector
import com.mathlearning.android.ui.growth.GrowthScreen
import com.mathlearning.android.ui.history.HistoryScreen
import com.mathlearning.android.ui.knowledge.KnowledgeScreen
import com.mathlearning.android.ui.mistakes.MistakesScreen
import com.mathlearning.android.ui.settings.SettingsScreen
import com.mathlearning.android.ui.solve.SolveScreen
import com.mathlearning.shared.model.Student
import org.koin.compose.koinInject

enum class Screen(val route: String, val label: String, val icon: ImageVector) {
    Solve("solve", "Solve", Icons.Default.Calculate),
    Knowledge("knowledge", "Knowledge", Icons.Default.AccountTree),
    Growth("growth", "Growth", Icons.Default.EmojiEvents),
    Mistakes("mistakes", "Mistakes", Icons.Default.ErrorOutline),
    History("history", "History", Icons.Default.History),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(
    students: List<Student>,
    selectedStudent: Student?,
    onStudentSelected: (Student) -> Unit,
    onLogout: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val navController = rememberNavController()
    val networkMonitor: NetworkMonitor = koinInject()
    val isOnline by networkMonitor.isOnline.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
        return
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        StudentSelector(
                            students = students,
                            selectedStudent = selectedStudent,
                            onStudentSelected = onStudentSelected,
                        )
                    },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                        IconButton(onClick = onLogout) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
                        }
                    },
                )
                if (!isOnline) {
                    OfflineBanner()
                }
            }
        },
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                Screen.entries.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Solve.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Solve.route) {
                SolveScreen(studentId = selectedStudent?.id, studentGrade = selectedStudent?.grade)
            }
            composable(Screen.Knowledge.route) {
                KnowledgeScreen(studentId = selectedStudent?.id)
            }
            composable(Screen.Growth.route) {
                GrowthScreen(studentId = selectedStudent?.id)
            }
            composable(Screen.Mistakes.route) {
                MistakesScreen(studentId = selectedStudent?.id)
            }
            composable(Screen.History.route) {
                HistoryScreen(studentId = selectedStudent?.id)
            }
        }
    }
}
