package com.mathlearning.android.ui.mistakes

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
import org.koin.androidx.compose.koinViewModel

@Composable
fun MistakesScreen(studentId: String?) {
    val viewModel: MistakesViewModel = koinViewModel()
    val mistakes by viewModel.mistakes.collectAsState()

    LaunchedEffect(studentId) { viewModel.loadMistakes(studentId) }

    when (val state = mistakes) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is UiState.Error -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.message)
                Button(onClick = { viewModel.loadMistakes(studentId) }) { Text("Retry") }
            }
        }
        is UiState.Success -> {
            if (state.data.records.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No mistakes found. Keep up the good work!")
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                    items(state.data.records) { mistake ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(mistake.questionText.take(120), style = MaterialTheme.typography.bodyMedium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(mistake.createdAt.take(10), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline)
                                    Spacer(Modifier.weight(1f))
                                    StarRating(rating = mistake.rating ?: 0)
                                }
                                mistake.knowledgeTags?.let { tags ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(top = 4.dp)) {
                                        tags.forEach { tag ->
                                            AssistChip(onClick = {}, label = { Text(tag) })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
