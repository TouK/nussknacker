create table "periodic_processes"
(
    "id"                 serial primary key,
    "process_name"       VARCHAR   not null,
    "process_version_id" BIGINT    not null,
    "process_json"       VARCHAR   not null,
    "periodic_property"  VARCHAR   not null,
    "active"             BOOLEAN   not null,
    "created_at"         TIMESTAMP not null
);

create table "periodic_process_deployments"
(
    "id"                  serial primary key,
    "periodic_process_id" BIGINT    not null
        constraint PERIODIC_PROCESS_DEPLOYMENTS_PERIODIC_PROCESSES__FK
            references "periodic_processes",
    "created_at"          TIMESTAMP not null,
    "run_at"              TIMESTAMP not null,
    "deployed_at"         TIMESTAMP,
    "completed_at"        TIMESTAMP,
    "status"              VARCHAR   not null
);

