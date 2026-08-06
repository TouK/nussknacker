-- lock_until and locked_at use TIMESTAMP WITH TIME ZONE (unlike most columns in this schema) to ensure
-- correct lock expiry comparisons during DST transitions — TIMESTAMP without zone can cause locks to
-- appear expired or unexpired when the server clock shifts.
CREATE TABLE "distributed_locks"
(
    "name"       VARCHAR                  NOT NULL PRIMARY KEY,
    "lock_until" TIMESTAMP WITH TIME ZONE NOT NULL,
    "locked_at"  TIMESTAMP WITH TIME ZONE NOT NULL,
    "locked_by"  VARCHAR                  NOT NULL
);
