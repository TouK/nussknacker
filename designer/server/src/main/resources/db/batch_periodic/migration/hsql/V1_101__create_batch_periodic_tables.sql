create table "periodic_processes"
(
    "id" BIGINT identity primary key,
	"process_name" VARCHAR(16777216) not null,
	"process_version_id" BIGINT not null,
	"process_json" VARCHAR(16777216) not null,
	"periodic_property" VARCHAR(16777216) not null,
	"active" BOOLEAN not null,
	"created_at" TIMESTAMP not null
);

create table "periodic_process_deployments"
(
    "id" BIGINT identity primary key,
    "periodic_process_id" BIGINT not null
        constraint PERIODIC_PROCESS_DEPLOYMENTS_PERIODIC_PROCESSES__FK
            references "periodic_processes",
    "created_at" TIMESTAMP not null,
    "run_at" TIMESTAMP not null,
    "deployed_at" TIMESTAMP,
    "completed_at" TIMESTAMP,
    "status" VARCHAR(16777216) not null
);

