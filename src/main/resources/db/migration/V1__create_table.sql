CREATE TABLE "clubs"
(
    "id"             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "is_active"      boolean,
    "founded_date"   DATE,
    "disbanded_date" DATE
);

CREATE TABLE "club_profiles"
(
    "id"             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "club_name"      varchar,
    "abbreviation"   varchar,
    "logo_url"       varchar,
    "effective_from" DATE,
    "effective_to"   DATE,
    "club_id"        bigint
);

CREATE TABLE "users"
(
    "id" bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY
);

CREATE TABLE "user_followed_clubs"
(
    "id"      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "user_id" bigint,
    "club_id" bigint
);

CREATE TABLE "user_followed_leagues"
(
    "id"        bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "user_id"   bigint,
    "league_id" bigint
);

CREATE TABLE "leagues"
(
    "id"              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "league_name"     varchar,
    "league_logo_url" varchar,
    "is_active"       boolean,
    "league_api_id"   varchar,
    "league_cycle"    varchar
);

CREATE TABLE "tournaments"
(
    "id"                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "tournament_name"   varchar,
    "start_date"        DATE,
    "end_date"          DATE,
    "tournament_api_id" varchar,
    "league_id"         BIGINT
);

CREATE TABLE "matches"
(
    "id"            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "start_time"    timestamp,
    "match_type"    varchar,
    "match_state"   varchar,
    "match_label"   varchar,
    "match_api_id"  varchar,
    "tournament_id" BIGINT
);

CREATE TABLE "match_participants"
(
    "id"              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "is_win"          boolean,
    "score"           integer,
    "match_id"        BIGINT,
    "club_id"         BIGINT,
    "club_profile_id" BIGINT
);

CREATE TABLE "poll_schedules"
(
    "id"            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "target_type"   VARCHAR,
    "target_id"     BIGINT,
    "status"        VARCHAR,
    "next_check_at" timestamp,
    "fail_count"    integer DEFAULT 0
);

CREATE TABLE "league_recurrence_windows"
(
    "id"                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "label"                 varchar,
    "sequence_order"        integer,
    "league_id"             BIGINT,
    "interval_year"         integer,
    "start_date"            date
);

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

ALTER TABLE "league_recurrence_windows"
    ADD FOREIGN KEY ("league_id") REFERENCES "leagues" ("id") DEFERRABLE;
