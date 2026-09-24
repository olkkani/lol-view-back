-- Minimum-bootstrap data: real LCK/international leagues and clubs, required
-- for the app to have anything meaningful to show. Runs in every environment
-- (local and prod) via both persistence-local and persistence-prod flyway
-- locations — unlike db/test-fixtures, which is local-only dummy data for
-- integration tests.

INSERT INTO "leagues" ("id", "league_name", "logo_url", "logo_backdrop", "is_active")
VALUES
    (1001, '월드 챔피언십', 'http://static.lolesports.com/leagues/1592594612171_WorldsDarkBG.png', 'ANY', true ),
    (1002, 'MSI', 'http://static.lolesports.com/leagues/1592594634248_MSIDarkBG.png', 'ANY', true ),
    (1003, 'First Stand', 'http://static.lolesports.com/leagues/1740042025201_RG_LOL_FIRST_STAND_LOGO_VOLT_ALPHA.png', 'ANY', true ),
    (1004, 'LCK', 'http://static.lolesports.com/leagues/lck-color-on-black.png', 'ANY', true ),
    (1005, 'Esports World Cup', 'http://static.lolesports.com/leagues/1782814488205_EWC26_PRIMARY_ABBREVIATED_LOGO_WHITE.png', 'ANY', true ),
    (1006, 'KeSPA Cup', 'http://static.lolesports.com/leagues/1784195628750_original1.png', 'ANY', true ),
    (1007, 'asian_games', 'http://static.lolesports.com/leagues/1788770828308_images.png', 'ANY', true );

INSERT INTO "league_external_ids" ("id", "entity_id", "provider", "external_id")
VALUES
    (3001, 1001, 'LOLESPORTS', '98767975604431411'),
    (3002, 1002, 'LOLESPORTS', '98767991325878492'),
    (3003, 1003, 'LOLESPORTS', '113464388705111224'),
    (3004, 1004, 'LOLESPORTS', '98767991310872058'),
    (3005, 1005, 'LOLESPORTS', '116838530616006090'),
    (3006, 1006, 'LOLESPORTS', '116929044967296666'),
    (3007, 1007, 'LOLESPORTS', '117228885404001005');

INSERT INTO "league_recurrence_windows" ("id", "label", "tie_break_priority", "league_id", "interval_year", "start_date")
VALUES
    (2001, 'LCK Split 1', 1, 1004, 1, '2026-01-03'),
    (2002, 'LCK Split 2', 2, 1004, 1, '2026-03-31'),
    (2003, 'LCK Split 3', 3, 1004, 1, '2026-07-28'),
    (2004, 'MSI', 1, 1002, 1, '2026-06-27'),
    (2005, '월드 챔피언십', 1, 1001, 1, '2025-11-09'),
    (2006, 'First Stand', 1, 1003, 1, '2026-03-16'),
    (2007, 'Esports World Cup', 1, 1005, 1, '2026-07-14'),
    (2008, 'KeSPA Cup', 1, 1006, 1, '2026-07-19'),
    (2009, 'asian_games', 1, 1007, 4, '2026-09-28');

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
