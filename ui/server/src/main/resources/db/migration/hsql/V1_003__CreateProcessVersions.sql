CREATE TABLE "process_versions" (
  "id"          NUMBER       NOT NULL,
  "process_id"  VARCHAR2(254) NOT NULL,
  "json"        VARCHAR2(100000),
  "main_class"  VARCHAR2(5000),
  "create_date" TIMESTAMP    NOT NULL,
  "user"        VARCHAR2(254) NOT NULL
);

ALTER TABLE "process_versions" ADD CONSTRAINT "pk_process_version" PRIMARY KEY ("process_id", "id");
