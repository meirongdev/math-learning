package com.mathlearning.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mathlearning.shared.api.MathApi
import com.mathlearning.shared.model.SolveRequest
import com.mathlearning.web.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*

@Composable
fun App() {
    AppTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
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
    var elapsedSeconds by remember { mutableStateOf(0) }
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
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header
        Text(
            text = "SG Math Tutor",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "AI-powered Singapore PSLE Math tutor using CPA method",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Input Card
        Card(
            modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Enter Your Math Question",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
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
                    maxLines = 6,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Grade selector
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
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
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

                        // Elapsed-time ticker
                        scope.launch {
                            elapsedSeconds = 0
                            while (isLoading) {
                                delay(1_000)
                                elapsedSeconds++
                            }
                        }

                        scope.launch {
                            try {
                                val request = SolveRequest(
                                    question = question,
                                    grade = selectedGrade.removePrefix("P").toInt(),
                                )
                                val result = withTimeout(300_000L) {
                                    api.solve(request)
                                }
                                parentGuide = result.parentGuide ?: ""
                                childScript = result.childScript ?: ""
                                barModel = result.barModelJson ?: ""
                                knowledgeTags = result.knowledgeTags?.joinToString(", ") ?: ""
                                hasResults = true
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

        // Error Alert
        if (errorMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                }
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
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stageMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${elapsedSeconds}s elapsed · first response may take ~45s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
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
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Parent Guide
            if (parentGuide.isNotBlank()) {
                ResultSection(
                    title = "Parent Guide",
                    content = parentGuide,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Child Script
            if (childScript.isNotBlank()) {
                ResultSection(
                    title = "Child's Learning Script",
                    content = childScript,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Bar Model
            if (barModel.isNotBlank() && barModel != "{}") {
                BarModelCard(barModel)
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // Empty state
        if (!hasResults && !isLoading && errorMessage == null) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Enter a math question above to get started",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Supports P1-P6 Singapore Math (PSLE 2026 syllabus)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Footer
        HorizontalDivider(
            modifier = Modifier.widthIn(max = 600.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Powered by AI - Aligned with PSLE 2026 Syllabus",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ResultSection(
    title: String,
    content: String,
    containerColor: Color,
    contentColor: Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
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
    val parsed = try {
        json.parseToJsonElement(barModelJson).jsonObject
    } catch (_: Exception) {
        return // invalid JSON, don't render
    }

    val title = parsed["title"]?.jsonPrimitive?.contentOrNull ?: "Bar Model"
    val bars = parsed["bars"]?.jsonArray ?: return

    Card(
        modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Find max total value across all bars for proportional scaling
            val maxTotal = bars.maxOfOrNull { bar ->
                bar.jsonObject["segments"]?.jsonArray?.sumOf {
                    it.jsonObject["value"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                } ?: 0.0
            } ?: 1.0

            bars.forEach { bar ->
                val barObj = bar.jsonObject
                val label = barObj["label"]?.jsonPrimitive?.contentOrNull ?: ""
                val segments = barObj["segments"]?.jsonArray ?: return@forEach

                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Spacer(modifier = Modifier.height(4.dp))

                val barTotal = segments.sumOf {
                    it.jsonObject["value"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                }

                if (segments.isNotEmpty() && barTotal > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(6.dp)),
                    ) {
                        segments.forEach { segment ->
                            val segObj = segment.jsonObject
                            val value = segObj["value"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                            val color = parseHexColor(
                                segObj["color"]?.jsonPrimitive?.contentOrNull ?: "#4CAF50",
                            )
                            val segLabel = segObj["label"]?.jsonPrimitive?.contentOrNull ?: ""
                            val fraction = (value / maxTotal).toFloat()

                            Box(
                                modifier = Modifier
                                    .weight(fraction)
                                    .fillMaxHeight()
                                    .background(color)
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (segLabel.isNotBlank()) "$segLabel (${ value.toInt()})" else "${value.toInt()}",
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

            // Annotations
            val annotations = parsed["annotations"]?.jsonArray
            if (annotations != null && annotations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                annotations.forEach { ann ->
                    val text = when {
                        ann is JsonPrimitive -> ann.contentOrNull
                        ann is JsonObject -> ann["text"]?.jsonPrimitive?.contentOrNull
                            ?: ann["content"]?.jsonPrimitive?.contentOrNull
                        else -> null
                    }
                    if (text != null) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
        }
    }
}
