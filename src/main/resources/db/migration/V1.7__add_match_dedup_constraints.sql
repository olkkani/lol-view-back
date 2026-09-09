ALTER TABLE "matches"
    ADD CONSTRAINT "matches_match_api_id_key" UNIQUE ("match_api_id");

ALTER TABLE "match_participants"
    ADD CONSTRAINT "match_participants_match_id_club_profile_id_key" UNIQUE ("match_id", "club_profile_id");
