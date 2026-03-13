package com.mathlearning.android.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mathlearning.android.ui.UiState
import com.mathlearning.android.ui.components.StarRating
import com.mathlearning.shared.model.RecordResponse
import org.koin.androidx.compose.koinViewModel

@Composable
fun HistoryScreen(studentId: String?) {
    val viewModel: HistoryViewModel = koinViewModel()
    val records by viewModel.records.collectAsState()

    LaunchedEffect(studentId) { studentId?.let { viewModel.loadRecords(it) } }

    if (studentId == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Select a student to view history")
        }
        return
    }

    when (val state = records) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is UiState.Error -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.message)
                Button(onClick = { viewModel.loadRecords(studentId) }) { Text("Retry") }
            }
        }
        is UiState.Success -> {
            val data = state.data
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                items(data.records) { record ->
                    HistoryRecordCard(record, onRate = { rating ->
                        viewModel.rateRecord(record.id, rating)
                    })
                }
                // Pagination
                item {
                    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center) {
                        if (data.page > 0) {
                            TextButton(onClick = { viewModel.loadRecords(studentId, data.page - 1) }) {
                                Text("Previous")
                            }
                        }
                        Text("Page ${data.page + 1} of ${data.totalPages}",
                            modifier = Modifier.padding(horizontal = 16.dp).align(Alignment.CenterVertically))
                        if (data.page + 1 < data.totalPages) {
                            TextButton(onClick = { viewModel.loadRecords(studentId, data.page + 1) }) {
                                Text("Next")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRecordCard(record: RecordResponse, onRate: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var rating by remember { mutableIntStateOf(record.rating ?: 0) }

    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { expanded = !expanded }) {
        Column(Modifier.padding(12.dp)) {
            Text(record.questionText.take(100), style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(record.createdAt.take(10), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.weight(1f))
                StarRating(rating = rating, onRatingChange = { rating = it; onRate(it) })
            }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 8.dp)) {
                    record.parentGuide?.let {
                        Text("Parent Guide", style = MaterialTheme.typography.titleSmall)
                        Text(it, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                    }
                    record.childScript?.let {
                        Text("Child Script", style = MaterialTheme.typography.titleSmall)
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
