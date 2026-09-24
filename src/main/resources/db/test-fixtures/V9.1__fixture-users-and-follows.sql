INSERT INTO "users" ("id")
VALUES
    (9000),
    (9001);

INSERT INTO "user_identities" ("id", "user_id", "provider", "provider_user_id")
VALUES
    (9000, 9000, 'GOOGLE', 'seed-google-sub-9000'),
    (9001, 9001, 'GOOGLE', 'seed-google-sub-9001');

INSERT INTO "user_followed_clubs" ("id", "user_id", "club_id")
VALUES
    (9000, 9000, 1000),
    (9001, 9000, 1003),
    (9002, 9001, 1007);

INSERT INTO "user_followed_leagues" ("id", "user_id", "league_id")
VALUES
    (9000, 9000, 1004),
    (9001, 9001, 1001);
