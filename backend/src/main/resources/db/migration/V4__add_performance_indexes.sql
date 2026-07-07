-- V4: Add missing indexes for query performance

-- solve_records: history queries ordered by time, filtered by student
CREATE INDEX idx_solve_records_student_created
    ON solve_records (student_id, created_at DESC);

-- solve_records: mistake filtering (rating <= 2)
CREATE INDEX idx_solve_records_student_rating
    ON solve_records (student_id, rating)
    WHERE rating IS NOT NULL;

-- solve_records: GIN index for knowledge_tags array containment queries
CREATE INDEX idx_solve_records_knowledge_tags
    ON solve_records USING GIN (knowledge_tags);

-- knowledge_nodes: tree traversal by parent
CREATE INDEX idx_knowledge_nodes_parent_code
    ON knowledge_nodes (parent_code);

-- assessment_question_tags: reverse lookup by node_code
CREATE INDEX idx_assessment_question_tags_node_code
    ON assessment_question_tags (node_code);

-- student_profiles: lookup by parent
CREATE INDEX idx_student_profiles_parent_id
    ON student_profiles (parent_id);
