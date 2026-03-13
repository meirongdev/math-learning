package com.mathlearning.android.ui.solve

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mathlearning.android.ui.UiState
import com.mathlearning.shared.api.MathApi
import com.mathlearning.shared.model.ExplanationMode
import com.mathlearning.shared.model.SolveRequest
import com.mathlearning.shared.model.SolveResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SolveViewModel(private val api: MathApi) : ViewModel() {
    private val _solveState = MutableStateFlow<UiState<SolveResponse>?>(null)
    val solveState: StateFlow<UiState<SolveResponse>?> = _solveState

    private val _lastRecordId = MutableStateFlow<String?>(null)
    val lastRecordId: StateFlow<String?> = _lastRecordId

    fun solve(question: String, grade: Int, studentId: String?, mode: ExplanationMode) {
        viewModelScope.launch {
            _solveState.value = UiState.Loading
            try {
                val response = api.solve(SolveRequest(question, grade, studentId, mode))
                _solveState.value = UiState.Success(response)
            } catch (e: Exception) {
                _solveState.value = UiState.Error("Failed to solve. Please try again.")
            }
        }
    }

    fun rateRecord(recordId: String, rating: Int) {
        viewModelScope.launch {
            try {
                api.rateRecord(recordId, rating)
            } catch (_: Exception) {
                // Silent fail for rating
            }
        }
    }

    fun reset() {
        _solveState.value = null
    }
}
