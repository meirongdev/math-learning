package com.mathlearning.android.ui.mistakes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mathlearning.android.cache.NetworkMonitor
import com.mathlearning.android.ui.UiState
import com.mathlearning.shared.api.MathApi
import com.mathlearning.shared.model.MistakePageResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MistakesViewModel(
    private val api: MathApi,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {
    private val _mistakes = MutableStateFlow<UiState<MistakePageResponse>>(UiState.Loading)
    val mistakes: StateFlow<UiState<MistakePageResponse>> = _mistakes

    fun loadMistakes(studentId: String?, page: Int = 0) {
        viewModelScope.launch {
            _mistakes.value = UiState.Loading
            try {
                _mistakes.value = UiState.Success(api.getMistakes(studentId = studentId, page = page))
            } catch (e: Exception) {
                _mistakes.value = UiState.Error("Failed to load mistakes")
            }
        }
    }
}
