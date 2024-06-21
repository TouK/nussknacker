CREATE TABLE transactions (
    datetime  TIMESTAMP,
    client_id STRING,
    amount    DECIMAL(15, 2),
    `date`    STRING
) PARTITIONED BY (`date`) WITH (
      'connector' = 'filesystem',
      'path' = 'file:///opt/flink/data/transactions-data/transactions',
      'format' = 'csv'
);

CREATE TABLE transactions_summary (
    client_id STRING,
    amount    DECIMAL(15, 2),
    `date`    STRING
) PARTITIONED BY (`date`) WITH (
      'connector' = 'filesystem',
      'path' = 'file:///opt/flink/data/transactions-data/transactions_summary',
      'format' = 'csv'
);

CREATE TABLE transactions_generator (
    datetime  TIMESTAMP,
    client_id STRING,
    amount    DECIMAL(15, 2)
) WITH (
      'connector' = 'faker',
      'number-of-rows' = '10000',
      'fields.datetime.expression' = '#{date.past ''10'',''DAYS''}',
      'fields.client_id.expression' = '#{Options.option ''Jan Kowalski'',''Frankie Mason'',''Koda Rivers''}',
      'fields.amount.expression' = '#{number.randomDouble ''2'', ''100'',''10000''}'
);
