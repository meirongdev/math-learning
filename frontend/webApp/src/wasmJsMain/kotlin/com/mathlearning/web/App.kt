package com.mathlearning.web

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mathlearning.shared.api.MathApi
import com.mathlearning.shared.api.UnauthorizedException
import com.mathlearning.shared.model.*
import com.mathlearning.shared.storage.clearToken
import com.mathlearning.shared.storage.loadExpiresAt
import com.mathlearning.shared.storage.loadToken
import com.mathlearning.web.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

enum class Screen { Solve, Knowledge, History }

@Serializable
private data class OcrParseResult(
    val fileName: String = "",
    val text: String = "",
)

@Composable
fun App() {
    AppTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val api = remember { MathApi() }

            var isLoggedIn by remember {
                val stored = loadToken()
                val expiresAt = loadExpiresAt()
                val valid = stored != null &&
                    expiresAt != null &&
                    try {
                        Instant.parse(expiresAt) > Clock.System.now()
                    } catch (_: Exception) {
                        false
                    }
                if (valid) api.token = stored
                mutableStateOf(valid)
            }

            if (isLoggedIn) {
                MainApp(api) {
                    clearToken()
                    api.token = null
                    isLoggedIn = false
                }
            } else {
                AuthScreen(api) { isLoggedIn = true }
            }
        }
    }
}

@Composable
fun MainApp(api: MathApi, onLogout: () -> Unit) {
    var currentScreen by remember { mutableStateOf(Screen.Solve) }
    var students by remember { mutableStateOf<List<Student>>(emptyList()) }
    var selectedStudent by remember { mutableStateOf<Student?>(null) }

    LaunchedEffect(Unit) {
        try {
            students = api.listStudents()
            if (students.isNotEmpty()) selectedStudent = students.first()
        } catch (_: UnauthorizedException) {
            onLogout()
        } catch (_: Exception) {}
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar with navigation
        TopBar(
            currentScreen = currentScreen,
            onScreenChange = { currentScreen = it },
            onLogout = onLogout,
        )

        // Content
        when (currentScreen) {
            Screen.Solve -> SolveScreen(
                api = api,
                students = students,
                selectedStudent = selectedStudent,
                onStudentSelected = { selectedStudent = it },
                onStudentsChanged = { newList ->
                    students = newList
                    if (selectedStudent != null && newList.none { it.id == selectedStudent!!.id }) {
                        selectedStudent = newList.firstOrNull()
                    }
                },
                onLogout = onLogout,
            )
            Screen.Knowledge -> KnowledgeScreen(
                api = api,
                selectedStudent = selectedStudent,
                onLogout = onLogout,
            )
            Screen.History -> HistoryScreen(
                api = api,
                students = students,
                selectedStudent = selectedStudent,
                onStudentSelected = { selectedStudent = it },
                onLogout = onLogout,
            )
        }
    }
}

@Composable
fun TopBar(currentScreen: Screen, onScreenChange: (Screen) -> Unit, onLogout: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "SG Math Tutor",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedButton(onClick = onLogout) {
                    Text("Logout")
                }
            }
            // Navigation tabs
            Row(modifier = Modifier.fillMaxWidth()) {
                Screen.entries.forEach { screen ->
                    val label = when (screen) {
                        Screen.Solve -> "Solve"
                        Screen.Knowledge -> "Knowledge"
                        Screen.History -> "History"
                    }
                    val selected = currentScreen == screen
                    TextButton(
                        onClick = { onScreenChange(screen) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        ),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                            if (selected) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .width(40.dp)
                                        .height(2.dp)
                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp)),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// Auth Screen
// ============================================================================

@Composable
fun AuthScreen(api: MathApi, onLoginSuccess: () -> Unit) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var isLogin by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    fun performAuth() {
        if (email.isBlank() || password.isBlank() || isLoading) return
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                if (isLogin) {
                    api.login(email, password)
                    onLoginSuccess()
                } else {
                    val success = api.register(email, password)
                    if (success) {
                        api.login(email, password)
                        onLoginSuccess()
                    } else {
                        errorMessage = "Registration failed"
                    }
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "An error occurred"
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "SG Math Tutor",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "AI-powered Singapore PSLE Math tutor",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = if (isLogin) "Login" else "Register",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { performAuth() }),
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { performAuth() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = email.isNotBlank() && password.isNotBlank() && !isLoading,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isLogin) "Login" else "Register")
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = {
                        isLogin = !isLogin
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isLogin) "Don't have an account? Register" else "Already have an account? Login")
                }
            }
        }
    }
}

