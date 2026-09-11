-- Unique constraint on abbreviation closes the race where two concurrent poll
-- runs could each miss-find the canonical TBD profile and insert their own
-- copy, producing two different club_profile_id values for "TBD" (see design
-- doc's "verified edge cases" section). Seeding the row here, rather than
-- creating it lazily from application code, means resolveClubProfiles only
-- ever reads it — there is no runtime creation path left to race on.

ALTER TABLE "club_profiles"
    ADD CONSTRAINT "club_profiles_abbreviation_key" UNIQUE ("abbreviation");

-- Use sentinel id 999999999 for TBD row (not in V1.3 seed data; safe from TSID generation)
INSERT INTO "club_profiles" ("id", "club_name", "abbreviation", "logo_url", "effective_from")
VALUES (999999999, 'TBD', 'TBD', '', CURRENT_DATE)
ON CONFLICT ("abbreviation") DO NOTHING;
