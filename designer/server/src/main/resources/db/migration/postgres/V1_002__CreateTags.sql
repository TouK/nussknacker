CREATE TABLE "tags" (
  "name"       VARCHAR(254) NOT NULL,
  "process_id" VARCHAR(254) NOT NULL
);

ALTER TABLE "tags" ADD CONSTRAINT "pk_tag" PRIMARY KEY ("name", "process_id");
ALTER TABLE "tags" ADD CONSTRAINT "tag-process-fk" FOREIGN KEY ("process_id") REFERENCES "processes" ("id");