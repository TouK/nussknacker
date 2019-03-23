CREATE TABLE "process_attachments" (
  "id"                  VARCHAR(254)  NOT NULL PRIMARY KEY,
  "process_id"          VARCHAR(254)  NOT NULL,
  "process_version_id"  INTEGER       NOT NULL,
  "file_name"           VARCHAR(254)  NOT NULL,
  "file_path"           VARCHAR(1000) NOT NULL,
  "user"                VARCHAR(254)  NOT NULL,
  "create_date"         TIMESTAMP     NOT NULL
);

ALTER TABLE "process_attachments"
ADD CONSTRAINT "proc_attach_proc_version_fk" FOREIGN KEY ("process_id", "process_version_id") REFERENCES "process_versions" ("process_id", "id") ON DELETE CASCADE;