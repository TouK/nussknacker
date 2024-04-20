CREATE TABLE transactions
(
    client_id STRING,
    amount    INT,
    datetime  DATE
) WITH (
    'connector' = 'datagen'
);

CREATE TABLE transactions_summary
(
    client_id STRING,
    amount    INT,
    datetime  DATE
) WITH (
      'connector' = 'datagen'
);

CREATE TABLE all_types_generated
(
    `string`        STRING,
    `boolean`       BOOLEAN,
    `tinyInt`       TINYINT,
    `smallInt`      SMALLINT,
    `int`           INT,
    `bigint`        BIGINT,
    `float`         FLOAT,
    `double`        DOUBLE,
    `decimal`       DECIMAL,
    `date`          DATE,
    `time`          TIME,
    `timestamp`     TIMESTAMP,
    `timestampLtz`  TIMESTAMP_LTZ,
    `row`           ROW<colStr STRING, colInt INT>,
    `array`         ARRAY<STRING>,
    `map`           MAP<STRING, STRING>,
    `multiset`      MULTISET<STRING>
) WITH (
      'connector' = 'datagen'
);
