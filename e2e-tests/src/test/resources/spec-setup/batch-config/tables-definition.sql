CREATE TABLE transactions (
    datetime  TIMESTAMP,
    client_id STRING,
    amount    DECIMAL(15, 2),
    `date`    STRING
) WITH (
      'connector' = 'filesystem',
      'path' = 'file:///opt/flink/data/transactions-data/transactions',
      'format' = 'csv'
);
