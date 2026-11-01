CREATE TABLE "clubs"
(
    "id"             bigint PRIMARY KEY,
    "is_active"      boolean,
    "founded_date"   DATE,
    "disbanded_date" DATE
);

CREATE TABLE "club_profiles"
(
    "id"             bigint PRIMARY KEY,
    "club_name"      varchar,
    "abbreviation"   varchar,
    "logo_url"       varchar,
    "logo_backdrop"  varchar,
    "effective_from" DATE,
    "effective_to"   DATE,
    "region"         varchar,
    "club_id"        bigint
);

CREATE TABLE "users"
(
    "id"   bigint PRIMARY KEY,
    "role" VARCHAR(20) NOT NULL DEFAULT 'USER'
);

CREATE TABLE "user_followed_clubs"
(
    "id"      bigint PRIMARY KEY,
    "user_id" bigint,
    "club_id" bigint
);

CREATE TABLE "user_followed_leagues"
(
    "id"        bigint PRIMARY KEY,
    "user_id"   bigint,
    "league_id" bigint
);

CREATE TABLE "leagues"
(
    "id"            bigint PRIMARY KEY,
    "league_name"   varchar,
    "logo_url"      varchar,
    "logo_backdrop" varchar,
    "is_active"     boolean
);

CREATE TABLE "tournaments"
(
    "id"              BIGINT PRIMARY KEY,
    "tournament_name" varchar,
    "start_date"      DATE,
    "end_date"        DATE,
    "league_id"       BIGINT
);

CREATE TABLE "matches"
(
    "id"            BIGINT PRIMARY KEY,
    "start_time"    timestamp,
    "match_type"    varchar,
    "match_state"   varchar,
    "match_label"   varchar,
    "match_api_id"  varchar,
    "tournament_id" BIGINT
);

CREATE TABLE "match_participants"
(
    "id"              BIGINT PRIMARY KEY,
    "match_id"        BIGINT,
    "club_id"         BIGINT,
    "club_profile_id" BIGINT
);

CREATE TABLE "match_sets"
(
    "id"              BIGINT PRIMARY KEY,
    "set_api_id"      varchar,
    "set_number"      integer,
    "set_win_club_id" BIGINT,
    "match_id"        BIGINT
);


CREATE TABLE "user_identities"
(
    "id"               bigint PRIMARY KEY,
    "user_id"          bigint,
    "provider"         varchar NOT NULL,
    "provider_user_id" varchar NOT NULL
);

CREATE TABLE "refresh_tokens"
(
    "id"         bigint PRIMARY KEY,
    "user_id"    bigint NOT NULL,
    "token_hash" varchar NOT NULL,
    "expires_at" timestamp NOT NULL,
    "created_at" timestamp NOT NULL,
    "revoked_at" timestamp
);

CREATE TABLE "league_external_ids"
(
    "id"          bigint PRIMARY KEY,
    "entity_id"   bigint  NOT NULL,
    "provider"    varchar NOT NULL,
    "external_id" varchar NOT NULL,
    CONSTRAINT "league_external_ids_entity_provider_key" UNIQUE ("entity_id", "provider")
);

CREATE TABLE "tournament_external_ids"
(
    "id"          bigint PRIMARY KEY,
    "entity_id"   bigint  NOT NULL,
    "provider"    varchar NOT NULL,
    "external_id" varchar NOT NULL,
    CONSTRAINT "tournament_external_ids_entity_provider_key" UNIQUE ("entity_id", "provider")
);

CREATE TABLE "club_external_ids"
(
    "id"          bigint PRIMARY KEY,
    "entity_id"   bigint  NOT NULL,
    "provider"    varchar NOT NULL,
    "external_id" varchar NOT NULL,
    CONSTRAINT "club_external_ids_entity_provider_key" UNIQUE ("entity_id", "provider")
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

ALTER TABLE "club_profiles"
    ADD FOREIGN KEY ("club_id") REFERENCES "clubs" ("id") DEFERRABLE;

ALTER TABLE "user_followed_clubs"
    ADD FOREIGN KEY ("user_id") REFERENCES "users" ("id") DEFERRABLE;

ALTER TABLE "user_followed_clubs"
    ADD FOREIGN KEY ("club_id") REFERENCES "clubs" ("id") DEFERRABLE;

ALTER TABLE "user_followed_leagues"
    ADD FOREIGN KEY ("user_id") REFERENCES "users" ("id") DEFERRABLE;

ALTER TABLE "user_followed_leagues"
    ADD FOREIGN KEY ("league_id") REFERENCES "leagues" ("id") DEFERRABLE;

ALTER TABLE "tournaments"
    ADD FOREIGN KEY ("league_id") REFERENCES "leagues" ("id") DEFERRABLE;

ALTER TABLE "matches"
    ADD FOREIGN KEY ("tournament_id") REFERENCES "tournaments" ("id") DEFERRABLE;

ALTER TABLE "match_participants"
    ADD FOREIGN KEY ("match_id") REFERENCES "matches" ("id") DEFERRABLE;

ALTER TABLE "match_participants"
    ADD FOREIGN KEY ("club_id") REFERENCES "clubs" ("id") DEFERRABLE;

ALTER TABLE "match_participants"
    ADD FOREIGN KEY ("club_profile_id") REFERENCES "club_profiles" ("id") DEFERRABLE;

ALTER TABLE "match_sets"
    ADD FOREIGN KEY ("match_id") REFERENCES "matches" ("id") DEFERRABLE;

ALTER TABLE "match_sets"
    ADD FOREIGN KEY ("set_win_club_id") REFERENCES "clubs" ("id") DEFERRABLE;

ALTER TABLE "user_identities"
    ADD FOREIGN KEY ("user_id") REFERENCES "users" ("id") DEFERRABLE;

ALTER TABLE "user_identities"
    ADD CONSTRAINT "uq_user_identities_provider_provider_user_id"
        UNIQUE ("provider", "provider_user_id");

ALTER TABLE "refresh_tokens"
    ADD FOREIGN KEY ("user_id") REFERENCES "users" ("id") DEFERRABLE;

ALTER TABLE "refresh_tokens"
    ADD CONSTRAINT "uq_refresh_tokens_token_hash"
        UNIQUE ("token_hash");

CREATE INDEX "idx_refresh_tokens_user_id" ON "refresh_tokens" ("user_id");

ALTER TABLE "matches"
    ADD CONSTRAINT "matches_match_api_id_key" UNIQUE ("match_api_id");

ALTER TABLE "match_participants"
    ADD CONSTRAINT "match_participants_match_id_club_profile_id_key" UNIQUE ("match_id", "club_profile_id");

-- club_profiles is an SCD-2 temporal table (effective_from/effective_to): a club can
-- legitimately have multiple profile rows over time (rebrands), so a blanket
-- UNIQUE(abbreviation) is too broad. Scope the constraint to only the TBD sentinel
-- value via a partial unique index — this still guarantees at most one row with
-- abbreviation = 'TBD', while leaving every other club free to have multiple rows
-- across eras.
CREATE UNIQUE INDEX "club_profiles_tbd_abbreviation_key" ON "club_profiles" ("abbreviation") WHERE "abbreviation" = 'TBD';
