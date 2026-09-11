-- Unique constraint on abbreviation closes the race where two concurrent poll
-- runs could each miss-find the canonical TBD profile and insert their own
-- copy, producing two different club_profile_id values for "TBD" (see design
-- doc's "verified edge cases" section). Seeding the row here, rather than
-- creating it lazily from application code, means resolveClubProfiles only
-- ever reads it — there is no runtime creation path left to race on.
--
-- club_profiles is an SCD-2 temporal table (effective_from/effective_to):
-- a club can legitimately have multiple profile rows over time (rebrands),
-- so a blanket UNIQUE(abbreviation) is too broad. Scope the constraint to
-- only the TBD sentinel value via a partial unique index — this still
-- guarantees at most one row with abbreviation = 'TBD', which is all this
-- feature needs, while leaving every other club free to have multiple rows
-- across eras.
CREATE UNIQUE INDEX "club_profiles_tbd_abbreviation_key" ON "club_profiles" ("abbreviation") WHERE "abbreviation" = 'TBD';

-- Use sentinel id 999999999 for TBD row (not in V1.3 seed data; safe from TSID generation)
-- effective_from is fixed far in the past (not CURRENT_DATE) so the sentinel row's
-- visibility never depends on migration wall-clock time relative to the match dates
-- it needs to resolve for — reads filter on effective_from <= <match date>.
-- Postgres only infers a partial unique index for ON CONFLICT when the INSERT's own
-- ON CONFLICT ... WHERE predicate matches the index's predicate exactly (unlike a
-- non-partial unique constraint, which ON CONFLICT (column-list) can target directly) —
-- so the WHERE clause below is required, not optional, for this statement to target
-- club_profiles_tbd_abbreviation_key. Confirmed empirically: the plain
-- ON CONFLICT ("abbreviation") DO NOTHING form fails migration with Postgres error 42P10
-- ("no unique or exclusion constraint matching the ON CONFLICT specification") once the
-- constraint above is a partial index instead of a table-wide UNIQUE constraint.
INSERT INTO "club_profiles" ("id", "club_name", "abbreviation", "logo_url", "effective_from")
VALUES (999999999, 'TBD', 'TBD', '', '1970-01-01')
ON CONFLICT ("abbreviation") WHERE "abbreviation" = 'TBD' DO NOTHING;
