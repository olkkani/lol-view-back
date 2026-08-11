INSERT INTO "clubs" ("id", "is_active", "region", "founded_date", "disbanded_date")
VALUES
    (1000, true, 'lck', '2020-01-01', NULL),
    (1001, true, 'lck', '2020-01-01', NULL),
    (1002, true, 'lck', '2020-01-01', NULL),
    (1003, true, 'lck', '2020-01-01', NULL),
    (1004, true, 'lck', '2020-01-01', NULL),
    (1005, true, 'lck', '2020-01-01', NULL),
    (1006, true, 'lck', '2020-01-01', NULL),
    (1007, true, 'lck', '2020-01-01', NULL),
    (1008, true, 'lck', '2020-01-01', NULL),
    (1009, true, 'lck', '2020-01-01', NULL);

INSERT INTO "club_profiles"
("id", "club_name", "abbreviation", "effective_from", "effective_to", "logo_url", "club_id")
VALUES
    (1000, 'Gen.G',              'GEN', '2013-09-07', NULL, 'http://static.lolesports.com/teams/1773829250929_GENGLOGO_GOLD.png',     1000),
    (1001, 'BNK FEARX',          'BFX', '2016-12-31', NULL, 'http://static.lolesports.com/teams/1734691810721_BFXfullcolorfordarkbg.png', 1001),
    (1002, 'Dplus Kia',          'DK',  '2026-08-11', NULL, 'http://static.lolesports.com/teams/1673260049703_DPlusKIALOGO11.png',     1002),
    (1003, 'T1',                 'T1',  '2020-01-01', NULL, 'http://static.lolesports.com/teams/1726801573959_539px-T1_2019_full_allmode.png', 1003),
    (1004, 'DN SOOPers',         'DNS', '2025-12-01', NULL, 'http://static.lolesports.com/teams/1767340467921_DN_SOOPerslogo_profile.webp', 1004),
    (1005, 'KIWOOM DRX',         'KRX', '2026-03-01', NULL, 'http://static.lolesports.com/teams/1774247803537_horizontal_EN_Wh.png',   1005),
    (1006, 'Nongshim RedForce',  'NS',  '2020-12-01', NULL, 'http://static.lolesports.com/teams/NSFullonDark.png',                     1006),
    (1007, 'kt Rolster',         'KT',  '2024-12-01', NULL, 'http://static.lolesports.com/teams/kt_darkbackground.png',                1007),
    (1008, 'HANJIN BRION',       'BRO', '2026-03-01', NULL, 'http://static.lolesports.com/teams/1716454325887_Nowyprojekt.png',        1008),
    (1009, 'Hanwha Life Esports','HLE', '2018-04-01', NULL, 'http://static.lolesports.com/teams/1631819564399_hle-2021-worlds.png',    1009);