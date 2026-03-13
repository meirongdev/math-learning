package com.mathlearning.android.ui.knowledge

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mathlearning.android.ui.UiState
import com.mathlearning.android.ui.components.MasteryBadge
import com.mathlearning.shared.model.KnowledgeNodeResponse
import com.mathlearning.shared.model.KnowledgeProgressResponse
import org.koin.androidx.compose.koinViewModel

@Composable
fun KnowledgeScreen(studentId: String?) {
    val viewModel: KnowledgeViewModel = koinViewModel()
    val graphState by viewModel.graphState.collectAsState()
    val progress by viewModel.progress.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadGraph() }
    LaunchedEffect(studentId) { studentId?.let { viewModel.loadProgress(it) } }

    when (val state = graphState) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is UiState.Error -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.message)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { viewModel.loadGraph() }) { Text("Retry") }
            }
        }
        is UiState.Success -> LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            items(state.data) { node ->
                KnowledgeNodeItem(
                    node = node,
                    progress = progress,
                    depth = 0,
                    studentId = studentId,
                    onMasteryClick = { code, level ->
                        studentId?.let { viewModel.updateMastery(it, code, level) }
                    },
                )
            }
        }
    }
}

@Composable
private fun KnowledgeNodeItem(
    node: KnowledgeNodeResponse,
    progress: Map<String, KnowledgeProgressResponse>,
    depth: Int,
    studentId: String?,
    onMasteryClick: (String, String) -> Unit,
) {
    var expanded by remember { mutableStateOf(depth < 1) }
    val mastery = progress[node.code]?.masteryLevel ?: "UNKNOWN"
    val nextLevel = when (mastery) {
        "UNKNOWN" -> "FAMILIAR"
        "FAMILIAR" -> "MASTERED"
        else -> "UNKNOWN"
    }

    Column(modifier = Modifier.padding(start = (depth * 16).dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { if (node.children.isNotEmpty()) expanded = !expanded }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (node.children.isNotEmpty()) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Spacer(modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(node.nameEn, style = MaterialTheme.typography.bodyMedium)
                Text(node.nameZh, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (studentId != null) {
                MasteryBadge(
                    level = mastery,
                    modifier = Modifier.clickable { onMasteryClick(node.code, nextLevel) },
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                node.children.forEach { child ->
                    KnowledgeNodeItem(child, progress, depth + 1, studentId, onMasteryClick)
                }
            }
        }
    }
}
