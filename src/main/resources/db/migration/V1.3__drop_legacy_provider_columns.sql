-- V1.3__drop_legacy_provider_columns.sql
--
-- DEPLOY THIS SEPARATELY FROM V1.2. Run only after confirming in production
-- that migration_audit_log's row_count_before/row_count_after values match
-- for both 'league_external_ids' and 'tournament_external_ids' (see spec's
-- CEO Review Amendments — this is the documented pre-deploy manual
-- verification step, not an automated gate).

ALTER TABLE "leagues" DROP COLUMN "league_api_id";
ALTER TABLE "tournaments" DROP COLUMN "tournament_api_id";
DROP TABLE "tournament_provider_mappings";
