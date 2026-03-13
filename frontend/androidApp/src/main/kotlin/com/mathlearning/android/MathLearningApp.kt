package com.mathlearning.android

import android.app.Application
import com.mathlearning.shared.storage.initTokenStore

class MathLearningApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initTokenStore(this)
    }
}
