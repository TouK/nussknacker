CREATE TABLE transactions (
    datetime  TIMESTAMP,
    client_id STRING,
    amount    DECIMAL(15, 2)
) WITH (
      'connector' = 'filesystem',
      'path' = 'file:///transactions',
      'format' = 'csv'
);
