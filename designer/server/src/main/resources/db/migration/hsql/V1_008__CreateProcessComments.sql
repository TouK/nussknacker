CREATE TABLE "process_comments" (
  "process_id"          VARCHAR2(254) NOT NULL,
  "process_version_id"  NUMBER NOT NULL,
  "content"             VARCHAR2(100000) NOT NULL,
  "user"                VARCHAR2(254) NOT NULL,
  "create_date"         TIMESTAMP    NOT NULL
);

ALTER TABLE "process_comments"
ADD CONSTRAINT "proc_comments_proc_version_fk" FOREIGN KEY ("process_id", "process_version_id") REFERENCES "process_versions" ("process_id", "id");