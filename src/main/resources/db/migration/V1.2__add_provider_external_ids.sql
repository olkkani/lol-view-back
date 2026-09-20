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

CREATE TABLE "migration_audit_log"
(
    "id"                bigint PRIMARY KEY,
    "migration_version" varchar   NOT NULL,
    "table_name"        varchar   NOT NULL,
    "row_count_before"  integer   NOT NULL,
    "row_count_after"   integer   NOT NULL,
    "recorded_at"       timestamp NOT NULL DEFAULT now()
);

-- Backfill leagues.league_api_id -> league_external_ids (provider=LOLESPORTS).
-- id values below start at 900000000000000001 to avoid clashing with TSID-
-- generated ids produced by the application at runtime (TSIDs are time-
-- ordered 64-bit values well below this range for the foreseeable future;
-- picking a manifestly separate numeric range for migration-seeded rows is
-- the same defensive pattern used for sentinel ids in this codebase, e.g.
-- V1.1's club_profiles TBD row id 999999999).
INSERT INTO "league_external_ids" ("id", "entity_id", "provider", "external_id")
SELECT
    900000000000000000 + ROW_NUMBER() OVER (ORDER BY id),
    id,
    'LOLESPORTS',
    league_api_id
FROM "leagues"
WHERE league_api_id IS NOT NULL;

-- Backfill tournaments.tournament_api_id -> tournament_external_ids (provider=LOLESPORTS).
INSERT INTO "tournament_external_ids" ("id", "entity_id", "provider", "external_id")
SELECT
    900000000000100000 + ROW_NUMBER() OVER (ORDER BY id),
    id,
    'LOLESPORTS',
    tournament_api_id
FROM "tournaments"
WHERE tournament_api_id IS NOT NULL;

-- Backfill tournament_provider_mappings -> tournament_external_ids, two rows
-- per existing mapping (LOLESPORTS + PANDASCORE). The mapping table stores
-- the raw lolesports API id string, not the tournaments.id FK directly, so
-- this join reconstructs the entity_id link via tournaments.tournament_api_id.
-- ON CONFLICT DO NOTHING guards the LOLESPORTS insert here because a tournament
-- may already have been backfilled by the direct tournament_api_id pass above.
INSERT INTO "tournament_external_ids" ("id", "entity_id", "provider", "external_id")
SELECT
    900000000000200000 + ROW_NUMBER() OVER (ORDER BY m.id),
    t.id,
    'LOLESPORTS',
    m.lolesports_tournament_id
FROM "tournament_provider_mappings" m
JOIN "tournaments" t ON t.tournament_api_id = m.lolesports_tournament_id
ON CONFLICT ("entity_id", "provider") DO NOTHING;

INSERT INTO "tournament_external_ids" ("id", "entity_id", "provider", "external_id")
SELECT
    900000000000300000 + ROW_NUMBER() OVER (ORDER BY m.id),
    t.id,
    'PANDASCORE',
    m.pandascore_tournament_id
FROM "tournament_provider_mappings" m
JOIN "tournaments" t ON t.tournament_api_id = m.lolesports_tournament_id;

-- Durable audit trail: record before/after row counts per source table so a
-- production incident weeks later can verify the backfill ran correctly
-- without relying on ephemeral deploy logs.
INSERT INTO "migration_audit_log" ("id", "migration_version", "table_name", "row_count_before", "row_count_after")
SELECT
    900000000000400001,
    'V1.2',
    'league_external_ids',
    (SELECT COUNT(*) FROM "leagues" WHERE league_api_id IS NOT NULL),
    (SELECT COUNT(*) FROM "league_external_ids" WHERE provider = 'LOLESPORTS');

INSERT INTO "migration_audit_log" ("id", "migration_version", "table_name", "row_count_before", "row_count_after")
SELECT
    900000000000400002,
    'V1.2',
    'tournament_external_ids',
    (SELECT COUNT(*) FROM "tournaments" WHERE tournament_api_id IS NOT NULL)
        + (SELECT COUNT(*) FROM "tournament_provider_mappings") * 2,
    (SELECT COUNT(*) FROM "tournament_external_ids");
