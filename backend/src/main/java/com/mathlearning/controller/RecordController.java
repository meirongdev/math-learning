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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

	@GetMapping("/{studentId}")
	public PagedRecordResponse getRecords(@PathVariable UUID studentId, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		Page<SolveRecord> result = solveRecordRepository.findByStudentId(studentId,
				PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
		List<RecordResponse> records = result.getContent().stream()
				.map(r -> new RecordResponse(r.getId(), r.getQuestionText(), r.getParentGuide(), r.getChildScript(),
						r.getBarModelJson(), r.getKnowledgeTags(), r.getRating(), r.getCreatedAt().toString()))
				.toList();
		return new PagedRecordResponse(records, result.getNumber(), result.getSize(), result.getTotalElements(),
				result.getTotalPages());
	}

	@PatchMapping("/{recordId}/rating")
	public ResponseEntity<RatingResponse> rateRecord(@PathVariable UUID recordId,
			@Valid @RequestBody RatingRequest request) {
		SolveRecord record = solveRecordRepository.findById(recordId).orElse(null);
		if (record == null) {
			return ResponseEntity.notFound().build();
		}
		record.setRating(request.rating());
		solveRecordRepository.save(record);

		// Suggest mastery level based on rating
		String suggestedMastery = switch (request.rating()) {
			case 4, 5 -> "MASTERED";
			case 2, 3 -> "FAMILIAR";
			default -> "UNKNOWN";
		};

		// Auto-update knowledge progress mastery for associated tags
		if (record.getKnowledgeTags() != null && record.getStudent() != null) {
			UUID studentId = record.getStudent().getId();
			for (String tag : record.getKnowledgeTags()) {
				knowledgeService.updateMastery(studentId, tag, suggestedMastery);
			}
		}

		return ResponseEntity.ok(new RatingResponse(record.getId(), request.rating(), suggestedMastery));
	}
}
