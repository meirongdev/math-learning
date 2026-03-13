package com.mathlearning.android.ui.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mathlearning.android.cache.NetworkMonitor
import com.mathlearning.android.ui.UiState
import com.mathlearning.shared.api.MathApi
import com.mathlearning.shared.model.KnowledgeNodeResponse
import com.mathlearning.shared.model.KnowledgeProgressResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class KnowledgeViewModel(
    private val api: MathApi,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {
    private val _graphState = MutableStateFlow<UiState<List<KnowledgeNodeResponse>>>(UiState.Loading)
    val graphState: StateFlow<UiState<List<KnowledgeNodeResponse>>> = _graphState

    private val _progress = MutableStateFlow<Map<String, KnowledgeProgressResponse>>(emptyMap())
    val progress: StateFlow<Map<String, KnowledgeProgressResponse>> = _progress

    fun loadGraph() {
        viewModelScope.launch {
            _graphState.value = UiState.Loading
            try {
                val graph = api.getKnowledgeGraph()
                _graphState.value = UiState.Success(graph)
            } catch (e: Exception) {
                _graphState.value = UiState.Error("Failed to load knowledge graph")
            }
        }
    }

    fun loadProgress(studentId: String) {
        viewModelScope.launch {
            try {
                val progressList = api.getKnowledgeProgress(studentId)
                _progress.value = progressList.associateBy { it.knowledgeCode }
            } catch (_: Exception) {}
        }
    }

    fun updateMastery(studentId: String, nodeCode: String, level: String) {
        viewModelScope.launch {
            try {
                api.updateMastery(studentId, nodeCode, level)
                loadProgress(studentId)
            } catch (_: Exception) {}
        }
    }
}
