CREATE TABLE transactions
(
    client_id STRING,
    amount INT,
    datetime STRING
) WITH (
    'connector' = 'datagen'
);

CREATE TABLE transactions_summary
(
    client_id STRING,
    amount INT,
    datetime STRING
) WITH (
      'connector' = 'datagen'
);
