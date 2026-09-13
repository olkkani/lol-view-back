CREATE TABLE "tournament_provider_mappings"
(
    "id"                       BIGINT PRIMARY KEY,
    "lolesports_tournament_id" VARCHAR NOT NULL,
    "pandascore_tournament_id" VARCHAR NOT NULL,
    CONSTRAINT "tournament_provider_mappings_lolesports_tournament_id_key" UNIQUE ("lolesports_tournament_id")
);

CREATE TABLE "bracket_matches"
(
    "id"               BIGINT PRIMARY KEY,
    "tournament_id"    VARCHAR   NOT NULL,
    "match_id"         BIGINT    NOT NULL,
    "name"             VARCHAR,
    "status"           VARCHAR,
    "slug"             VARCHAR,
    "winner_id"        BIGINT,
    "winner_type"      VARCHAR,
    "begin_at"         TIMESTAMP,
    "end_at"           TIMESTAMP,
    "scheduled_at"     TIMESTAMP,
    "previous_matches" JSONB,
    "opponents"        JSONB,
    "results"          JSONB,
    CONSTRAINT "bracket_matches_tournament_id_match_id_key" UNIQUE ("tournament_id", "match_id")
);
