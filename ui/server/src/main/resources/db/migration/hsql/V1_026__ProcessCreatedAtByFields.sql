-- create fields
ALTER TABLE "processes" ADD COLUMN "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE "processes" ADD COLUMN "created_by" VARCHAR(255);

-- set base value fields
UPDATE "processes" p SET
    "created_at" = (SELECT pv."create_date" FROM "process_versions" pv WHERE pv."process_id" = p."id" ORDER BY pv."create_date" ASC LIMIT 1),
    "created_by" = (SELECT pv."user" FROM "process_versions" pv WHERE pv."process_id" = p."id" ORDER BY pv."create_date" ASC LIMIT 1);

-- set set not null fields
ALTER TABLE "processes" ALTER COLUMN "created_at" set not null;
ALTER TABLE "processes" ALTER COLUMN "created_by" set not null;