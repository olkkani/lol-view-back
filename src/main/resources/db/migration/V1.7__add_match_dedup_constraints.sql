-- Deduplicate existing data before enforcing uniqueness, so this migration is safe to run
-- against a production database that already has duplicate matches / match_participants rows
-- (see Goal: production has been double-inserting on every re-poll cycle before this fix).
-- This block is idempotent: a database with no duplicates has these DELETEs affect zero rows.

-- Step 1: dedup "match_participants" by (match_id, club_profile_id) — keep the lowest id.
DELETE
FROM "match_participants" "mp"
    USING "match_participants" "mp_keep"
WHERE "mp"."match_id" = "mp_keep"."match_id"
  AND "mp"."club_profile_id" = "mp_keep"."club_profile_id"
  AND "mp"."id" > "mp_keep"."id";

-- Step 2: for every "matches" row about to be removed in Step 3 (a duplicate match_api_id,
-- not the one being kept), delete ALL of its dependent rows first so Step 3 does not violate
-- the "match_sets"/"match_participants" foreign keys or leave orphaned rows behind.
DELETE
FROM "match_sets" "ms"
WHERE "ms"."match_id" IN (SELECT "m"."id"
                           FROM "matches" "m"
                                    JOIN "matches" "m_keep"
                                         ON "m"."match_api_id" = "m_keep"."match_api_id"
                           WHERE "m"."id" > "m_keep"."id");

DELETE
FROM "match_participants" "mp"
WHERE "mp"."match_id" IN (SELECT "m"."id"
                           FROM "matches" "m"
                                    JOIN "matches" "m_keep"
                                         ON "m"."match_api_id" = "m_keep"."match_api_id"
                           WHERE "m"."id" > "m_keep"."id");

-- Step 3: dedup "matches" by match_api_id — keep the lowest id.
DELETE
FROM "matches" "m"
    USING "matches" "m_keep"
WHERE "m"."match_api_id" = "m_keep"."match_api_id"
  AND "m"."id" > "m_keep"."id";

ALTER TABLE "matches"
    ADD CONSTRAINT "matches_match_api_id_key" UNIQUE ("match_api_id");

ALTER TABLE "match_participants"
    ADD CONSTRAINT "match_participants_match_id_club_profile_id_key" UNIQUE ("match_id", "club_profile_id");
