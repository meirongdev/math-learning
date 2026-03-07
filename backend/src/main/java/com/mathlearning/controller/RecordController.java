package com.mathlearning.controller;

import com.mathlearning.model.entity.SolveRecord;
import com.mathlearning.repository.SolveRecordRepository;
import com.mathlearning.service.KnowledgeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/records")
public class RecordController {

	private final SolveRecordRepository solveRecordRepository;
	private final KnowledgeService knowledgeService;

	public RecordController(SolveRecordRepository solveRecordRepository, KnowledgeService knowledgeService) {
		this.solveRecordRepository = solveRecordRepository;
		this.knowledgeService = knowledgeService;
	}

	public record RecordResponse(UUID id, String questionText, String parentGuide, String childScript,
			String barModelJson, List<String> knowledgeTags, Integer rating, String createdAt) {
	}

	public record PagedRecordResponse(List<RecordResponse> records, int page, int size, long totalElements,
			int totalPages) {
	}

	public record RatingRequest(@Min(1) @Max(5) int rating) {
	}

	public record RatingResponse(UUID recordId, int rating, String suggestedMastery) {
	}

	public record MistakeRecordResponse(UUID id, String questionText, List<String> knowledgeTags, Integer rating,
			String createdAt) {
	}

	public record MistakePageResponse(List<MistakeRecordResponse> records, int page, int size, long totalElements,
			int totalPages) {
	}

	public record RecordExportResponse(UUID recordId, String suggestedFileName, String mimeType, String printableHtml,
			String markdownContent, String generatedAt) {
	}

