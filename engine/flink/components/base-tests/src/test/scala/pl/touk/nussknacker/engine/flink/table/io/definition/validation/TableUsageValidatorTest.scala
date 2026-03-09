package pl.touk.nussknacker.engine.flink.table.io.definition.validation

import cats.data.{NonEmptyList, Validated}
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.scalatest.{BeforeAndAfterAll, LoneElement, Outcome}
import org.scalatest.Inside.inside
import org.scalatest.funspec.FixtureAnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterFactory
import pl.touk.nussknacker.engine.flink.table.io.definition.{
  FlinkDataDefinition,
  FlinkDataDefinitionError,
  FlinkDdlParser
}
import pl.touk.nussknacker.engine.flink.table.io.definition.FlinkDataDefinitionValidationError.{
  ConnectorNotFound,
  PersistableMetadataColumnUsedInSink,
  TableRuntimeValidationError,
  TableUseCaseNotSupportedByConnector
}
import pl.touk.nussknacker.engine.flink.table.io.definition.discovery.TableDiscoveryImpl
import pl.touk.nussknacker.test.{PatientScalaFutures, ValidatedValuesDetailedMessage}

import java.net.URLClassLoader

class TableUsageValidatorTest
    extends FixtureAnyFunSpec
    with Matchers
    with LoneElement
    with ValidatedValuesDetailedMessage
    with TableDrivenPropertyChecks
    with PatientScalaFutures
    with BeforeAndAfterAll {

  override protected type FixtureParam = StreamExecutionEnvironment

  private val miniClusterWithServices =
    FlinkMiniClusterFactory
      .createUnitTestsMiniClusterWithServices()

  override protected def afterAll(): Unit = {
    super.afterAll()
    miniClusterWithServices.close()
  }

  override protected def withFixture(test: OneArgTest): Outcome = {
    miniClusterWithServices.withDetachedStreamExecutionEnvironment { env =>
      test(env)
    }
  }

  private val emptyClassLoader = new URLClassLoader(Array.empty, getClass.getClassLoader)

  private def discoverAndValidateTableUsage(
      sql: String,
      sEnv: StreamExecutionEnvironment,
      tableUseCase: TableUseCase
  ): Validated[NonEmptyList[FlinkDataDefinitionError], Unit] = {
    val parsedSql      = FlinkDdlParser.parseUnsafe(sql)
    val dataDefinition = FlinkDataDefinition.applyUnsafe(parsedSql, None)

    val env = StreamTableEnvironment.create(sEnv)

    val discovery = new TableDiscoveryImpl(List.empty)
    val tableDefinition = discovery
      .discoverTableIdentifiers(dataDefinition, env)
      .map(id => discovery.discoverTable(env, dataDefinition, id))
      .loneElement

    new TableUsageValidatorImpl(emptyClassLoader).validateTableUsage(
      tableDefinition,
      tableUseCase,
      env,
      dataDefinition
    )
  }

  it("should return error for table with connector not on classpath") { sEnv =>
    val sql =
      s"""|CREATE TABLE `test_table` (
          |  `someString` STRING
          |) WITH (
          |  'connector' = 'not-on-classpath-connector'
          |)""".stripMargin

    val error = discoverAndValidateTableUsage(sql, sEnv, TableUseCase.Source).invalidValue.head
    error should matchPattern { case ConnectorNotFound("not-on-classpath-connector", _) =>
    }
  }

  it("should return error for table with format not on classpath") { sEnv =>
    val sql =
      s"""|CREATE TABLE `test_table` (
          |  `someString` STRING
          |) WITH (
          |  'connector' = 'filesystem',
          |  'path' = '.',
          |  'format' = 'not-on-classpath-format'
          |)""".stripMargin
    val error = discoverAndValidateTableUsage(sql, sEnv, TableUseCase.Source).invalidValue.head
    inside(error) { case err: TableRuntimeValidationError =>
      err.message shouldBe
        "Table validation failed. Reason: Could not find any format factory for identifier 'not-on-classpath-format' in the classpath."
    }
  }

  it("should return error for source only table with redundant options") { sEnv =>
    val sql =
      s"""|CREATE TABLE `datagen_table` (
          |  `someString` STRING
          |) WITH (
          |  'connector' = 'datagen',
          |  'redundant' = '123'
          |)""".stripMargin
    val error = discoverAndValidateTableUsage(sql, sEnv, TableUseCase.Source).invalidValue.head
    inside(error) { case e: TableRuntimeValidationError =>
      e.message shouldBe
        """|Table validation failed. Reason: Unsupported options found for 'datagen'.
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

  it("should return error for validating a table as source which cannot be a source") { sEnv =>
    val sql =
      s"""|CREATE TABLE `test_table` (
          |  `someString` STRING
          |) WITH (
          |  'connector' = 'blackhole'
          |)""".stripMargin
    val error = discoverAndValidateTableUsage(sql, sEnv, TableUseCase.Source).invalidValue.head
    inside(error) { case err: TableUseCaseNotSupportedByConnector =>
      err.message shouldBe "Table using connector 'blackhole' cannot be used as Source"
    }
  }

  it("should return error for validating a table as sink which cannot be a sink") { sEnv =>
    val sql =
      s"""|CREATE TABLE `test_table` (
          |  `someString` STRING
          |) WITH (
          |  'connector' = 'datagen'
          |)""".stripMargin
    val error = discoverAndValidateTableUsage(sql, sEnv, TableUseCase.Sink).invalidValue.head
    inside(error) { case err: TableUseCaseNotSupportedByConnector =>
      err.message shouldBe "Table using connector 'datagen' cannot be used as Sink"
    }
  }

  it("should return error for sink table with persistable metadata field") { sEnv =>
    val sql =
      s"""|CREATE TABLE `test_table` (
          |  `file.path` STRING METADATA
          |) WITH (
          |  'connector' = 'blackhole'
          |)""".stripMargin
    val error = discoverAndValidateTableUsage(sql, sEnv, TableUseCase.Sink).invalidValue.head
    error shouldBe PersistableMetadataColumnUsedInSink
  }

}
