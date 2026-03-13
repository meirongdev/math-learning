package com.mathlearning.android.ui.growth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mathlearning.android.cache.NetworkMonitor
import com.mathlearning.android.ui.UiState
import com.mathlearning.shared.api.MathApi
import com.mathlearning.shared.model.AchievementResponse
import com.mathlearning.shared.model.LearningPathResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GrowthViewModel(
    private val api: MathApi,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {
    private val _achievements = MutableStateFlow<UiState<List<AchievementResponse>>>(UiState.Loading)
    val achievements: StateFlow<UiState<List<AchievementResponse>>> = _achievements

    private val _learningPath = MutableStateFlow<UiState<LearningPathResponse>?>(null)
    val learningPath: StateFlow<UiState<LearningPathResponse>?> = _learningPath

    fun load(studentId: String) {
        viewModelScope.launch {
            _achievements.value = UiState.Loading
            try {
                _achievements.value = UiState.Success(api.getStudentAchievements(studentId))
            } catch (e: Exception) {
                _achievements.value = UiState.Error("Failed to load achievements")
            }
        }
        viewModelScope.launch {
            _learningPath.value = UiState.Loading
            try {
                _learningPath.value = UiState.Success(api.getLearningPath(studentId))
            } catch (e: Exception) {
                _learningPath.value = UiState.Error("Failed to load learning path")
            }
        }
    }
}
