package pl.touk.nussknacker.engine.flink.table.definition

import cats.implicits.{catsSyntaxValidatedId, toTraverseOps}
import org.apache.flink.configuration.Configuration
import org.apache.flink.table.api.DataTypes
import org.apache.flink.table.catalog._
import org.scalatest.Inside.inside
import org.scalatest.LoneElement
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterFactory
import pl.touk.nussknacker.engine.flink.table.definition.FlinkDataDefinitionCreationError.EmptyDataDefinitionConfiguration
import pl.touk.nussknacker.engine.flink.table.definition.FlinkDataDefinitionDiscoveryError.{
  ConnectorDiscoveryProblem,
  TableEnvironmentRuntimeValidationError
}
import pl.touk.nussknacker.engine.flink.table.definition.FlinkDataDefinitionRegistrationError.{
  CatalogRegistrationError,
  SqlStatementExecutionError
}
import pl.touk.nussknacker.engine.flink.table.utils.DataTypesExtensions._
import pl.touk.nussknacker.engine.flink.table.utils.ModelClassLoaderSimulationSuite
import pl.touk.nussknacker.test.{PatientScalaFutures, ValidatedValuesDetailedMessage}

import scala.jdk.CollectionConverters._

class TablesDefinitionDiscoveryTest
    extends AnyFunSuite
    with Matchers
    with LoneElement
    with ValidatedValuesDetailedMessage
    with TableDrivenPropertyChecks
    with PatientScalaFutures
    with ModelClassLoaderSimulationSuite {

  private val minicluster = FlinkMiniClusterFactory.createUnitTestsMiniClusterWithServices()

  private def discoverTables(sql: String) = {
    val parsedSql      = FlinkDdlParser.parseUnsafe(sql)
    val dataDefinition = FlinkDataDefinition.applyUnsafe(parsedSql, None)
    TablesDefinitionDiscovery
      .prepareDiscovery(dataDefinition, minicluster, simulatedModelClassloader)
      .andThen(_.listTables.sequence)
  }

  test("extracts table definition with correct source and sink data type") {
    val sql =
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
    val tableDefinition = discoverTables(sql).validValue.loneElement

    val sourceRowType = tableDefinition.sourceRowDataType.toLogicalRowTypeUnsafe
    sourceRowType.getFieldNames.asScala shouldBe List(
      "someString",
      "someVarChar",
      "someInt",
      "someIntComputed",
      "file.name"
    )
    sourceRowType.getTypeAt(0) shouldEqual DataTypes.STRING().getLogicalType
    sourceRowType.getTypeAt(1) shouldEqual DataTypes.VARCHAR(150).getLogicalType
    sourceRowType.getTypeAt(2) shouldEqual DataTypes.INT().getLogicalType
    sourceRowType.getTypeAt(3) shouldEqual DataTypes.INT().getLogicalType
    sourceRowType.getTypeAt(4) shouldEqual DataTypes.STRING().notNull().getLogicalType

    tableDefinition.sinkRowDataType.toLogicalRowTypeUnsafe.getFieldNames.asScala shouldBe List(
      "someString",
      "someVarChar",
      "someInt",
      "file.name"
    )
  }

  test("use catalog configuration in data definition") {
    val catalogConfiguration = Configuration.fromMap(Map("type" -> StubbedCatalogFactory.catalogName).asJava)
    val flinkDataDefinition  = FlinkDataDefinition.applyUnsafe(List.empty, Some(catalogConfiguration))
    val discovery =
      TablesDefinitionDiscovery.prepareDiscovery(flinkDataDefinition, minicluster, simulatedModelClassloader).validValue
    val tableDefinition = discovery.listTables.sequence.validValue.loneElement

    tableDefinition.tableId.toString shouldBe s"`_nu_catalog`." +
      s"`${StubbedCatalogFactory.sampleBoundedTablePath.getDatabaseName}`." +
      s"`${StubbedCatalogFactory.sampleBoundedTablePath.getObjectName}`"
    tableDefinition.schema shouldBe ResolvedSchema.of(
      Column.physical(StubbedCatalogFactory.sampleColumnName, DataTypes.STRING())
    )
  }

  test("returns no error for persistable metadata column table") {
    val sql =
      s"""|CREATE TABLE testTable (
          |    `file.name` STRING NOT NULL METADATA
          |) WITH (
          |    'connector' = 'filesystem',
          |    'path' = '.',
          |    'format' = 'csv'
          |);""".stripMargin
    discoverTables(sql) shouldBe Symbol("valid")
  }

  test("return error for empty flink data definition") {
    FlinkDataDefinition.apply(List.empty, None) shouldBe EmptyDataDefinitionConfiguration.invalidNel
  }

  test("returns error for table under non-default database") {
    val sql =
      """|CREATE TABLE somedb.testTable
         |(
         |    someString  STRING
         |) WITH (
         |    'connector' = 'datagen'
         |);""".stripMargin
    val error = discoverTables(sql).invalidValue.toList.loneElement

    inside(error) { case e: SqlStatementExecutionError =>
      e.getMessage should include("Cause: Could not execute CreateTable in path `default_catalog`.`somedb`.`testTable`")
    }
  }

  test("should return error if cannot connect to catalog at discovery preparation") {
    val sql =
      """|CREATE CATALOG my_catalog WITH (
        |    'type' = 'jdbc',
        |    'default-database' = 'default-db',
        |    'username' = 'username',
        |    'password' = 'password',
        |    'base-url' = 'jdbc:postgresql://localhost:5432'
        |)""".stripMargin
    val error = discoverTables(sql).invalidValue.toList.loneElement
    inside(error) { case e: CatalogRegistrationError =>
      e.getMessage shouldBe
        """Could not create catalog.
          |Cause: Failed connecting to jdbc:postgresql://localhost:5432/default-db via JDBC.""".stripMargin
    }

  }

  test("should return error for table with connector not on classpath") {
    val sql =
      s"""|CREATE TABLE `test_table` (
          |  `someString` STRING
          |) WITH (
          |  'connector' = 'not-on-classpath-connector'
          |)""".stripMargin
    val error = discoverTables(sql).invalidValue.toList.loneElement
    inside(error) { case e: ConnectorDiscoveryProblem =>
      e.getMessage shouldBe "Could not find matching connector: [not-on-classpath-connector]"
    }
  }

  test("should return error for table with format not on classpath") {
    val sql =
      s"""|CREATE TABLE `test_table` (
          |  `someString` STRING
          |) WITH (
          |  'connector' = 'filesystem',
          |  'path' = '.',
          |  'format' = 'not-on-classpath-format'
          |)""".stripMargin
    val errors = discoverTables(sql).invalidValue.toList
    inside(errors) { case err1 :: err2 :: Nil =>
      err1.getMessage shouldBe "Could not find any format factory for identifier 'not-on-classpath-format' in the classpath."
      err2.getMessage shouldBe "Could not find any format factory for identifier 'not-on-classpath-format' in the classpath."
    }
  }

  test("should return error for source only table with redundant options") {
    val sql =
      s"""|CREATE TABLE `datagen_table` (
          |  `someString` STRING
          |) WITH (
          |  'connector' = 'datagen',
          |  'redundant' = '123'
          |)""".stripMargin
    val error = discoverTables(sql).invalidValue.toList.loneElement
    inside(error) { case e: TableEnvironmentRuntimeValidationError =>
      e.getMessage shouldBe
        """|Unsupported options found for 'datagen'.
           |
           |Unsupported options:
           |
           |redundant
           |
           |Supported options:
           |
           |connector
           |fields.someString.kind
           |fields.someString.length
           |fields.someString.null-rate
           |fields.someString.var-len
           |number-of-rows
           |rows-per-second
           |scan.parallelism""".stripMargin
    }
  }

  test("should return error for sink only table with redundant options") {
    val sql =
      s"""|CREATE TABLE `datagen_table` (
          |  `someString` STRING
          |) WITH (
          |  'connector' = 'blackhole',
          |  'redundant' = '123'
          |)""".stripMargin
    val error = discoverTables(sql).invalidValue.toList.loneElement
    inside(error) { case e: TableEnvironmentRuntimeValidationError =>
      e.getMessage shouldBe
        """|Unsupported options found for 'blackhole'.
           |
           |Unsupported options:
           |
           |redundant
           |
           |Supported options:
           |
           |connector
           |property-version
           |scan.watermark.alignment.group
           |scan.watermark.alignment.max-drift
           |scan.watermark.alignment.update-interval
           |scan.watermark.emit.strategy
           |scan.watermark.idle-timeout""".stripMargin
    }
  }

  test("should return duplicated error for source and sink table with redundant options") {
    val sql =
      s"""|CREATE TABLE testTable (
          |    `file.name` STRING
          |) WITH (
          |    'connector' = 'filesystem',
          |    'path' = '.',
          |    'format' = 'csv',
          |    'redundant' = '123'
          |);""".stripMargin
    val error = discoverTables(sql).invalidValue.toList
    inside(error) { case List(e1: TableEnvironmentRuntimeValidationError, e2) =>
      val expectedMessage =
        """|Unsupported options found for 'filesystem'.
           |
           |Unsupported options:
           |
           |redundant
           |
           |Supported options:
           |
           |auto-compaction
           |compaction.file-size
           |connector
           |format
           |partition.default-name
           |partition.time-extractor.class
           |partition.time-extractor.kind
           |partition.time-extractor.timestamp-formatter
           |partition.time-extractor.timestamp-pattern
           |path
           |property-version
           |scan.watermark.alignment.group
           |scan.watermark.alignment.max-drift
           |scan.watermark.alignment.update-interval
           |scan.watermark.emit.strategy
           |scan.watermark.idle-timeout
           |sink.parallelism
           |sink.partition-commit.delay
           |sink.partition-commit.policy.class
           |sink.partition-commit.policy.class.parameters
           |sink.partition-commit.policy.kind
           |sink.partition-commit.success-file.name
           |sink.partition-commit.trigger
           |sink.partition-commit.watermark-time-zone
           |sink.rolling-policy.check-interval
           |sink.rolling-policy.file-size
           |sink.rolling-policy.inactivity-interval
           |sink.rolling-policy.rollover-interval
           |sink.shuffle-by-partition.enable
           |source.monitor-interval
           |source.path.regex-pattern
           |source.report-statistics""".stripMargin
      e1.getMessage shouldBe expectedMessage
      e2.getMessage shouldBe expectedMessage
    }
  }

}
