CREATE TABLE Orders
(
    customerName STRING,
    order_number INT
) WITH (
      'connector' = 'datagen'
);
