-- Phase 6: Knowledge graph, assessment questions, and rating

-- 1. Knowledge nodes table
CREATE TABLE knowledge_nodes (
    code        VARCHAR(100) PRIMARY KEY,
    name_en     VARCHAR(200) NOT NULL,
    name_zh     VARCHAR(200) NOT NULL,
    parent_code VARCHAR(100) REFERENCES knowledge_nodes(code),
    grade_start INTEGER NOT NULL CHECK (grade_start BETWEEN 1 AND 6),
    sort_order  INTEGER NOT NULL DEFAULT 0
);

-- 2. Add mastery_level to knowledge_progress
ALTER TABLE knowledge_progress
    ADD COLUMN mastery_level VARCHAR(10) NOT NULL DEFAULT 'UNKNOWN'
        CHECK (mastery_level IN ('UNKNOWN', 'FAMILIAR', 'MASTERED'));

-- 3. Add rating to solve_records
ALTER TABLE solve_records
    ADD COLUMN rating INTEGER CHECK (rating BETWEEN 1 AND 5);

-- 4. Assessment questions
CREATE TABLE assessment_questions (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_text  TEXT NOT NULL,
    grade          INTEGER NOT NULL CHECK (grade BETWEEN 1 AND 6),
    difficulty     VARCHAR(10) NOT NULL CHECK (difficulty IN ('easy', 'medium', 'hard')),
    answer_hint    TEXT
);

CREATE TABLE assessment_question_tags (
    question_id UUID REFERENCES assessment_questions(id) ON DELETE CASCADE,
    node_code   VARCHAR(100) REFERENCES knowledge_nodes(code),
    PRIMARY KEY (question_id, node_code)
);
