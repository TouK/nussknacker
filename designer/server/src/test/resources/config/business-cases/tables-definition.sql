CREATE TABLE transactions
(
    client_id STRING,
    amount    INT
) WITH (
      'connector' = 'filesystem',
      'path' = 'file:///transactions',
      'format' = 'csv'
      );

CREATE TABLE transactions_summary
(
    client_id STRING,
    amount    INT
) WITH (
      'connector' = 'filesystem',
      'path' = 'file:///transactions_summary',
      'format' = 'csv'
      )
