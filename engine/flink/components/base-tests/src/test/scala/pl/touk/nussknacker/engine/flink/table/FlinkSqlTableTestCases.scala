package pl.touk.nussknacker.engine.flink.table

import pl.touk.nussknacker.engine.flink.table.io.definition.FlinkSqlDdlStatement._

object FlinkSqlTableTestCases {

  val allColumnTypesTable: String =
    s"""|CREATE TABLE testTable
        |(
        |    someString  STRING,
        |    someVarChar VARCHAR(150),
        |    someInt     INT,
        |    someIntComputed AS someInt * 2,
        |    `file.name` STRING NOT NULL METADATA
        |) WITH (
        |    'connector' = 'filesystem',
        |    'path' = '.',
        |    'format' = 'csv'
        |);""".stripMargin

  val unboundedDatagenTable: String =
    s"""|CREATE TABLE datagen_table (
        |    someString STRING
        |) WITH (
        |    'connector' = 'datagen'
        |)""".stripMargin

  val unboundedDatagenTableFormatted: String =
    s"""|CREATE TABLE `datagen_table` (
        |  `someString` STRING
        |)
        |WITH (
        |  'connector' = 'datagen'
        |)""".stripMargin

  val unboundedKafkaTable: String =
    """|CREATE TABLE my_kafka_table (
       |    id STRING,
       |    `timestamp` TIMESTAMP(3),
       |    `value` DOUBLE,
       |    WATERMARK FOR `timestamp` AS `timestamp` - INTERVAL '5' SECOND
       |)
       |WITH (
       |    'connector' = 'kafka',
       |    'topic' = 'my-topic',
       |    'properties.bootstrap.servers' = 'kafka-broker:9092',
       |    'properties.group.id' = 'flink-group',
       |    'scan.startup.mode' = 'earliest-offset',
       |    'format' = 'json'
       |)""".stripMargin

  val unboundedKafkaTableFormatted: String =
    """|CREATE TABLE `my_kafka_table` (
       |  `id` STRING,
       |  `timestamp` TIMESTAMP(3),
       |  `value` DOUBLE,
       |  WATERMARK FOR `timestamp` AS `timestamp` - INTERVAL '5' SECOND
       |)
       |WITH (
       |  'connector' = 'kafka',
       |  'topic' = 'my-topic',
       |  'properties.bootstrap.servers' = 'kafka-broker:9092',
       |  'properties.group.id' = 'flink-group',
       |  'scan.startup.mode' = 'earliest-offset',
       |  'format' = 'json'
       |)""".stripMargin

  object PostgresCatalog {

    val postgresCatalog: String =
      """|CREATE CATALOG my_catalog WITH (
         |    'type' = 'jdbc',
         |    'default-database' = 'default-db',
         |    'username' = 'username',
         |    'password' = 'password',
         |    'base-url' = 'jdbc:postgresql://localhost:5432'
         |)""".stripMargin

    val postgresCatalogParsed = CreateCatalog(
      CatalogName("my_catalog"),
      List(
        SqlOption("type", "jdbc"),
        SqlOption("default-database", "default-db"),
        SqlOption("username", "username"),
        SqlOption("password", "password"),
        SqlOption("base-url", "jdbc:postgresql://localhost:5432"),
      ),
      CatalogType("jdbc")
    )

  }

  val blackholeTable: String =
    """|CREATE TABLE `blackhole_table` WITH (
       |  'connector' = 'blackhole'
       |)""".stripMargin

}
