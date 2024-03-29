CREATE TABLE transactions
(
    client_id STRING,
    amount INT,
    datetime DATE
) WITH (
    'connector' = 'datagen'
);

CREATE TABLE transactions_summary
(
    client_id STRING,
    amount INT,
    datetime DATE
) WITH (
      'connector' = 'datagen'
);
