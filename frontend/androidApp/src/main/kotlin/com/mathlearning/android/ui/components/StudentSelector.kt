package com.mathlearning.android.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mathlearning.shared.model.Student

@Composable
fun StudentSelector(
    students: List<Student>,
    selectedStudent: Student?,
    onStudentSelected: (Student) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        TextButton(onClick = { expanded = true }) {
            Text(selectedStudent?.let { "${it.name} (P${it.grade})" } ?: "Select Student")
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            students.forEach { student ->
                DropdownMenuItem(
                    text = { Text("${student.name} (P${student.grade})") },
                    onClick = {
                        onStudentSelected(student)
                        expanded = false
                    },
                )
            }
        }
    }
}
