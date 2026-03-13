package com.mathlearning.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MasteryBadge(level: String, modifier: Modifier = Modifier) {
    val (bg, label) = when (level) {
        "MASTERED" -> Color(0xFF4CAF50) to "Mastered"
        "FAMILIAR" -> Color(0xFFFFC107) to "Familiar"
        else -> Color(0xFF9E9E9E) to "Unknown"
    }
    Text(
        text = label,
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
