package com.mathlearning.android.ui.solve

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mathlearning.android.ui.UiState
import com.mathlearning.android.ui.components.BarModelCard
import com.mathlearning.android.ui.components.StarRating
import com.mathlearning.android.ocr.CameraScreen
import com.mathlearning.android.ocr.OcrManager
import com.mathlearning.android.ocr.OcrState
import com.mathlearning.shared.model.ExplanationMode
import com.mathlearning.shared.model.SolveResponse
import org.koin.androidx.compose.koinViewModel

@Composable
fun SolveScreen(studentId: String?, studentGrade: Int?) {
    val viewModel: SolveViewModel = koinViewModel()
    val solveState by viewModel.solveState.collectAsState()

    var question by remember { mutableStateOf("") }
    var grade by remember { mutableIntStateOf(studentGrade ?: 1) }
    var mode by remember { mutableStateOf(ExplanationMode.ORIGINAL) }

    val ocrManager = remember { OcrManager() }
    val ocrState by ocrManager.state.collectAsState()

    // Handle OCR result
    LaunchedEffect(ocrState) {
        if (ocrState is OcrState.Success) {
            question = (ocrState as OcrState.Success).text
            ocrManager.dismiss()
        }
    }

    if (ocrState is OcrState.Preview || ocrState is OcrState.Processing) {
        CameraScreen(ocrManager = ocrManager, onDismiss = { ocrManager.dismiss() })
        return
    }

    // Update grade when student changes
    LaunchedEffect(studentGrade) {
        studentGrade?.let { grade = it }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // Input section
        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            label = { Text("Enter math question") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5,
            trailingIcon = {
                IconButton(onClick = { ocrManager.showPreview() }) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "OCR")
                }
            },
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Grade selector
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..6).forEach { g ->
                FilterChip(
                    selected = grade == g,
                    onClick = { grade = g },
                    label = { Text("P$g") },
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Mode selector
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExplanationMode.entries.forEach { m ->
                FilterChip(
                    selected = mode == m,
                    onClick = { mode = m },
                    label = { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Solve button
        Button(
            onClick = { viewModel.solve(question, grade, studentId, mode) },
            enabled = question.isNotBlank() && solveState !is UiState.Loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Solve")
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Results
        when (val state = solveState) {
            is UiState.Loading -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                Text("Solving... this may take a moment")
            }
            is UiState.Success -> SolveResultCards(state.data)
            is UiState.Error -> {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        text = state.message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { viewModel.solve(question, grade, studentId, mode) }) {
                    Text("Retry")
                }
            }
            null -> {} // Initial state
        }
    }
}

@Composable
private fun SolveResultCards(result: SolveResponse) {
    var rating by remember { mutableIntStateOf(0) }

    result.parentGuide?.let { guide ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Parent Guide", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(guide)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    result.childScript?.let { script ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Child Script", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(script)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    BarModelCard(result.barModelJson)
    Spacer(modifier = Modifier.height(8.dp))

    result.knowledgeTags?.takeIf { it.isNotEmpty() }?.let { tags ->
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            tags.forEach { tag ->
                AssistChip(onClick = {}, label = { Text(tag) })
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    // Star rating
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Rate this explanation", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            StarRating(rating = rating, onRatingChange = { rating = it })
        }
    }
}
