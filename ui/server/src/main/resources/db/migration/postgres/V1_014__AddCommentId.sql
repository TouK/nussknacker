ALTER TABLE "process_comments" ADD COLUMN "id" INTEGER;

CREATE SEQUENCE "process_comments_id_sequence";
UPDATE "process_comments" set "id" = nextval('process_comments_id_sequence');

ALTER TABLE "process_comments" ADD CONSTRAINT "process_comments_pk" PRIMARY KEY ("id");