CREATE TABLE "process_versions" (
  "id"          INTEGER      NOT NULL,
  "process_id"  VARCHAR(254) NOT NULL,
  "json"        VARCHAR(100000),
  "main_class"  VARCHAR(5000),
  "create_date" TIMESTAMP    NOT NULL,
  "user"        VARCHAR(254) NOT NULL
);

ALTER TABLE "process_versions" ADD CONSTRAINT "pk_process_version" PRIMARY KEY ("process_id", "id");
