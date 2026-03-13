package com.mathlearning.android.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mathlearning.android.cache.NetworkMonitor
import com.mathlearning.android.ui.UiState
import com.mathlearning.shared.api.MathApi
import com.mathlearning.shared.model.PagedRecordResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val api: MathApi,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {
    private val _records = MutableStateFlow<UiState<PagedRecordResponse>>(UiState.Loading)
    val records: StateFlow<UiState<PagedRecordResponse>> = _records

    private var currentPage = 0

    fun loadRecords(studentId: String, page: Int = 0) {
        currentPage = page
        viewModelScope.launch {
            _records.value = UiState.Loading
            try {
                _records.value = UiState.Success(api.getRecords(studentId, page))
            } catch (e: Exception) {
                _records.value = UiState.Error("Failed to load history")
            }
        }
    }

    fun rateRecord(recordId: String, rating: Int) {
        viewModelScope.launch {
            try { api.rateRecord(recordId, rating) } catch (_: Exception) {}
        }
    }
}
