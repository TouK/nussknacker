CREATE TABLE "deployed_process_versions" (
  "process_id"         VARCHAR2(254) NOT NULL,
  "process_version_id" NUMBER       NOT NULL,
  "environment"        VARCHAR2(254) NOT NULL,
  "user"               VARCHAR2(254) NOT NULL,
  "deploy_at"          TIMESTAMP    NOT NULL
);

ALTER TABLE "deployed_process_versions"
ADD CONSTRAINT "pk_deployed_process_version" PRIMARY KEY ("process_id", "process_version_id", "environment", "deploy_at");

ALTER TABLE "deployed_process_versions"
ADD CONSTRAINT "proc_ver_in_deployed_proc_fk" FOREIGN KEY ("process_id", "process_version_id") REFERENCES "process_versions" ("process_id", "id");
