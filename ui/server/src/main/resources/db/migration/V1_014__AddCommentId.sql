alter table "process_comments" ADD COLUMN "id" NUMBER;

create sequence "process_comments_id_sequence";
update "process_comments" set "id" = "process_comments_id_sequence".nextval;

ALTER TABLE "process_comments" ADD CONSTRAINT "process_comments_pk" PRIMARY KEY ("id");