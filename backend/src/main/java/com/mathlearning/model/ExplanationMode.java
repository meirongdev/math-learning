package com.mathlearning.model;

/**
 * Controls the explanation style used by the Content Agent.
 *
 * <ul>
 * <li>{@code ORIGINAL} — direct step-by-step solution with a fun child
 * script</li>
 * <li>{@code SOCRATIC} — heuristic approach using leading questions to guide
 * the child</li>
 * </ul>
 */
public enum ExplanationMode {

	ORIGINAL, SOCRATIC
}
