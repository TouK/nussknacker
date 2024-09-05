CREATE TABLE transactions (
    datetime  TIMESTAMP,
    client_id STRING,
    amount    DECIMAL(15, 2),
    amountDoubled AS amount * 2,
    `file.name` STRING NOT NULL METADATA
) WITH (
      'connector' = 'filesystem',
      'path' = 'file:///transactions',
      'format' = 'json'
);
