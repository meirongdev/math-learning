package com.mathlearning.web

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    var parentGuide by remember { mutableStateOf("") }
    var childScript by remember { mutableStateOf("") }

    val grades = listOf("P1", "P2", "P3", "P4", "P5", "P6")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title
        Text(
            text = "SG Math Tutor",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Singapore Math problem solver for Primary students",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Input card
        Card(
            modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Question input
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Math Question") },
                    placeholder = { Text("e.g. Amy has 24 sweets. She gives 1/3 to Bob...") },
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
                        modifier = Modifier.menuAnchor().fillMaxWidth()
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

                Spacer(modifier = Modifier.height(16.dp))

                // Solve button
                Button(
                    onClick = {
                        if (question.isBlank()) return@Button
                        isLoading = true
                        parentGuide = ""
                        childScript = ""

                        scope.launch {
                            try {
                                val request = SolveRequest(
                                    question = question,
                                    grade = selectedGrade
                                )
                                api.solveStream(request).collect { event ->
                                    when (event.type) {
                                        "parent_guide" -> parentGuide += event.content
                                        "child_script" -> childScript += event.content
                                        else -> {} // ignore unknown types
                                    }
                                }
                            } catch (e: Exception) {
                                parentGuide = "Error: ${e.message}"
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
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

        // Results
        if (parentGuide.isNotEmpty() || childScript.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (parentGuide.isNotEmpty()) {
                        Text(
                            text = "Parent Guide",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = parentGuide,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    if (parentGuide.isNotEmpty() && childScript.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    }

                    if (childScript.isNotEmpty()) {
                        Text(
                            text = "Child Script",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = childScript,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
