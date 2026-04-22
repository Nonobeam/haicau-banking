-- ============================================================
-- V17: Composite unique index on (coa_path, ledger)
-- ============================================================
-- The CoA redesign pairs each customer wallet account (SUB) with a GL mirror
-- that shares the same coa_path. The V8 index on coa_path alone rejected the
-- mirror insert. Replace it with a composite unique index so (coa_path, ledger)
-- together are unique, allowing exactly one SUB and one GL row per path.

DROP INDEX IF EXISTS accounts_coa_path_idx;

CREATE UNIQUE INDEX accounts_coa_path_idx
    ON accounts(coa_path, ledger)
    WHERE coa_path IS NOT NULL;
