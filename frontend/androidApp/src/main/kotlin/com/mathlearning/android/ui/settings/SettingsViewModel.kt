package com.mathlearning.android.ui.settings

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mathlearning.android.di.BASE_URL_KEY
import com.mathlearning.android.di.DEFAULT_BASE_URL
import com.mathlearning.shared.api.MathApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val android.content.Context.dataStore by preferencesDataStore(name = "settings")

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val dataStore = application.dataStore

    private val _url = MutableStateFlow(DEFAULT_BASE_URL)
    val url: StateFlow<String> = _url

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult

    init {
        viewModelScope.launch {
            _url.value = dataStore.data.map { prefs ->
                prefs[BASE_URL_KEY] ?: DEFAULT_BASE_URL
            }.first()
        }
    }

    fun updateUrl(newUrl: String) {
        _url.value = newUrl
    }

    fun saveUrl() {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[BASE_URL_KEY] = _url.value
            }
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _testResult.value = "Testing..."
            try {
                val testApi = MathApi(baseUrl = { _url.value })
                testApi.getKnowledgeGraph()
                _testResult.value = "Connected successfully!"
            } catch (e: Exception) {
                _testResult.value = "Connection failed: ${e.message?.take(80)}"
            }
        }
    }
}
