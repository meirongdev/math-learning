package com.mathlearning.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.mathlearning.android.navigation.MainNavigation
import com.mathlearning.android.ui.auth.AuthScreen
import com.mathlearning.android.ui.theme.MathLearningTheme
import com.mathlearning.shared.api.MathApi
import com.mathlearning.shared.model.Student
import com.mathlearning.shared.storage.clearToken
import com.mathlearning.shared.storage.loadExpiresAt
import com.mathlearning.shared.storage.loadToken
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val api: MathApi by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MathLearningTheme {
                var isLoggedIn by remember { mutableStateOf(checkTokenValid()) }
                var students by remember { mutableStateOf<List<Student>>(emptyList()) }
                var selectedStudent by remember { mutableStateOf<Student?>(null) }

                if (isLoggedIn) {
                    LaunchedEffect(Unit) {
                        try {
                            api.token = loadToken()
                            students = api.listStudents()
                            if (selectedStudent == null && students.isNotEmpty()) {
                                selectedStudent = students.first()
                            }
                        } catch (e: Exception) {
                            isLoggedIn = false
                        }
                    }
                    MainNavigation(
                        students = students,
                        selectedStudent = selectedStudent,
                        onStudentSelected = { selectedStudent = it },
                        onLogout = {
                            clearToken()
                            api.token = null
                            isLoggedIn = false
                        },
                        onOpenSettings = {},
                    )
                } else {
                    AuthScreen(
                        onLoginSuccess = {
                            isLoggedIn = true
                        },
                    )
                }
            }
        }
    }

    private fun checkTokenValid(): Boolean {
        val token = loadToken() ?: return false
        val expiresAt = loadExpiresAt() ?: return false
        return try {
            // Simple check: expiresAt is an ISO string, compare with current time
            val expiryMillis = java.time.Instant.parse(expiresAt).toEpochMilli()
            System.currentTimeMillis() < expiryMillis
        } catch (e: Exception) {
            false
        }
    }
}
