alter table "processes" add column "processing_type" VARCHAR2(254);

update "processes" set "processing_type" = 'streaming';

ALTER TABLE "processes" ALTER COLUMN "processing_type" set not null;