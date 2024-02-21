CREATE TABLE Orders (
                        customerName STRING,
                        order_number BIGINT,
                        price        DECIMAL(32,2)
) WITH (
      'connector' = 'datagen'
);

CREATE TABLE Products (
                          product_id BIGINT,
                          product_name STRING,
                          category STRING,
                          price DECIMAL(10, 2)
) WITH (
      'connector' = 'datagen'
);

CREATE TABLE Inventory (
                           product_id BIGINT,
                           quantity INT,
                           last_update TIMESTAMP(3)
) WITH (
      'connector' = 'datagen'
);