	@GetMapping(value = "/{studentId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<PagedRecordResponse> getRecords(@AuthenticationPrincipal UUID userId,
			@PathVariable UUID studentId, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		Page<SolveRecord> result = solveRecordRepository.findByStudentIdAndStudentParentId(studentId, userId,
				PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
		List<RecordResponse> records = result.getContent().stream()
				.map(r -> new RecordResponse(r.getId(), r.getQuestionText(), r.getParentGuide(), r.getChildScript(),
						r.getBarModelJson(), r.getKnowledgeTags(), r.getRating(), r.getCreatedAt().toString()))
				.toList();
		return ResponseEntity.ok(new PagedRecordResponse(records, result.getNumber(), result.getSize(),
				result.getTotalElements(), result.getTotalPages()));
	}

	@GetMapping(value = "/mistakes", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<MistakePageResponse> getMistakes(@AuthenticationPrincipal UUID userId,
			@RequestParam(required = false) UUID studentId, @RequestParam(required = false) String tag,
			@RequestParam(required = false) OffsetDateTime from,
			@RequestParam(required = false) OffsetDateTime to,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		Page<SolveRecord> result = solveRecordRepository.findMistakes(userId, studentId, PageRequest.of(page, size));
		List<MistakeRecordResponse> records = result.getContent().stream()
				.filter(r -> tag == null || (r.getKnowledgeTags() != null && r.getKnowledgeTags().contains(tag)))
				.filter(r -> from == null || !r.getCreatedAt().isBefore(from))
				.filter(r -> to == null || !r.getCreatedAt().isAfter(to))
				.map(r -> new MistakeRecordResponse(r.getId(), r.getQuestionText(), r.getKnowledgeTags(), r.getRating(),
						r.getCreatedAt().toString()))
				.toList();
		return ResponseEntity.ok(new MistakePageResponse(records, result.getNumber(), result.getSize(),
				result.getTotalElements(), result.getTotalPages()));
	}

	@GetMapping(value = "/{recordId}/export", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<RecordExportResponse> exportRecord(@AuthenticationPrincipal UUID userId,
			@PathVariable UUID recordId) {
		SolveRecord solveRecord = solveRecordRepository.findByIdWithStudentAndParentId(recordId, userId)
				.orElse(null);
		if (solveRecord == null) {
			return ResponseEntity.notFound().build();
		}

		String safeQuestion = solveRecord.getQuestionText() != null ? solveRecord.getQuestionText() : "";
		String title = "SG Math Tutor - Practice Export";
		String tags = solveRecord.getKnowledgeTags() == null || solveRecord.getKnowledgeTags().isEmpty() ? "N/A"
				: String.join(", ", solveRecord.getKnowledgeTags());
		String parentGuide = solveRecord.getParentGuide() != null ? solveRecord.getParentGuide() : "N/A";
		String childScript = solveRecord.getChildScript() != null ? solveRecord.getChildScript() : "N/A";
		String barModel = solveRecord.getBarModelJson() != null ? solveRecord.getBarModelJson() : "{}";

		String markdown = """
				# %s

				- Student: %s
				- Grade: P%d
				- Created At: %s
				- Knowledge Tags: %s

				## Question
				%s

				## Parent Guide
				%s

				## Child Script
				%s

				## Bar Model JSON
				```json
				%s
				```
				""".formatted(title, solveRecord.getStudent().getName(), solveRecord.getStudent().getGrade(),
				solveRecord.getCreatedAt(), tags, safeQuestion, parentGuide, childScript, barModel);

		String html = """
				<!doctype html>
				<html>
				<head><meta charset="utf-8"><title>%s</title></head>
				<body style="font-family: 'Noto Sans SC', Arial, sans-serif; line-height:1.6; padding:24px;">
				  <h1>%s</h1>
				  <p><strong>Student:</strong> %s (P%d)</p>
				  <p><strong>Created:</strong> %s</p>
				  <p><strong>Knowledge Tags:</strong> %s</p>
				  <h2>Question</h2><p>%s</p>
				  <h2>Parent Guide</h2><p>%s</p>
				  <h2>Child Script</h2><p>%s</p>
				  <h2>Bar Model JSON</h2><pre>%s</pre>
				</body>
				</html>
				""".formatted(title, title, solveRecord.getStudent().getName(), solveRecord.getStudent().getGrade(),
				solveRecord.getCreatedAt(), tags, escapeHtml(safeQuestion), escapeHtml(parentGuide),
				escapeHtml(childScript), escapeHtml(barModel));

		String fileName = "math-learning-%s-%s"
				.formatted(solveRecord.getStudent().getName().replaceAll("\\s+", "-"), solveRecord.getId());
		return ResponseEntity
				.ok(new RecordExportResponse(solveRecord.getId(), fileName + ".pdf", "application/pdf",
				html, markdown, OffsetDateTime.now().toString()));
	}

	@PatchMapping("/{recordId}/rating")
	public ResponseEntity<RatingResponse> rateRecord(@AuthenticationPrincipal UUID userId, @PathVariable UUID recordId,
			@Valid @RequestBody RatingRequest request) {
		SolveRecord solveRecord = solveRecordRepository.findByIdAndStudentParentId(recordId, userId).orElse(null);
		if (solveRecord == null) {
			return ResponseEntity.notFound().build();
		}
		solveRecord.setRating(request.rating());
		solveRecordRepository.save(solveRecord);

		// Suggest mastery level based on rating
		String suggestedMastery = switch (request.rating()) {
			case 4, 5 -> "MASTERED";
			case 2, 3 -> "FAMILIAR";
			default -> "UNKNOWN";
		};

		// Auto-update knowledge progress mastery for associated tags
		if (solveRecord.getKnowledgeTags() != null && solveRecord.getStudent() != null) {
			UUID studentId = solveRecord.getStudent().getId();
			for (String tag : solveRecord.getKnowledgeTags()) {
				knowledgeService.updateMastery(studentId, tag, suggestedMastery);
			}
		}

		return ResponseEntity.ok(new RatingResponse(solveRecord.getId(), request.rating(), suggestedMastery));
	}

	private String escapeHtml(String raw) {
		return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
