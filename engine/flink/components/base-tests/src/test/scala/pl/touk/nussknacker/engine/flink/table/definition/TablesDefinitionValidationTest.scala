package pl.touk.nussknacker.engine.flink.table.definition

import org.apache.flink.configuration.Configuration
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.scalatest.{Inside, LoneElement, Outcome}
import org.scalatest.funspec.FixtureAnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterFactory
import pl.touk.nussknacker.engine.flink.table.definition.FlinkDataDefinitionCreationError.FlinkDdlParseError.ParseError
import pl.touk.nussknacker.engine.flink.table.definition.FlinkDataDefinitionDiscoveryError.{
  CatalogDiscoveryProblem,
  CatalogNonTransientValidationError
}
import pl.touk.nussknacker.engine.flink.table.utils.ModelClassLoaderSimulationSuite
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

class TablesDefinitionValidationTest
    extends FixtureAnyFunSpec
    with Matchers
    with LoneElement
    with ModelClassLoaderSimulationSuite
    with Inside
    with ValidatedValuesDetailedMessage {

  override protected type FixtureParam = TablesDefinitionValidation

  private val miniClusterWithServices =
    FlinkMiniClusterFactory
      .createMiniClusterWithServices(
        simulatedModelClassloader,
        new Configuration,
      )

  override protected def afterAll(): Unit = {
    super.afterAll()
    miniClusterWithServices.close()
  }

  override protected def withFixture(test: OneArgTest): Outcome = {
    miniClusterWithServices.withDetachedStreamExecutionEnvironment { env =>
      test(new TablesDefinitionValidation(StreamTableEnvironment.create(env), simulatedModelClassloader))
    }
  }

  it("should not return external calls reliant validation errors") { validation =>
    val sql =
      """|CREATE CATALOG test_catalog WITH (
         |    'type' = 'jdbc',
         |    'default-database' = 'default-db',
         |    'username' = 'username',
         |    'password' = 'password',
         |    'base-url' = 'jdbc:postgresql://localhost:5432'
         |)""".stripMargin
    validation.validateWithoutExternalConnections(sql) shouldBe Symbol("valid")
  }

  it("should return error if catalog cannot be found on classpath") { validation =>
    val sql =
      """|CREATE CATALOG test_catalog WITH (
         |    'type' = 'non-existant-catalog'
         |)""".stripMargin
    val error = validation.validateWithoutExternalConnections(sql).invalidValue.toList.loneElement
    inside(error) { case e: CatalogDiscoveryProblem =>
      e.getMessage should startWith("Could not find matching catalog: [non-existant-catalog]")
    }
  }

  it("should return error for missing required catalog option") { validation =>
    val sql =
      """|CREATE CATALOG test_catalog WITH (
         |    'type' = 'jdbc',
         |    'username' = 'username',
         |    'password' = 'password',
         |    'base-url' = 'jdbc:postgresql://localhost:5432'
         |)""".stripMargin
    val error = validation.validateWithoutExternalConnections(sql).invalidValue.toList.loneElement
    inside(error) { case e: CatalogNonTransientValidationError =>
      e.getMessage shouldBe
        """|One or more required options are missing.
           |
           |Missing required options are:
           |
           |default-database""".stripMargin
    }
  }

  it("return error for empty flink data definition") { validation =>
    val error = validation.validateWithoutExternalConnections("").invalidValue.toList.loneElement
    inside(error) { case e: ParseError =>
      e.getMessage should startWith("""Could not parse SQL statements: Encountered "<EOF>" at line 0, column 0.""")
    }
  }

  it("should return Flink SQL parsing error") { validation =>
    val sql =
      s"""|CREATE TABLE `test_table` (
          |  `someString` STRING
          |) WITH (
          |  'connector' = 'not-on-classpath-connector',
          |)""".stripMargin
    val error = validation.validateWithoutExternalConnections(sql).invalidValue.toList.loneElement
    inside(error) { case e: FlinkDataDefinitionCreationError =>
      e.getMessage should startWith("""Could not parse SQL statements: Encountered ")" at line 5, column 1""")
    }
  }

  it("returns error from data definition registration (statement execution)") { validation =>
    val sql =
      """|CREATE TABLE somedb.testTable
         |(
         |    someString  STRING
         |) WITH (
         |    'connector' = 'datagen'
         |);""".stripMargin
    val error = validation.validateWithoutExternalConnections(sql).invalidValue.toList.loneElement

    inside(error) { case e: FlinkDataDefinitionRegistrationError =>
      e.getMessage should include("Cause: Could not execute CreateTable in path `default_catalog`.`somedb`.`testTable`")
    }
  }

  it("should return table environment runtime validation error") { validation =>
    val sql =
      s"""|CREATE TABLE testTable (
          |    `file.name` STRING
          |) WITH (
          |    'connector' = 'filesystem',
          |    'path' = '.',
          |    'format' = 'csv',
          |    'redundant' = '123'
          |);""".stripMargin
    val error = validation.validateWithoutExternalConnections(sql).invalidValue.toList
    inside(error) { case List(e1: FlinkDataDefinitionDiscoveryError, e2) =>
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
