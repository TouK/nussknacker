CREATE TABLE transactions_input
(
    datetime  TIMESTAMP,
    client_id STRING,
    amount    DECIMAL(15, 2),
    `date`    AS CAST('2023-01-01' AS DATE)
) WITH (
      'connector' = 'filesystem',
      'path' = 'file:///opt/flink/data/test-data/transactions-input',
      'format' = 'csv'
);

CREATE TABLE transactions_faked
(
    datetime  TIMESTAMP,
    client_id STRING,
    amount    DECIMAL(15, 2)
) WITH (
      'connector' = 'faker',
      'number-of-rows' = '1000',
      'fields.datetime.expression' = '#{date.past ''10'',''DAYS''}',
      'fields.client_id.expression' = '#{superhero.name}',
      'fields.amount.expression' = '#{number.numberBetween ''100'',''10000000''}'
);

CREATE TABLE transactions_summary
(
    client_id STRING,
    amount    DECIMAL(15, 2),
    `date`    DATE
) PARTITIONED BY (`date`) WITH (
      'connector' = 'filesystem',
      'path' = 'file:///opt/flink/data/test-data/transactions',
      'format' = 'csv'
);

CREATE TABLE all_types_generated
(
    `string`       STRING,
    `boolean`      BOOLEAN,
    `tinyInt`      TINYINT,
    `smallInt`     SMALLINT,
    `int`          INT,
    `bigint`       BIGINT,
    `float`        FLOAT,
    `double`       DOUBLE,
    `decimal`      DECIMAL,
    `date`         DATE,
    `time`         TIME,
    `timestamp`    TIMESTAMP,
    `timestampLtz` TIMESTAMP_LTZ,
    `row`          ROW<colStr STRING,colInt INT>,
    `array`        ARRAY<STRING>,
    `map`          MAP<STRING,STRING>,
    `multiset`     MULTISET<STRING>
) WITH (
      'connector' = 'datagen',
      'number-of-rows' = '1000'
);
