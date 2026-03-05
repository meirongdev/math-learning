package com.mathlearning.web

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mathlearning.shared.api.MathApi
import com.mathlearning.shared.model.SolveRequest
import com.mathlearning.web.theme.AppTheme
import kotlinx.coroutines.launch

@Composable
fun App() {
    AppTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            MathTutorScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MathTutorScreen() {
    val api = remember { MathApi() }
    val scope = rememberCoroutineScope()

    var question by remember { mutableStateOf("") }
    var selectedGrade by remember { mutableStateOf("P3") }
    var gradeExpanded by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Result sections
    var parentGuide by remember { mutableStateOf("") }
    var childScript by remember { mutableStateOf("") }
    var barModel by remember { mutableStateOf("") }
    var knowledgeTags by remember { mutableStateOf("") }
    var hasResults by remember { mutableStateOf(false) }

    val grades = listOf("P1", "P2", "P3", "P4", "P5", "P6")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header
        Text(
            text = "SG Math Tutor",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "AI-powered Singapore PSLE Math tutor using CPA method",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Input Card
        Card(
            modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Enter Your Math Question",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Question input
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Math Question") },
                    placeholder = { Text("e.g. Amy has 24 sweets. She gives 1/3 to Bob. How many does Amy have left?") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Grade selector
                ExposedDropdownMenuBox(
                    expanded = gradeExpanded,
                    onExpandedChange = { gradeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedGrade,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Grade Level") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = gradeExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = gradeExpanded,
                        onDismissRequest = { gradeExpanded = false }
                    ) {
                        grades.forEach { grade ->
                            DropdownMenuItem(
                                text = { Text(grade) },
                                onClick = {
                                    selectedGrade = grade
                                    gradeExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Solve button
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

                        scope.launch {
                            try {
                                val request = SolveRequest(
                                    question = question,
                                    grade = selectedGrade.removePrefix("P").toInt()
                                )
                                api.solveStream(request).collect { event ->
                                    when (event.type) {
                                        "parent_guide" -> parentGuide = event.content
                                        "child_script" -> childScript = event.content
                                        "bar_model" -> barModel = event.content
                                        "knowledge_tags" -> knowledgeTags = event.content
                                        "error" -> errorMessage = event.content
                                    }
                                }
                                if (errorMessage == null) {
                                    hasResults = true
                                }
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "An unexpected error occurred"
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = question.isNotBlank() && !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isLoading) "Solving..." else "Solve")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Error Alert
        if (errorMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Loading indicator
        if (isLoading) {
            Card(
                modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "AI is solving your question...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "This may take a moment for new questions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Results
        if (hasResults) {
            // Knowledge Tags
            if (knowledgeTags.isNotBlank()) {
                ResultSection(
                    title = "Knowledge Points",
                    content = knowledgeTags,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Parent Guide
            if (parentGuide.isNotBlank()) {
                ResultSection(
                    title = "Parent Guide",
                    content = parentGuide,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Child Script
            if (childScript.isNotBlank()) {
                ResultSection(
                    title = "Child's Learning Script",
                    content = childScript,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Bar Model
            if (barModel.isNotBlank()) {
                ResultSection(
                    title = "Bar Model",
                    content = barModel,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // Empty state
        if (!hasResults && !isLoading && errorMessage == null) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Enter a math question above to get started",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Supports P1-P6 Singapore Math (PSLE 2026 syllabus)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Footer
        HorizontalDivider(
            modifier = Modifier.widthIn(max = 600.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Powered by AI - Aligned with PSLE 2026 Syllabus",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ResultSection(
    title: String,
    content: String,
    containerColor: Color,
    contentColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
        }
    }
}
