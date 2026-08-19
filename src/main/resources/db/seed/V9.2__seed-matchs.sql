INSERT INTO "tournaments" ("id", "tournament_name", "start_date", "end_date", "tournament_api_id", "league_id")
VALUES
    (9000, 'LCK Split 3 2026', '2026-07-28', '2026-09-20', '113000000000000001', 1004);

INSERT INTO "matches" ("id", "start_time", "match_type", "match_state", "match_label", "match_api_id", "tournament_id")
VALUES
    (9000, (CURRENT_DATE - INTERVAL '1 day') + TIME '09:00:00', 'BO3', 'FINISHED', 'Week 1 Day 1', '113000000000000101', 9000),
    (9001, (CURRENT_DATE - INTERVAL '1 day') + TIME '11:00:00', 'BO3', 'FINISHED', 'Week 1 Day 1', '113000000000000102', 9000),
    (9002, CURRENT_DATE + TIME '09:00:00', 'BO3', 'ONGOING', 'Week 1 Day 2', '113000000000000103', 9000),
    (9003, CURRENT_DATE + TIME '19:00:00', 'BO3', 'SCHEDULED', 'Week 1 Day 2', '113000000000000104', 9000),
    (9004, (CURRENT_DATE + INTERVAL '1 day') + TIME '09:00:00', 'BO5', 'SCHEDULED', 'Week 1 Day 3', '113000000000000105', 9000),
    -- 어제 3건
    (9005, (CURRENT_DATE - INTERVAL '1 day') + TIME '13:00:00', 'BO3', 'FINISHED', 'Week 1 Day 1', '113000000000000106', 9000),
    (9006, (CURRENT_DATE - INTERVAL '1 day') + TIME '15:00:00', 'BO3', 'FINISHED', 'Week 1 Day 1', '113000000000000107', 9000),
    (9007, (CURRENT_DATE - INTERVAL '1 day') + TIME '17:00:00', 'BO3', 'FINISHED', 'Week 1 Day 1', '113000000000000108', 9000),
    -- 오늘, 1시간/2시간 전에 종료
    (9008, NOW() - INTERVAL '1 hour', 'BO3', 'FINISHED', 'Week 1 Day 2', '113000000000000109', 9000),
    (9009, NOW() - INTERVAL '2 hour', 'BO3', 'FINISHED', 'Week 1 Day 2', '113000000000000110', 9000),
    -- 현재 진행 중, 1:0 스코어
    (9010, NOW(), 'BO3', 'ONGOING', 'Week 1 Day 2', '113000000000000111', 9000),
    -- 오늘, 1시간/2시간 뒤 예정
    (9011, NOW() + INTERVAL '1 hour', 'BO3', 'SCHEDULED', 'Week 1 Day 2', '113000000000000112', 9000),
    (9012, NOW() + INTERVAL '2 hour', 'BO3', 'SCHEDULED', 'Week 1 Day 2', '113000000000000113', 9000),
    -- 내일 2건
    (9013, (CURRENT_DATE + INTERVAL '1 day') + TIME '11:00:00', 'BO3', 'SCHEDULED', 'Week 1 Day 3', '113000000000000114', 9000),
    (9014, (CURRENT_DATE + INTERVAL '1 day') + TIME '13:00:00', 'BO3', 'SCHEDULED', 'Week 1 Day 3', '113000000000000115', 9000),
    -- +3일 3건
    (9015, (CURRENT_DATE + INTERVAL '3 day') + TIME '09:00:00', 'BO3', 'SCHEDULED', 'Week 1 Day 4', '113000000000000116', 9000),
    (9016, (CURRENT_DATE + INTERVAL '3 day') + TIME '11:00:00', 'BO3', 'SCHEDULED', 'Week 1 Day 4', '113000000000000117', 9000),
    (9017, (CURRENT_DATE + INTERVAL '3 day') + TIME '13:00:00', 'BO3', 'SCHEDULED', 'Week 1 Day 4', '113000000000000118', 9000),
    -- +5일 2건
    (9018, (CURRENT_DATE + INTERVAL '5 day') + TIME '09:00:00', 'BO3', 'SCHEDULED', 'Week 1 Day 5', '113000000000000119', 9000),
    (9019, (CURRENT_DATE + INTERVAL '5 day') + TIME '11:00:00', 'BO3', 'SCHEDULED', 'Week 1 Day 5', '113000000000000120', 9000);

INSERT INTO "match_participants" ("id", "is_win", "score", "match_id", "club_id", "club_profile_id")
VALUES
    (9000, true, 2, 9000, 1000, 1000),
    (9001, false, 0, 9000, 1003, 1003),
    (9002, false, 1, 9001, 1007, 1007),
    (9003, true, 2, 9001, 1009, 1009),
    (9004, NULL, 0, 9002, 1001, 1001),
    (9005, NULL, 0, 9002, 1005, 1005),
    (9006, NULL, 0, 9003, 1002, 1002),
    (9007, NULL, 0, 9003, 1006, 1006),
    (9008, NULL, 0, 9004, 1004, 1004),
    (9009, NULL, 0, 9004, 1008, 1008),
    -- 어제 3건
    (9010, true, 2, 9005, 1000, 1000),
    (9011, false, 1, 9005, 1001, 1001),
    (9012, true, 2, 9006, 1002, 1002),
    (9013, false, 0, 9006, 1003, 1003),
    (9014, false, 1, 9007, 1004, 1004),
    (9015, true, 2, 9007, 1005, 1005),
    -- 오늘, 1시간/2시간 전에 종료
    (9016, true, 2, 9008, 1006, 1006),
    (9017, false, 0, 9008, 1007, 1007),
    (9018, true, 2, 9009, 1008, 1008),
    (9019, false, 1, 9009, 1009, 1009),
    -- 현재 진행 중, 1:0
    (9020, NULL, 1, 9010, 1000, 1000),
    (9021, NULL, 0, 9010, 1002, 1002),
    -- 오늘, 1시간/2시간 뒤 예정
    (9022, NULL, 0, 9011, 1001, 1001),
    (9023, NULL, 0, 9011, 1003, 1003),
    (9024, NULL, 0, 9012, 1004, 1004),
    (9025, NULL, 0, 9012, 1006, 1006),
    -- 내일 2건
    (9026, NULL, 0, 9013, 1005, 1005),
    (9027, NULL, 0, 9013, 1007, 1007),
    (9028, NULL, 0, 9014, 1008, 1008),
    (9029, NULL, 0, 9014, 1009, 1009),
    -- +3일 3건
    (9030, NULL, 0, 9015, 1000, 1000),
    (9031, NULL, 0, 9015, 1004, 1004),
    (9032, NULL, 0, 9016, 1001, 1001),
    (9033, NULL, 0, 9016, 1005, 1005),
    (9034, NULL, 0, 9017, 1002, 1002),
    (9035, NULL, 0, 9017, 1006, 1006),
    -- +5일 2건
    (9036, NULL, 0, 9018, 1003, 1003),
    (9037, NULL, 0, 9018, 1007, 1007),
    (9038, NULL, 0, 9019, 1008, 1008),
    (9039, NULL, 0, 9019, 1009, 1009);
