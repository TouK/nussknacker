CREATE TABLE input (
    client_id STRING,
    amount  DECIMAL(15,2)
) WITH (
    'connector' = 'datagen',
    'number-of-rows' = '1'
);

CREATE TABLE output_invalid_column_names
(
    `Raw editor`  STRING,
    `Table`       STRING
) WITH (
    'connector' = 'blackhole'
);

CREATE TABLE output (
    client_id STRING,
    amount  DECIMAL(15,2)
)  WITH (
      'connector' = 'blackhole'
);
