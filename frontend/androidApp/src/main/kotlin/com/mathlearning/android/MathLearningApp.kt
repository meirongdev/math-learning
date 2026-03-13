package com.mathlearning.android

import android.app.Application
import com.mathlearning.android.di.appModule
import com.mathlearning.shared.storage.initTokenStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MathLearningApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initTokenStore(this)
        startKoin {
            androidContext(this@MathLearningApp)
            modules(appModule)
        }
    }
}