// ============================================================================
// Student Management Dialog
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentManagementDialog(
    api: MathApi,
    students: List<Student>,
    onStudentsChanged: (List<Student>) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var newName by remember { mutableStateOf("") }
    var newGrade by remember { mutableStateOf("P3") }
    var gradeExpanded by remember { mutableStateOf(false) }
    var isAdding by remember { mutableStateOf(false) }
    var dialogError by remember { mutableStateOf<String?>(null) }
    val grades = listOf("P1", "P2", "P3", "P4", "P5", "P6")

    fun performAddStudent() {
        if (newName.isBlank() || isAdding) return
        isAdding = true
        dialogError = null
        scope.launch {
            try {
                api.createStudent(newName, newGrade.removePrefix("P").toInt())
                val updated = api.listStudents()
                onStudentsChanged(updated)
                newName = ""
            } catch (e: Exception) {
                dialogError = "Add failed: ${e.message}"
            } finally {
                isAdding = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Students") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Student list
                students.forEach { student ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${student.name} (P${student.grade})", modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                dialogError = null
                                scope.launch {
                                    try {
                                        api.deleteStudent(student.id)
                                        val updated = api.listStudents()
                                        onStudentsChanged(updated)
                                    } catch (e: Exception) {
                                        dialogError = "Delete failed: ${e.message}"
                                    }
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) {
                            Text("Delete")
                        }
                    }
                }

                if (students.isEmpty()) {
                    Text(
                        "No students yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text("Add Student", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { performAddStudent() }),
                )
                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = gradeExpanded,
                    onExpandedChange = { gradeExpanded = it },
                ) {
                    OutlinedTextField(
                        value = newGrade,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Grade") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = gradeExpanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = gradeExpanded,
                        onDismissRequest = { gradeExpanded = false },
                    ) {
                        grades.forEach { g ->
                            DropdownMenuItem(
                                text = { Text(g) },
                                onClick = {
                                    newGrade = g
                                    gradeExpanded = false
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { performAddStudent() },
                    enabled = newName.isNotBlank() && !isAdding,
                ) {
                    Text("Add")
                }

                if (dialogError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = dialogError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

// ============================================================================
// Solve Screen
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SolveScreen(
    api: MathApi,
    students: List<Student>,
    selectedStudent: Student?,
    onStudentSelected: (Student?) -> Unit,
    onStudentsChanged: (List<Student>) -> Unit,
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var question by remember { mutableStateOf("") }
    var selectedGrade by remember { mutableStateOf("P3") }
    var gradeExpanded by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var studentExpanded by remember { mutableStateOf(false) }
    var showManageDialog by remember { mutableStateOf(false) }

    // Result state
    var parentGuide by remember { mutableStateOf("") }
    var childScript by remember { mutableStateOf("") }
    var barModel by remember { mutableStateOf("") }
    var knowledgeTags by remember { mutableStateOf("") }
    var hasResults by remember { mutableStateOf(false) }
    var lastRecordId by remember { mutableStateOf<String?>(null) }
    var currentRating by remember { mutableStateOf<Int?>(null) }
    var isSavingRating by remember { mutableStateOf(false) }
    var isOcrLoading by remember { mutableStateOf(false) }
    var ocrSource by remember { mutableStateOf<String?>(null) }
    var ocrStatus by remember { mutableStateOf<String?>(null) }
    var selectedMode by remember { mutableStateOf(ExplanationMode.ORIGINAL) }

    val json = remember {
        Json {
            ignoreUnknownKeys = true
        }
    }

    val grades = listOf("P1", "P2", "P3", "P4", "P5", "P6")

    // Timer: update elapsedSeconds while `isLoading` using a composable-scoped coroutine
    LaunchedEffect(isLoading) {
        if (isLoading) {
            elapsedSeconds = 0
            while (isActive && isLoading) {
                delay(1_000)
                elapsedSeconds++
            }
        }
    }

    if (showManageDialog) {
        StudentManagementDialog(
            api = api,
            students = students,
            onStudentsChanged = onStudentsChanged,
            onDismiss = { showManageDialog = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Student card
        Card(
            modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Student", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { showManageDialog = true }) {
                        Text("Manage")
                    }
                }

                if (students.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    ExposedDropdownMenuBox(
                        expanded = studentExpanded,
                        onExpandedChange = { studentExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = selectedStudent?.let { "${it.name} (P${it.grade})" } ?: "Select a student",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = studentExpanded) },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = studentExpanded,
                            onDismissRequest = { studentExpanded = false },
                        ) {
                            students.forEach { student ->
                                DropdownMenuItem(
                                    text = { Text("${student.name} (P${student.grade})") },
                                    onClick = {
                                        onStudentSelected(student)
                                        studentExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Input card
        Card(
            modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Enter Your Math Question", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Math Question") },
                    placeholder = { Text("e.g. Amy has 24 sweets... / 小明有24颗糖，给了Bob三分之一，还剩多少？") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            isOcrLoading = true
                            ocrStatus = null
                            scope.launch {
                                try {
                                    val result = runBrowserOcr(json)
                                    if (result.cancelled) {
                                        ocrStatus = "OCR cancelled. You can upload another image anytime."
                                        ocrSource = null
                                        return@launch
                                    }
                                    ocrSource = result.fileName.ifBlank { null }
                                    if (result.text.isBlank()) {
                                        ocrStatus = "No text was detected. Try a clearer photo with better lighting."
                                    } else {
                                        question = result.text
                                        ocrStatus = "OCR completed. Review the extracted text before solving."
                                    }
                                } catch (e: Exception) {
                                    ocrStatus = e.message ?: "OCR failed. Please try again."
                                } finally {
                                    isOcrLoading = false
                                }
                            }
                        },
                        enabled = !isOcrLoading && !isLoading,
                    ) {
                        Text(if (isOcrLoading) "Recognizing..." else "Upload Image OCR")
                    }

                    if (ocrSource != null) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("Source: ${ocrSource}") },
                        )
                    }
                }

                if (ocrStatus != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = ocrStatus ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ocrStatus!!.startsWith("OCR completed")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                ExposedDropdownMenuBox(
                    expanded = gradeExpanded,
                    onExpandedChange = { gradeExpanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedGrade,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Grade Level") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = gradeExpanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = gradeExpanded,
                        onDismissRequest = { gradeExpanded = false },
                    ) {
                        grades.forEach { grade ->
                            DropdownMenuItem(
                                text = { Text(grade) },
                                onClick = {
                                    selectedGrade = grade
                                    gradeExpanded = false
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Explanation mode switcher
                Text("Explanation Mode", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExplanationMode.entries.forEach { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode },
                            label = {
                                Text(
                                    when (mode) {
                                        ExplanationMode.ORIGINAL -> "\uD83D\uDCDD Direct"
                                        ExplanationMode.SOCRATIC -> "\uD83E\uDD14 Socratic"
                                    },
                                )
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (question.isBlank()) return@Button
                        isLoading = true
                        errorMessage = null
                        parentGuide = ""
                        childScript = ""
                        barModel = ""
                        knowledgeTags = ""
                        hasResults = false
                        lastRecordId = null
                        currentRating = null
                        isSavingRating = false

                        scope.launch {
                            try {
                                val request = SolveRequest(
                                    question = question,
                                    grade = selectedGrade.removePrefix("P").toInt(),
                                    studentId = selectedStudent?.id,
                                    mode = selectedMode,
                                )
                                val result = withTimeout(300_000L) { api.solve(request) }
                                parentGuide = result.parentGuide ?: ""
                                childScript = result.childScript ?: ""
                                barModel = result.barModelJson ?: ""
                                knowledgeTags = result.knowledgeTags?.joinToString(", ") ?: ""
                                if (selectedStudent != null) {
                                    val newestRecord = api.getRecords(selectedStudent.id, page = 0, size = 1).records.firstOrNull()
                                    lastRecordId = newestRecord?.id
                                    currentRating = newestRecord?.rating
                                }
                                hasResults = true
                            } catch (e: UnauthorizedException) {
                                onLogout()
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "An unexpected error occurred"
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = question.isNotBlank() && !isLoading,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isLoading) "Solving..." else "Solve")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Error
        if (errorMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Text(
                    text = errorMessage ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Loading indicator
        if (isLoading) {
            val stageMessage = when {
                elapsedSeconds < 4 -> "Searching knowledge base..."
                elapsedSeconds < 25 -> "Analyzing the question..."
                else -> "Generating explanations..."
            }
            Card(
                modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stageMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${elapsedSeconds}s elapsed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Results
        if (hasResults) {
            if (knowledgeTags.isNotBlank()) {
                ResultSection("Knowledge Points", knowledgeTags, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (parentGuide.isNotBlank()) {
                ResultSection("Parent Guide", parentGuide, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (childScript.isNotBlank()) {
                ResultSection("Child's Learning Script", childScript, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (barModel.isNotBlank() && barModel != "{}") {
                BarModelCard(barModel)
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Star rating
            StarRatingCard(
                currentRating = currentRating,
                enabled = lastRecordId != null,
                isSaving = isSavingRating,
                helperText = if (lastRecordId == null) "Select a student before solving to save a rating." else null,
            ) { rating ->
                val recordId = lastRecordId ?: return@StarRatingCard
                currentRating = rating
                isSavingRating = true
                scope.launch {
                    try {
                        api.rateRecord(recordId, rating)
                    } catch (_: Exception) {
                        currentRating = null
                    } finally {
                        isSavingRating = false
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Empty state
        if (!hasResults && !isLoading && errorMessage == null) {
            Spacer(modifier = Modifier.height(32.dp))
            Text("Enter a math question above to get started", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Supports P1-P6 Singapore Math (PSLE 2026 syllabus)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ============================================================================
// Star Rating
// ============================================================================

@Composable
fun StarRatingCard(
    currentRating: Int?,
    enabled: Boolean = true,
    isSaving: Boolean = false,
    helperText: String? = null,
    onRate: (Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Rate this explanation", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            RatingSelector(
                currentRating = currentRating,
                enabled = enabled && !isSaving,
                onRate = onRate,
            )
            if (helperText != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(helperText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            if (isSaving) {
                Spacer(modifier = Modifier.height(6.dp))
                Text("Saving rating...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            } else if (currentRating != null && enabled) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Rating saved!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun RatingSelector(currentRating: Int?, enabled: Boolean, onRate: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..5).forEach { star ->
            val filled = currentRating != null && star <= currentRating
            FilledTonalIconButton(
                onClick = { onRate(star) },
                enabled = enabled,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (filled) Color(0xFFFFF3C4) else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (filled) Color(0xFFE0A800) else MaterialTheme.colorScheme.outline,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.outline,
                ),
            ) {
                Icon(
                    imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = "Rate $star out of 5",
                )
            }
        }
    }
}

@Composable
fun RatingBadge(rating: Int, compact: Boolean = false) {
    val safeRating = rating.coerceIn(1, 5)
    Surface(
        color = Color(0xFFFFF3C4),
        contentColor = Color(0xFFE0A800),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, Color(0xFFFFE08A)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = if (compact) 4.dp else 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(safeRating) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    modifier = Modifier.size(if (compact) 14.dp else 16.dp),
                )
            }
            if (safeRating < 5) {
                Text("$safeRating/5", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ============================================================================
// Knowledge Screen
// ============================================================================

@Composable
fun KnowledgeScreen(api: MathApi, selectedStudent: Student?, onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()
    var graph by remember { mutableStateOf<List<KnowledgeNodeResponse>>(emptyList()) }
    var progress by remember { mutableStateOf<Map<String, String>>(emptyMap()) } // code -> masteryLevel
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedStudent) {
        isLoading = true
        try {
            graph = api.getKnowledgeGraph()
            if (selectedStudent != null) {
                val progressList = api.getKnowledgeProgress(selectedStudent.id)
                progress = progressList.associate { it.knowledgeCode to it.masteryLevel }
            }
        } catch (e: UnauthorizedException) {
            onLogout()
        } catch (e: Exception) {
            errorMessage = e.message
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (selectedStudent != null) {
            Text(
                "Knowledge Map for ${selectedStudent.name} (P${selectedStudent.grade})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        } else {
            Text("Knowledge Map", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator()
        } else if (errorMessage != null) {
            Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error)
        } else {
            graph.forEach { node ->
                KnowledgeNodeTree(
                    node = node,
                    progress = progress,
                    level = 0,
                    selectedStudent = selectedStudent,
                    onUpdateMastery = { code, level ->
                        if (selectedStudent != null) {
                            scope.launch {
                                try {
                                    api.updateMastery(selectedStudent.id, code, level)
                                    progress = progress + (code to level)
                                } catch (_: Exception) {}
                            }
                        }
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun KnowledgeNodeTree(
    node: KnowledgeNodeResponse,
    progress: Map<String, String>,
    level: Int,
    selectedStudent: Student?,
    onUpdateMastery: (String, String) -> Unit,
) {
    var expanded by remember { mutableStateOf(level < 1) }
    val mastery = progress[node.code] ?: "UNKNOWN"
    val hasChildren = node.children.isNotEmpty()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .padding(start = (level * 16).dp, top = 2.dp, bottom = 2.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (level == 0) 2.dp else 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (level == 0) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().let { if (hasChildren) it.clickable { expanded = !expanded } else it },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hasChildren) {
                    Text(if (expanded) "\u25BC " else "\u25B6 ", fontSize = 12.sp)
                } else {
                    Spacer(modifier = Modifier.width(16.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        node.nameEn,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (level == 0) FontWeight.Bold else FontWeight.Normal,
                    )
                    Text(
                        "${node.nameZh} (P${node.gradeStart}+)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Mastery badge
                if (!hasChildren && selectedStudent != null) {
                    MasteryBadge(mastery) { newLevel -> onUpdateMastery(node.code, newLevel) }
                }
            }

            if (expanded && hasChildren) {
                node.children.forEach { child ->
                    KnowledgeNodeTree(child, progress, level + 1, selectedStudent, onUpdateMastery)
                }
            }
        }
    }
}

@Composable
fun MasteryBadge(mastery: String, onCycle: (String) -> Unit) {
    val (color, label) = when (mastery) {
        "MASTERED" -> Color(0xFF4CAF50) to "Mastered"
        "FAMILIAR" -> Color(0xFFFFC107) to "Familiar"
        else -> Color(0xFF9E9E9E) to "Unknown"
    }
    val next = when (mastery) {
        "UNKNOWN" -> "FAMILIAR"
        "FAMILIAR" -> "MASTERED"
        else -> "UNKNOWN"
    }

    Surface(
        modifier = Modifier.clickable { onCycle(next) },
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.2f),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ============================================================================
// History Screen
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    api: MathApi,
    students: List<Student>,
    selectedStudent: Student?,
    onStudentSelected: (Student?) -> Unit,
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var records by remember { mutableStateOf<List<RecordResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var studentExpanded by remember { mutableStateOf(false) }
    var expandedRecordId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedStudent) {
        if (selectedStudent == null) {
            records = emptyList()
            return@LaunchedEffect
        }
        isLoading = true
        errorMessage = null
        try {
            val result = api.getRecords(selectedStudent.id)
            records = result.records
        } catch (e: UnauthorizedException) {
            onLogout()
        } catch (e: Exception) {
            errorMessage = e.message
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Solve History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(12.dp))

        // Student filter
        if (students.isNotEmpty()) {
            ExposedDropdownMenuBox(
                expanded = studentExpanded,
                onExpandedChange = { studentExpanded = it },
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = selectedStudent?.let { "${it.name} (P${it.grade})" } ?: "Select a student",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Filter by student") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = studentExpanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = studentExpanded,
                    onDismissRequest = { studentExpanded = false },
                ) {
                    students.forEach { student ->
                        DropdownMenuItem(
                            text = { Text("${student.name} (P${student.grade})") },
                            onClick = {
                                onStudentSelected(student)
                                studentExpanded = false
                            },
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator()
        } else if (errorMessage != null) {
            Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error)
        } else if (records.isEmpty()) {
            Text("No solve records yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            records.forEach { record ->
                val isExpanded = expandedRecordId == record.id
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp)
                        .padding(vertical = 4.dp)
                        .clickable { expandedRecordId = if (isExpanded) null else record.id },
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                record.questionText.take(80) + if (record.questionText.length > 80) "..." else "",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                                maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Knowledge tags
                            record.knowledgeTags?.take(3)?.forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        tag,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            // Stars
                            val ratingVal = record.rating
                            if (ratingVal != null) {
                                RatingBadge(ratingVal, compact = true)
                            }
                            Text(
                                record.createdAt.take(19).replace("T", " "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }

                        // Expanded content
                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))

                            val guide = record.parentGuide
                            if (!guide.isNullOrBlank()) {
                                Text("Parent Guide", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text(guide, style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            val script = record.childScript
                            if (!script.isNullOrBlank()) {
                                Text("Child Script", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text(script, style = MaterialTheme.typography.bodySmall)
                            }

                            // Rating inline
                            Spacer(modifier = Modifier.height(8.dp))
                            var localRating by remember(record.id) { mutableStateOf(record.rating) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Rate: ", style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.width(8.dp))
                                RatingSelector(currentRating = localRating, enabled = true) { star ->
                                    localRating = star
                                    scope.launch {
									try {
										api.rateRecord(record.id, star)
									} catch (_: Exception) {}
								}
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ============================================================================
// Shared Components
// ============================================================================

@Composable
fun ResultSection(title: String, content: String, containerColor: Color, contentColor: Color) {
    Card(
        modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = contentColor)
            Spacer(modifier = Modifier.height(8.dp))
            Text(content, style = MaterialTheme.typography.bodyMedium, color = contentColor)
        }
    }
}

private fun parseHexColor(hex: String): Color {
    val clean = hex.removePrefix("#")
    return try {
        Color(("FF$clean").toLong(16))
    } catch (_: Exception) {
        Color(0xFF4CAF50)
    }
}

@Composable
fun BarModelCard(barModelJson: String) {
    val json = Json { ignoreUnknownKeys = true }
    val warnings = mutableListOf<String>()

    val parsed = try {
        json.parseToJsonElement(barModelJson).jsonObject
    } catch (e: Exception) {
        warnings.add("JSON parse failed: ${e.message}")
        null
    }

    if (parsed == null) {
        println("BarModelCard debug: JSON parse failed: $barModelJson")
        Card(
            modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Bar Model (invalid JSON)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(modifier = Modifier.height(8.dp))
                Text(barModelJson.take(500), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                if (warnings.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Debug:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    warnings.forEach { w -> Text("- $w", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        return
    }

    val title = parsed["title"]?.jsonPrimitive?.contentOrNull ?: "Bar Model"
    val barsJson = parsed["bars"]
    val bars = when (barsJson) {
        is JsonArray -> barsJson
        else -> {
            warnings.add("'bars' is missing or not an array")
            JsonArray(emptyList())
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(modifier = Modifier.height(12.dp))

            val maxTotal = try {
                bars.map { bar ->
                    try {
                        bar.jsonObject["segments"]?.jsonArray?.sumOf {
                            it.jsonObject["value"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                        } ?: 0.0
                    } catch (e: Exception) {
                        warnings.add("Failed to compute bar total: ${e.message}")
                        0.0
                    }
                }.maxOrNull() ?: 1.0
            } catch (e: Exception) {
                warnings.add("Failed to compute maxTotal: ${e.message}")
                1.0
            }

            val safeMax = if (maxTotal <= 0.0) {
                warnings.add("Computed maxTotal <= 0. Falling back to 1.0")
                1.0
            } else {
                maxTotal
            }

            // Define internal data structure for rendering
            data class SegmentModel(val label: String, val value: Double, val color: Color)
            data class BarModel(val label: String, val segments: List<SegmentModel>, val barTotal: Double)

            val parsedBars = bars.mapNotNull { bar ->
                try {
                    val barObj = bar.jsonObject
                    val label = barObj["label"]?.jsonPrimitive?.contentOrNull ?: ""
                    val segmentsArray = barObj["segments"]?.jsonArray
                    if (segmentsArray == null || segmentsArray.isEmpty()) {
                        warnings.add("Bar '$label' has no segments")
                        null
                    } else {
                        val segments = segmentsArray.mapNotNull { segment ->
                            try {
                                val segObj = segment.jsonObject
                                val value = segObj["value"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                                val colorRaw = segObj["color"]?.jsonPrimitive?.contentOrNull ?: "#4CAF50"
                                val color = try {
                                    parseHexColor(colorRaw)
                                } catch (e: Exception) {
                                    warnings.add("Invalid color '$colorRaw', using fallback")
                                    parseHexColor("#4CAF50")
                                }
                                val segLabel = segObj["label"]?.jsonPrimitive?.contentOrNull ?: ""
                                SegmentModel(segLabel, value, color)
                            } catch (e: Exception) {
                                warnings.add("Failed to parse a segment: ${e.message}")
                                null
                            }
                        }
                        val barTotal = segments.sumOf { it.value }
                        if (segments.isEmpty() || barTotal <= 0) {
                            null
                        } else {
                            BarModel(label, segments, barTotal)
                        }
                    }
                } catch (e: Exception) {
                    warnings.add("Failed to parse bar: ${e.message}")
                    null
                }
            }

            parsedBars.forEach { barModel ->
                Text(barModel.label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(modifier = Modifier.height(4.dp))

                Row(modifier = Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(6.dp))) {
                    barModel.segments.forEach { segment ->
                        val fraction = (segment.value / safeMax).toFloat().coerceAtLeast(0.0f)
                        if (fraction > 0) {
                            Box(
                                modifier = Modifier.weight(fraction).fillMaxHeight().background(segment.color).padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (segment.label.isNotBlank()) "${segment.label} (${segment.value.toInt()})" else "${segment.value.toInt()}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            val annotations = parsed["annotations"]?.jsonArray
            if (annotations != null && annotations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                annotations.forEach { ann ->
                    val text = when {
                        ann is JsonPrimitive -> ann.contentOrNull
                        ann is JsonObject -> ann["text"]?.jsonPrimitive?.contentOrNull ?: ann["content"]?.jsonPrimitive?.contentOrNull
                        else -> null
                    }
                    if (text != null) {
                        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }

            if (warnings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Debug info:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                warnings.forEach { w -> Text("- $w", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                println("BarModelCard debug: ${warnings.joinToString("; ")}")
            }
        }
    }
}
