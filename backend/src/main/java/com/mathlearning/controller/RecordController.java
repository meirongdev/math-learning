package com.mathlearning.controller;

import com.mathlearning.model.entity.SolveRecord;
import com.mathlearning.repository.SolveRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/records")
public class RecordController {

	private final SolveRecordRepository solveRecordRepository;

	public RecordController(SolveRecordRepository solveRecordRepository) {
		this.solveRecordRepository = solveRecordRepository;
	}

	public record RecordResponse(UUID id, String questionText, String parentGuide, String childScript,
			String barModelJson, List<String> knowledgeTags, String createdAt) {
	}

	public record PagedRecordResponse(List<RecordResponse> records, int page, int size, long totalElements,
			int totalPages) {
	}

	@GetMapping("/{studentId}")
	public PagedRecordResponse getRecords(@PathVariable UUID studentId,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		Page<SolveRecord> result = solveRecordRepository.findByStudentId(studentId,
				PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
		List<RecordResponse> records = result.getContent().stream()
				.map(r -> new RecordResponse(r.getId(), r.getQuestionText(), r.getParentGuide(), r.getChildScript(),
						r.getBarModelJson(), r.getKnowledgeTags(), r.getCreatedAt().toString()))
				.toList();
		return new PagedRecordResponse(records, result.getNumber(), result.getSize(), result.getTotalElements(),
				result.getTotalPages());
	}
}
