CREATE TABLE "distributed_locks"
(
    "name"       VARCHAR   NOT NULL PRIMARY KEY,
    "lock_until" TIMESTAMP NOT NULL,
    "locked_at"  TIMESTAMP NOT NULL,
    "locked_by"  VARCHAR   NOT NULL
);