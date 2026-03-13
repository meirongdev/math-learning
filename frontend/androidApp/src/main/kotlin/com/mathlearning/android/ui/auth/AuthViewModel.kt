package com.mathlearning.android.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mathlearning.shared.api.MathApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val api: MathApi) : ViewModel() {
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun login(email: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                api.login(email, password)
                onSuccess()
            } catch (e: Exception) {
                _error.value = "Login failed. Please check your credentials."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun register(email: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val success = api.register(email, password)
                if (success) {
                    api.login(email, password)
                    onSuccess()
                } else {
                    _error.value = "Registration failed. Email may already be in use."
                }
            } catch (e: Exception) {
                _error.value = "Registration failed. Please try again."
            } finally {
                _isLoading.value = false
            }
        }
    }
}
