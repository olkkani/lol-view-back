-- V1.5__add_bracket_stages.sql
--
-- bracket_stages records which competition format (round robin, Swiss,
-- single elimination, double elimination) each stage of a tournament
-- uses. PandaScore's bracket API exposes no stage-format field (verified
-- against live LCK 2026 Playoffs data) — this is developer-curated
-- metadata, not synced data. See docs/designs (or the office-hours
-- design doc referenced in this plan's header) for the full rationale.

CREATE TABLE "bracket_stages"
(
    "id"            BIGINT PRIMARY KEY,
    "tournament_id" VARCHAR NOT NULL,
    "name"          VARCHAR NOT NULL,
    "format"        VARCHAR NOT NULL,
    "ordinal"       INTEGER NOT NULL,
    "config"        JSONB,
    CONSTRAINT "bracket_stages_tournament_id_ordinal_key" UNIQUE ("tournament_id", "ordinal")
);

ALTER TABLE "bracket_matches"
    ADD COLUMN "stage_id" BIGINT REFERENCES "bracket_stages" ("id") ON DELETE SET NULL;
