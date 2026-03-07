package com.mathlearning.controller;

import com.mathlearning.AbstractIntegrationTest;
import com.mathlearning.model.entity.SolveRecord;
import com.mathlearning.model.entity.StudentProfile;
import com.mathlearning.model.entity.User;
import com.mathlearning.repository.SolveRecordRepository;
import com.mathlearning.repository.StudentProfileRepository;
import com.mathlearning.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecordControllerTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    StudentProfileRepository studentProfileRepository;

    @Autowired
    SolveRecordRepository solveRecordRepository;

    private String ownerToken;
    private String outsiderToken;
    private SolveRecord ownerLowRatedRecord;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User owner = userRepository
                .save(User.builder().email("owner-" + suffix + "@example.com").password("encoded").build());
        User outsider = userRepository.save(
                User.builder().email("outsider-" + suffix + "@example.com").password("encoded").build());
        ownerToken = generateTestToken(owner.getId());
        outsiderToken = generateTestToken(outsider.getId());

        StudentProfile ownerStudent = studentProfileRepository
                .save(StudentProfile.builder().parent(owner).name("Mia").grade(4).build());
        StudentProfile outsiderStudent = studentProfileRepository
                .save(StudentProfile.builder().parent(outsider).name("Tom").grade(4).build());

        ownerLowRatedRecord = solveRecordRepository.save(SolveRecord.builder()
                .student(ownerStudent)
                .questionText("What is 1/2 + 1/3?")
                .parentGuide("Use LCD")
                .childScript("Find common denominator")
                .knowledgeTags(List.of("frac.add_sub"))
                .rating(2)
                .createdAt(OffsetDateTime.now().minusDays(1))
                .build());

        solveRecordRepository.save(SolveRecord.builder()
                .student(ownerStudent)
                .questionText("What is 3 + 4?")
                .knowledgeTags(List.of("whole_numbers"))
                .rating(5)
                .createdAt(OffsetDateTime.now())
                .build());

        solveRecordRepository.save(SolveRecord.builder()
                .student(outsiderStudent)
                .questionText("What is 5/8 + 1/8?")
                .knowledgeTags(List.of("frac.add_sub"))
                .rating(1)
                .createdAt(OffsetDateTime.now().minusHours(3))
                .build());
    }

    @Test
    void getMistakes_ReturnsOnlyOwnerLowRatedRecords() throws Exception {
        mockMvc.perform(get("/api/v1/records/mistakes")
                        .param("studentId", ownerLowRatedRecord.getStudent().getId().toString())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.records[0].rating").value(org.hamcrest.Matchers.lessThanOrEqualTo(2)))
                .andExpect(jsonPath("$.records[*].id").value(org.hamcrest.Matchers.hasItem(ownerLowRatedRecord.getId().toString())));
    }

    @Test
    void exportRecord_ReturnsPreviewPayloadForOwner() throws Exception {
        mockMvc.perform(get("/api/v1/records/{recordId}/export", ownerLowRatedRecord.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordId").value(ownerLowRatedRecord.getId().toString()))
                .andExpect(jsonPath("$.mimeType").exists())
                .andExpect(jsonPath("$.suggestedFileName").value(org.hamcrest.Matchers.endsWith(".pdf")))
                .andExpect(jsonPath("$.markdownContent").exists());
    }

    @Test
    void exportRecord_OutsiderGets404() throws Exception {
        mockMvc.perform(get("/api/v1/records/{recordId}/export", ownerLowRatedRecord.getId())
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void rateRecord_OutsiderGets404() throws Exception {
        mockMvc.perform(patch("/api/v1/records/{recordId}/rating", ownerLowRatedRecord.getId())
                        .header("Authorization", "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rating": 4
                                }
                                """))
                .andExpect(status().isNotFound());
    }
}
