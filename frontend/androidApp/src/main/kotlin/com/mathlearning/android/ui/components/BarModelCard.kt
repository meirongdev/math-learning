package com.mathlearning.android.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BarModelCard(barModelJson: String?, modifier: Modifier = Modifier) {
    if (barModelJson.isNullOrBlank() || barModelJson == "{}") return
    Card(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Bar Model",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(12.dp),
        )
        Text(
            text = barModelJson,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        )
    }
}
