-- V1.4__rename_sequence_order_to_tie_break_priority.sql
--
-- sequence_order never encoded an occurrence count — it is only used by
-- TournamentDueChecker.findMatchingWindow as a deterministic tie-break when
-- two recurrence windows are equidistant (circular month distance) from a
-- tournament's start date. Rename it to match that single responsibility.

ALTER TABLE "league_recurrence_windows"
    RENAME COLUMN "sequence_order" TO "tie_break_priority";
