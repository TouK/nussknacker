CREATE TABLE all_types_input (
    `string`              STRING,
    `boolean`             BOOLEAN,
    `tinyInt`             TINYINT,
    `smallInt`            SMALLINT,
    `int`                 INT,
    `bigint`              BIGINT,
    `float`               FLOAT,
    `double`              DOUBLE,
    `decimal`             DECIMAL,
    `date`                DATE,
    `time`                TIME,
    `timestamp`           TIMESTAMP,
    `timestampLtz`        TIMESTAMP_LTZ
) WITH (
      'connector' = 'datagen',
      'number-of-rows' = '1'
);
