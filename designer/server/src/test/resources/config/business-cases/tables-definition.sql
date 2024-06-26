CREATE TABLE transactions
(
    datetime  TIMESTAMP,
    client_id STRING,
    amount    DECIMAL(15,2),
    `date`    STRING
) PARTITIONED BY (`date`) WITH (
      'connector' = 'filesystem',
      'path' = 'file:///transactions',
      'format' = 'csv'
);

CREATE TABLE transactions_summary
(
    client_id STRING,
    amount    DECIMAL(15,2),
    `date`    STRING
) PARTITIONED BY (`date`) WITH (
      'connector' = 'filesystem',
      'path' = 'file:///output/transactions_summary',
      'format' = 'csv'
);
