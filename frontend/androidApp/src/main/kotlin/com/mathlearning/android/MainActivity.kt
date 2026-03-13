package com.mathlearning.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mathlearning.android.ui.theme.MathLearningTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MathLearningTheme {
                // Navigation will be added in Task 8
                androidx.compose.material3.Text("Math Learning App")
            }
        }
    }
}
