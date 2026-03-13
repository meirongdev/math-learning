package com.mathlearning.android.di

import androidx.datastore.preferences.core.stringPreferencesKey
import com.mathlearning.android.cache.AppDatabase
import com.mathlearning.android.cache.NetworkMonitor
import com.mathlearning.android.ui.auth.AuthViewModel
import com.mathlearning.android.ui.growth.GrowthViewModel
import com.mathlearning.android.ui.history.HistoryViewModel
import com.mathlearning.android.ui.knowledge.KnowledgeViewModel
import com.mathlearning.android.ui.mistakes.MistakesViewModel
import com.mathlearning.android.ui.settings.SettingsViewModel
import com.mathlearning.android.ui.solve.SolveViewModel
import com.mathlearning.shared.api.MathApi
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val BASE_URL_KEY = stringPreferencesKey("backend_url")
const val DEFAULT_BASE_URL = "http://10.0.2.2:8080"

val appModule = module {
    // Infrastructure
    single { NetworkMonitor(androidContext()) }
    single { AppDatabase.create(androidContext()) }
    single { get<AppDatabase>().knowledgeNodeDao() }
    single { get<AppDatabase>().recordDao() }
    single { get<AppDatabase>().achievementDao() }

    // API
    single { MathApi(baseUrl = { DEFAULT_BASE_URL }) }

    // ViewModels
    viewModel { AuthViewModel(get()) }
    viewModel { SolveViewModel(get()) }
    viewModel { KnowledgeViewModel(get(), get()) }
    viewModel { GrowthViewModel(get(), get()) }
    viewModel { HistoryViewModel(get(), get()) }
    viewModel { MistakesViewModel(get(), get()) }
    viewModel { SettingsViewModel(androidApplication()) }
}
