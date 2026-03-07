package com.mathlearning.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class ExplanationMode {
    ORIGINAL,
    SOCRATIC,
}

@Serializable
data class SolveRequest(
    val question: String,
    val grade: Int,
    val studentId: String? = null,
    val mode: ExplanationMode? = null,
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

// Phase 6 models

@Serializable
data class KnowledgeNodeResponse(
    val code: String,
    val nameEn: String,
    val nameZh: String,
    val parentCode: String? = null,
    val gradeStart: Int,
    val children: List<KnowledgeNodeResponse> = emptyList(),
)

@Serializable
data class KnowledgeProgressResponse(
    val id: String,
    val knowledgeCode: String,
    val attemptCount: Int = 0,
    val correctCount: Int = 0,
    val masteryScore: String = "0",
    val masteryLevel: String = "UNKNOWN",
    val updatedAt: String = "",
)

@Serializable
data class UpdateMasteryRequest(
    val masteryLevel: String,
)

@Serializable
data class RecordResponse(
    val id: String,
    val questionText: String,
    val parentGuide: String? = null,
    val childScript: String? = null,
    val barModelJson: String? = null,
    val knowledgeTags: List<String>? = null,
    val rating: Int? = null,
    val createdAt: String = "",
)

@Serializable
data class PagedRecordResponse(
    val records: List<RecordResponse>,
    val page: Int = 0,
    val size: Int = 20,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
)

@Serializable
data class MistakeRecordResponse(
    val id: String,
    val questionText: String,
    val knowledgeTags: List<String>? = null,
    val rating: Int? = null,
    val createdAt: String = "",
)

@Serializable
data class MistakePageResponse(
    val records: List<MistakeRecordResponse>,
    val page: Int = 0,
    val size: Int = 20,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
)

@Serializable
data class RecordExportResponse(
    val recordId: String,
    val suggestedFileName: String,
    val mimeType: String,
    val printableHtml: String,
    val markdownContent: String,
    val generatedAt: String,
)

@Serializable
data class RatingRequest(
    val rating: Int,
)

@Serializable
data class RatingResponse(
    val recordId: String,
    val rating: Int,
    val suggestedMastery: String,
)

@Serializable
data class AssessmentQuestionResponse(
    val id: String,
    val questionText: String,
    val grade: Int,
    val difficulty: String,
    val answerHint: String? = null,
)

@Serializable
data class AchievementResponse(
    val code: String,
    val title: String,
    val description: String,
    val icon: String,
    val unlocked: Boolean,
    val currentValue: Int,
    val targetValue: Int,
)

@Serializable
data class LearningNodeSummary(
    val code: String,
    val nameEn: String,
    val nameZh: String,
    val masteryLevel: String,
    val gradeStart: Int,
)

@Serializable
data class LearningPathResponse(
    val summary: String,
    val reason: String,
    val focusNode: LearningNodeSummary,
    val prerequisiteNode: LearningNodeSummary? = null,
    val questions: List<AssessmentQuestionResponse> = emptyList(),
)
