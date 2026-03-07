# Documentation Guide

This folder is organized around the current running system, not historical implementation phases.

## Start Here

- `quickstart.md` — local setup, boot, and verification steps.
- `architecture.md` — current technical architecture, data flow, and major runtime decisions.
- `current-implementation.md` — feature-by-feature implementation notes mapped to the codebase.
- `reference/api.md` — authoritative HTTP API reference.
- `roadmap.md` — shipped phases vs upcoming phases.
- `dev-plan.md` — detailed delivery plan and execution status.

## Supporting References

- `reference/configuration.md` — profile and environment configuration.
- `reference/make-targets.md` — Makefile command reference.
- `reference/streaming.md` — current JSON vs SSE streaming decision log.
- `reference/troubleshooting.md` — operational debugging notes.

## Tutorials

- `tutorials/add-psle-questions.md` — extend the assessment/RAG content.
- `tutorials/switch-llm-provider.md` — move from local Ollama to hosted providers.

## Domain Docs Worth Keeping

- `knowledge-graph-requirements.md` — original knowledge graph requirements and seed-data context.
- `learning-guide.md` — implementation-focused learning notes for contributors.
- `requirements.md` — product and platform requirements baseline.
- `product-ideas.md` — future-facing ideas that are intentionally not all implemented yet.

Historical one-off design notes that were fully superseded by the running implementation were removed to keep this directory current and easier to trust.
