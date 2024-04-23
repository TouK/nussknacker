CREATE TABLE transactions
(
    datetime  TIMESTAMP,
    client_id STRING,
    amount    INT,
    `date`    STRING
) PARTITIONED BY (`date`) WITH (
      'connector' = 'filesystem',
      'path' = 'file:///transactions',
      'format' = 'csv'
);

CREATE TABLE transactions_summary
(
    client_id STRING,
    amount    INT,
    `date`    STRING
) PARTITIONED BY (`date`) WITH (
      'connector' = 'filesystem',
      'path' = 'file:///output/transactions_summary',
      'format' = 'csv'
);
