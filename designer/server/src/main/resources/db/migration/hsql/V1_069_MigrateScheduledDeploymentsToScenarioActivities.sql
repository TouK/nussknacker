-- Migrating scheduled deployments to scenario activities is not performed for HSQL db, must be done manually if needed
-- Drop the temporary jvm_timezone table, that is created by 068 migration, and then used by the Postgres version of 069 migration
DROP TABLE jvm_timezone;
