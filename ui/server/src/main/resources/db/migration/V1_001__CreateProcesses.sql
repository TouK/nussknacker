CREATE TABLE "processes" (
  "id"          VARCHAR2(254) NOT NULL PRIMARY KEY,
  "name"        VARCHAR2(254) NOT NULL,
  "description" VARCHAR2(1000),
  "type"        VARCHAR2(254) NOT NULL
);