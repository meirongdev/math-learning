package com.mathlearning.model;

import java.util.List;

/**
 * Aggregated result from the multi-agent solving pipeline.
 *
 * @param parentGuide   teaching guide for the parent (Persona Agent output)
 * @param childScript   fun explanatory script for the child (Persona Agent output)
 * @param barModelJson  Bar Model visual description as JSON string (CPA Designer Agent output)
 * @param knowledgeTags list of knowledge point codes identified by the Planner Agent
 */
public record SolveResult(
        String parentGuide,
        String childScript,
        String barModelJson,
        List<String> knowledgeTags
) {}
