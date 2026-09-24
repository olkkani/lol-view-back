INSERT INTO "tournaments" ("id", "tournament_name", "start_date", "end_date", "league_id")
VALUES
    (9000, 'LCK Split 3 2026', '2026-07-28', '2026-09-20', 1004),
    (9001, 'T1 vs GenG Head-to-Head', '2026-01-01', '2026-12-31', 1004),
    (9002, 'asian_games_2026', '2026-09-28', '2026-10-02', 1007);

INSERT INTO "tournament_external_ids" ("id", "entity_id", "provider", "external_id")
VALUES
    (9002, 9002, 'LOLESPORTS', '117229236387441643');
