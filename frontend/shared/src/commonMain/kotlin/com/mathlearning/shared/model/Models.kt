package com.mathlearning.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class SolveRequest(
    val question: String,
    val grade: Int,
    val studentId: String? = null
)

@Serializable
data class SolveEvent(
    val type: String,
    val content: String
)

@Serializable
data class Student(
    val id: String,
    val name: String,
    val grade: String
)
