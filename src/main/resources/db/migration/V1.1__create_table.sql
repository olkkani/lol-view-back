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
    "is_active"     boolean,
    "league_api_id" varchar
);

CREATE TABLE "tournaments"
(
    "id"                BIGINT PRIMARY KEY,
    "tournament_name"   varchar,
    "start_date"        DATE,
    "end_date"          DATE,
    "tournament_api_id" varchar,
    "league_id"         BIGINT
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


CREATE TABLE "league_recurrence_windows"
(
    "id"             BIGINT PRIMARY KEY,
    "label"          varchar,
    "sequence_order" integer,
    "league_id"      BIGINT,
    "interval_year"  integer,
    "start_date"     date
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

ALTER TABLE "league_recurrence_windows"
    ADD FOREIGN KEY ("league_id") REFERENCES "leagues" ("id") DEFERRABLE;

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
-- across eras. See V1.8 (pre-squash) for the original race-condition rationale.
CREATE UNIQUE INDEX "club_profiles_tbd_abbreviation_key" ON "club_profiles" ("abbreviation") WHERE "abbreviation" = 'TBD';

INSERT INTO leagues (id, league_api_id, league_name, logo_url, logo_backdrop, is_active)
VALUES
    (1001, '98767975604431411', '월드 챔피언십', 'http://static.lolesports.com/leagues/1592594612171_WorldsDarkBG.png', 'ANY', true ),
    (1002, '98767991325878492', 'MSI', 'http://static.lolesports.com/leagues/1592594634248_MSIDarkBG.png', 'ANY', true ),
    (1003, '113464388705111224', 'First Stand', 'http://static.lolesports.com/leagues/1740042025201_RG_LOL_FIRST_STAND_LOGO_VOLT_ALPHA.png', 'ANY', true ),
    (1004, '98767991310872058', 'LCK', 'http://static.lolesports.com/leagues/lck-color-on-black.png', 'ANY', true ),
    (1005, '116838530616006090', 'Esports World Cup', 'http://static.lolesports.com/leagues/1782814488205_EWC26_PRIMARY_ABBREVIATED_LOGO_WHITE.png', 'ANY', true ),
    (1006, '116929044967296666', 'KeSPA Cup', 'http://static.lolesports.com/leagues/1784195628750_original1.png', 'ANY', true );

INSERT INTO league_recurrence_windows (id, label, sequence_order, league_id, interval_year, start_date)
VALUES
    (2001, 'LCK Split 1', 1, 1004, 1, '2026-01-03'),
    (2002, 'LCK Split 2', 2, 1004, 1, '2026-03-31'),
    (2003, 'LCK Split 3', 3, 1004, 1, '2026-07-28'),
    (2004, 'MSI', 1, 1002, 1, '2026-06-27'),
    (2005, '월드 챔피언십', 1, 1001, 1, '2025-11-09'),
    (2006, 'First Stand', 1, 1003, 1, '2026-03-16'),
    (2007, 'Esports World Cup', 1, 1005, 1, '2026-07-14'),
    (2008, 'KeSPA Cup', 1, 1006, 1, '2026-07-19');

INSERT INTO "clubs" ("id", "is_active", "founded_date", "disbanded_date")
VALUES
    (1000, true, '2020-01-01', NULL),
    (1001, true, '2020-01-01', NULL),
    (1002, true, '2020-01-01', NULL),
    (1003, true, '2020-01-01', NULL),
    (1004, true, '2020-01-01', NULL),
    (1005, true, '2020-01-01', NULL),
    (1006, true, '2020-01-01', NULL),
    (1007, true, '2020-01-01', NULL),
    (1008, true, '2020-01-01', NULL),
    (1009, true, '2020-01-01', NULL);

INSERT INTO "club_profiles"
("id", "club_name", "abbreviation", "effective_from", "effective_to", "logo_url", "logo_backdrop", "club_id", "region")
VALUES
    (1000, 'Gen.G',              'GEN', '2013-09-07', NULL, 'http://static.lolesports.com/teams/1773829250929_GENGLOGO_GOLD.png',     'ANY',  1000, 'LCK'),
    (1001, 'BNK FEARX',          'BFX', '2016-12-31', NULL, 'http://static.lolesports.com/teams/1734691810721_BFXfullcolorfordarkbg.png', 'ANY',  1001, 'LCK'),
    (1002, 'Dplus Kia',          'DK',  '2026-08-11', NULL, 'http://static.lolesports.com/teams/1673260049703_DPlusKIALOGO11.png',     'DARK', 1002, 'LCK'),
    (1003, 'T1',                 'T1',  '2020-01-01', NULL, 'http://static.lolesports.com/teams/1726801573959_539px-T1_2019_full_allmode.png', 'ANY',  1003, 'LCK'),
    (1004, 'DN SOOPers',         'DNS', '2025-12-01', NULL, 'http://static.lolesports.com/teams/1767340467921_DN_SOOPerslogo_profile.webp', 'ANY',  1004, 'LCK'),
    (1005, 'KIWOOM DRX',         'KRX', '2026-03-01', NULL, 'http://static.lolesports.com/teams/1774247803537_horizontal_EN_Wh.png',   'DARK', 1005, 'LCK'),
    (1006, 'Nongshim RedForce',  'NS',  '2020-12-01', NULL, 'http://static.lolesports.com/teams/NSFullonDark.png',                     'ANY',  1006, 'LCK'),
    (1007, 'kt Rolster',         'KT',  '2024-12-01', NULL, 'http://static.lolesports.com/teams/kt_darkbackground.png',                'ANY',  1007, 'LCK'),
    (1008, 'HANJIN BRION',       'BRO', '2026-03-01', NULL, 'http://static.lolesports.com/teams/1716454325887_Nowyprojekt.png',        'ANY',  1008, 'LCK'),
    (1009, 'Hanwha Life Esports','HLE', '2018-04-01', NULL, 'http://static.lolesports.com/teams/1631819564399_hle-2021-worlds.png',    'ANY',  1009, 'LCK');

-- Use sentinel id 999999999 for TBD row (not in the seed data above; safe from TSID generation)
-- effective_from is fixed far in the past (not CURRENT_DATE) so the sentinel row's
-- visibility never depends on migration wall-clock time relative to the match dates
-- it needs to resolve for — reads filter on effective_from <= <match date>.
INSERT INTO "club_profiles" ("id", "club_name", "abbreviation", "logo_url", "effective_from")
VALUES (999999999, 'TBD', 'TBD', '', '1970-01-01');
