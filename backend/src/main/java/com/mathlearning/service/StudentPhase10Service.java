package com.mathlearning.service;

import com.mathlearning.model.entity.AssessmentQuestion;
import com.mathlearning.model.entity.KnowledgeNode;
import com.mathlearning.model.entity.KnowledgeProgress;
import com.mathlearning.repository.AssessmentQuestionRepository;
import com.mathlearning.repository.KnowledgeNodeRepository;
import com.mathlearning.repository.KnowledgeProgressRepository;
import com.mathlearning.repository.SolveRecordRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class StudentPhase10Service {

	private static final String MASTERED = "MASTERED";
	private static final String FAMILIAR = "FAMILIAR";
	private static final String UNKNOWN = "UNKNOWN";

	private final SolveRecordRepository solveRecordRepository;
	private final KnowledgeProgressRepository knowledgeProgressRepository;
	private final KnowledgeNodeRepository knowledgeNodeRepository;
	private final AssessmentQuestionRepository assessmentQuestionRepository;

	public StudentPhase10Service(SolveRecordRepository solveRecordRepository,
			KnowledgeProgressRepository knowledgeProgressRepository, KnowledgeNodeRepository knowledgeNodeRepository,
			AssessmentQuestionRepository assessmentQuestionRepository) {
		this.solveRecordRepository = solveRecordRepository;
		this.knowledgeProgressRepository = knowledgeProgressRepository;
		this.knowledgeNodeRepository = knowledgeNodeRepository;
		this.assessmentQuestionRepository = assessmentQuestionRepository;
	}

	public record AchievementBadge(String code, String title, String description, String icon, boolean unlocked,
			int currentValue, int targetValue) {
	}

	public record LearningNode(String code, String nameEn, String nameZh, String masteryLevel, int gradeStart) {
	}

	public record RecommendedQuestion(UUID id, String questionText, int grade, String difficulty, String answerHint) {
	}

	public record LearningPath(String summary, String reason, LearningNode focusNode, LearningNode prerequisiteNode,
			List<RecommendedQuestion> questions) {
	}

	public List<AchievementBadge> getAchievements(UUID studentId) {
		var records = solveRecordRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
		var progress = knowledgeProgressRepository.findByStudentIdOrderByAttemptCountDesc(studentId);

		int totalSolves = records.size();
		int currentStreakDays = calculateCurrentStreak(records.stream().map(r -> r.getCreatedAt().toLocalDate()).toList());
		int distinctPracticeDays = (int) records.stream().map(r -> r.getCreatedAt().toLocalDate()).distinct().count();
		int masteredNodes = (int) progress.stream().filter(p -> MASTERED.equals(p.getMasteryLevel())).count();
		int fractionMasteryCount = (int) progress.stream()
				.filter(p -> MASTERED.equals(p.getMasteryLevel()) && p.getKnowledgeCode().contains("fraction"))
				.count();
		Set<String> exploredCodes = progress.stream().map(KnowledgeProgress::getKnowledgeCode)
				.collect(Collectors.toCollection(LinkedHashSet::new));
		int reflectionCount = (int) records.stream().filter(r -> r.getRating() != null).count();

		List<AchievementBadge> badges = new ArrayList<>();
		badges.add(badge("first-solve", "First Spark", "Solve the first question together.", "Spark",
				totalSolves, 1));
		badges.add(badge("streak-3", "3-Day Orbit", "Practice on 3 consecutive days.", "Orbit",
				currentStreakDays, 3));
		badges.add(badge("streak-7", "7-Day Comet", "Keep the learning streak alive for a full week.", "Comet",
				currentStreakDays, 7));
		badges.add(badge("knowledge-explorer", "Knowledge Explorer", "Touch at least 5 different skills.",
				"Explorer", exploredCodes.size(), 5));
		badges.add(badge("fraction-master", "Fraction Finisher", "Master at least 1 fractions skill.",
				"Fraction", fractionMasteryCount, 1));
		badges.add(badge("mastery-builder", "Mastery Builder", "Reach mastery in 3 knowledge nodes.",
				"Builder", masteredNodes, 3));
		badges.add(badge("reflective-coach", "Reflective Coach", "Rate 3 explanations to refine the tutor.",
				"Coach", reflectionCount, 3));
		badges.add(badge("steady-parent", "Steady Parent", "Practice on 5 different days.", "Steady",
				distinctPracticeDays, 5));
		return badges;
	}

	public LearningPath getLearningPath(UUID studentId, int studentGrade) {
		List<KnowledgeNode> nodes = knowledgeNodeRepository.findAllByOrderBySortOrderAsc();
		Map<String, KnowledgeNode> nodeMap = nodes.stream().collect(Collectors.toMap(KnowledgeNode::getCode, n -> n));
		Map<String, KnowledgeProgress> progressMap = knowledgeProgressRepository.findByStudentIdOrderByAttemptCountDesc(studentId)
				.stream().collect(Collectors.toMap(KnowledgeProgress::getKnowledgeCode, p -> p, (a, _) -> a));

		KnowledgeNode baseTarget = selectWeakestNode(nodes, progressMap, studentGrade);
		KnowledgeNode prerequisite = findUnmetPrerequisite(baseTarget, nodeMap, progressMap);
		KnowledgeNode effectiveTarget = prerequisite != null ? prerequisite : baseTarget;

		LearningNode focusNode = toLearningNode(baseTarget, progressMap);
		LearningNode prerequisiteNode = prerequisite != null ? toLearningNode(prerequisite, progressMap) : null;
		List<RecommendedQuestion> questions = recommendQuestions(effectiveTarget, studentGrade);

		String reason = prerequisite != null
				? "The learner is close to '%s', but the prerequisite '%s' is not mastered yet, so we should reinforce that foundation first."
					.formatted(baseTarget.getNameEn(), prerequisite.getNameEn())
				: "The learner needs one more focused challenge in '%s'. This recommendation is based on the weakest non-mastered node with recent activity or unmet mastery."
					.formatted(baseTarget.getNameEn());
		String pluralSuffix = questions.size() == 1 ? "" : "s";
		String summary = questions.isEmpty()
				? "No tagged challenge question is available yet, but '%s' is still the best next focus area.".formatted(effectiveTarget.getNameEn())
				: "Next best challenge: strengthen '%s' with %d curated question%s."
					.formatted(effectiveTarget.getNameEn(), questions.size(), pluralSuffix);

		return new LearningPath(summary, reason, focusNode, prerequisiteNode, questions);
	}

	private AchievementBadge badge(String code, String title, String description, String icon, int currentValue,
			int targetValue) {
		return new AchievementBadge(code, title, description, icon, currentValue >= targetValue,
				Math.min(currentValue, targetValue), targetValue);
	}

	private int calculateCurrentStreak(List<LocalDate> orderedDates) {
		if (orderedDates.isEmpty()) {
			return 0;
		}
		List<LocalDate> uniqueDates = orderedDates.stream().distinct().sorted(Comparator.reverseOrder()).toList();
		LocalDate today = LocalDate.now();
		LocalDate cursor = uniqueDates.getFirst();
		if (!cursor.equals(today) && !cursor.equals(today.minusDays(1))) {
			return 0;
		}
		int streak = 1;
		for (int i = 1; i < uniqueDates.size(); i++) {
			if (uniqueDates.get(i).equals(cursor.minusDays(1))) {
				streak++;
				cursor = uniqueDates.get(i);
			} else {
				break;
			}
		}
		return streak;
	}

	private KnowledgeNode selectWeakestNode(List<KnowledgeNode> nodes, Map<String, KnowledgeProgress> progressMap,
			int studentGrade) {
		Predicate<KnowledgeNode> gradeEligible = node -> node.getGradeStart() <= studentGrade;
		List<KnowledgeNode> candidateNodes = nodes.stream().filter(gradeEligible)
				.filter(node -> progressMap.containsKey(node.getCode()) || node.getParentCode() != null).toList();

		return candidateNodes.stream().filter(node -> !isMastered(node.getCode(), progressMap)).sorted(Comparator
				.comparingInt((KnowledgeNode node) -> masteryPriority(progressMap.get(node.getCode()))).thenComparing(
						( KnowledgeNode node) -> attemptPriority(progressMap.get(node.getCode())), Comparator.reverseOrder())
				.thenComparing(KnowledgeNode::getGradeStart).thenComparing(KnowledgeNode::getSortOrder)).findFirst()
				.orElseGet(() -> nodes.stream().filter(gradeEligible).findFirst().orElse(nodes.getFirst()));
	}

	private KnowledgeNode findUnmetPrerequisite(KnowledgeNode target, Map<String, KnowledgeNode> nodeMap,
			Map<String, KnowledgeProgress> progressMap) {
		if (target == null || target.getParentCode() == null) {
			return null;
		}
		KnowledgeNode parent = nodeMap.get(target.getParentCode());
		if (parent == null) {
			return null;
		}
		return !isMastered(parent.getCode(), progressMap) ? parent : null;
	}

	private List<RecommendedQuestion> recommendQuestions(KnowledgeNode target, int studentGrade) {
		List<AssessmentQuestion> exactMatches = assessmentQuestionRepository.findRandomByTagAndGrade(target.getCode(),
				studentGrade, 3);
		List<AssessmentQuestion> questions = exactMatches.isEmpty()
				? assessmentQuestionRepository.findRandomByTagAndGrade(null, studentGrade, 3)
				: exactMatches;
		return questions.stream().map(q -> new RecommendedQuestion(q.getId(), q.getQuestionText(), q.getGrade(),
				q.getDifficulty(), q.getAnswerHint())).toList();
	}

	private LearningNode toLearningNode(KnowledgeNode node, Map<String, KnowledgeProgress> progressMap) {
		return new LearningNode(node.getCode(), node.getNameEn(), node.getNameZh(),
				progressMap.getOrDefault(node.getCode(), defaultProgress(node.getCode())).getMasteryLevel(),
				node.getGradeStart());
	}

	private KnowledgeProgress defaultProgress(String knowledgeCode) {
		KnowledgeProgress progress = new KnowledgeProgress();
		progress.setKnowledgeCode(knowledgeCode);
		progress.setMasteryLevel(UNKNOWN);
		progress.setUpdatedAt(OffsetDateTime.now());
		return progress;
	}

	private int masteryPriority(KnowledgeProgress progress) {
		if (progress == null) {
			return 3;
		}
		return switch (progress.getMasteryLevel()) {
			case FAMILIAR -> 0;
			case UNKNOWN -> 1;
			case MASTERED -> 4;
			default -> 2;
		};
	}

	private int attemptPriority(KnowledgeProgress progress) {
		return progress != null ? progress.getAttemptCount() : 0;
	}

	private boolean isMastered(String knowledgeCode, Map<String, KnowledgeProgress> progressMap) {
		return MASTERED.equals(progressMap.getOrDefault(knowledgeCode, defaultProgress(knowledgeCode)).getMasteryLevel());
	}
}
