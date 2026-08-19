INSERT INTO leagues (id, league_api_id, league_name, logo_url, logo_backdrop, is_active)
VALUES
    (1001, '98767975604431411', '월드 챔피언십', 'http://static.lolesports.com/leagues/1592594612171_WorldsDarkBG.png', 'ANY', true ),
    (1002, '98767991325878492', 'MSI', 'http://static.lolesports.com/leagues/1592594634248_MSIDarkBG.png', 'ANY', true ),
    (1003, '113464388705111224', 'First Stand', 'http://static.lolesports.com/leagues/1740042025201_RG_LOL_FIRST_STAND_LOGO_VOLT_ALPHA.png', 'ANY', true ),
    (1004, '98767991310872058', 'LCK', 'http://static.lolesports.com/leagues/lck-color-on-black.png', 'ANY', true ),
    (1005, '116838530616006090', 'Esports World Cup', 'http://static.lolesports.com/leagues/1782814488205_EWC26_PRIMARY_ABBREVIATED_LOGO_WHITE.png', 'ANY', true ),
    (1006, '116929044967296666', 'KeSPA Cup', 'http://static.lolesports.com/leagues/1784195628750_original1.png', 'ANY', true );

INSERT INTO league_recurrence_windows (id, label, sequence_order, league_id, interval_year, start_date)
VALUES
    (2001, 'LCK Split 1', 1, 1004, 1, '2026-01-03'),
    (2002, 'LCK Split 2', 2, 1004, 1, '2026-03-31'),
    (2003, 'LCK Split 3', 3, 1004, 1, '2026-07-28'),
    (2004, 'MSI', 1, 1002, 1, '2026-06-27'),
    (2005, '월드 챔피언십', 1, 1001, 1, '2025-11-09'),
    (2006, 'First Stand', 1, 1003, 1, '2026-03-16'),
    (2007, 'Esports World Cup', 1, 1005, 1, '2026-07-14'),
    (2008, 'KeSPA Cup', 1, 1006, 1, '2026-07-19');