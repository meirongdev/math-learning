package com.mathlearning.android.ui.growth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mathlearning.android.ui.UiState
import org.koin.androidx.compose.koinViewModel

@Composable
fun GrowthScreen(studentId: String?) {
    val viewModel: GrowthViewModel = koinViewModel()
    val achievements by viewModel.achievements.collectAsState()
    val learningPath by viewModel.learningPath.collectAsState()

    LaunchedEffect(studentId) { studentId?.let { viewModel.load(it) } }

    if (studentId == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Select a student to view growth")
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Learning path section
        item {
            Text("Learning Path", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
        }
        item {
            when (val lp = learningPath) {
                is UiState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                is UiState.Success -> Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(lp.data.summary, style = MaterialTheme.typography.bodyLarge)
                        Text(lp.data.reason, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("Focus: ${lp.data.focusNode.nameEn} (${lp.data.focusNode.nameZh})")
                    }
                }
                is UiState.Error -> Text(lp.message, color = MaterialTheme.colorScheme.error)
                null -> {}
            }
            Spacer(Modifier.height(16.dp))
        }

        // Achievements section
        item {
            Text("Achievements", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
        }
        when (val ach = achievements) {
            is UiState.Loading -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            is UiState.Error -> item { Text(ach.message, color = MaterialTheme.colorScheme.error) }
            is UiState.Success -> items(ach.data) { achievement ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(achievement.icon, style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(achievement.title, style = MaterialTheme.typography.titleSmall)
                            Text(achievement.description, style = MaterialTheme.typography.bodySmall)
                        }
                        if (achievement.unlocked) {
                            Text("Unlocked", color = MaterialTheme.colorScheme.primary)
                        } else {
                            Text("${achievement.currentValue}/${achievement.targetValue}",
                                color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}
