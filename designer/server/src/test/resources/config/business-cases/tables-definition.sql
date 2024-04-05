CREATE TABLE transactions
(
    datatime TIMESTAMP,
    client_id STRING,
    amount    DECIMAL(15, 2)
) WITH (
      'connector' = 'filesystem',
      'path' = 'file:///transactions',
      'format' = 'csv'
      );

CREATE TABLE transactions_summary
(
    datatime TIMESTAMP,
    client_id STRING,
    amount    DECIMAL(15, 2)
) WITH (
      'connector' = 'filesystem',
      'path' = 'file:///output/transactions_summary',
      'format' = 'csv'
      )
