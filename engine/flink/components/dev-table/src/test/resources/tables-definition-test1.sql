CREATE TABLE TestTable1
(
    someString  STRING,
    someVarChar VARCHAR(150),
    someInt     INT
) WITH (
      'connector' = 'datagen'
);

CREATE TABLE TestTable2
(
    someString2  STRING,
    someVarChar2 VARCHAR(150),
    someInt2     INT
) WITH (
      'connector' = 'datagen'
);
