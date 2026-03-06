package com.mathlearning.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class SolveRequest(
    val question: String,
    val grade: Int,
    val studentId: String? = null,
)

@Serializable
data class SolveResponse(
    val parentGuide: String? = null,
    val childScript: String? = null,
    val barModelJson: String? = null,
    val knowledgeTags: List<String>? = null,
)

@Serializable
data class SolveEvent(
    val type: String,
    val content: String,
)

@Serializable
data class Student(
    val id: String,
    val name: String,
    val grade: Int,
)

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class LoginResponse(
    val token: String,
    val userId: String,
    val expiresAt: String,
)

@Serializable
data class CreateStudentRequest(
    val name: String,
    val grade: Int,
)
