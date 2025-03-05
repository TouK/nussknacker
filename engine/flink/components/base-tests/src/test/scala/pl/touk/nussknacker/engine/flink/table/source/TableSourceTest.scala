package pl.touk.nussknacker.engine.flink.table.source

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.commons.io.FileUtils
import org.apache.flink.types.Row
import org.scalatest.{BeforeAndAfterAll, LoneElement}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, NodesDeploymentData}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterFactory
import pl.touk.nussknacker.engine.flink.table.FlinkTableComponentProvider
import pl.touk.nussknacker.engine.flink.table.definition.{FlinkDataDefinition, StubbedCatalogFactory}
import pl.touk.nussknacker.engine.flink.table.source.TableSource.SQL_EXPRESSION_PARAMETER_NAME
import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner
import pl.touk.nussknacker.engine.process.FlinkJobConfig.ExecutionMode
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.{PatientScalaFutures, ValidatedValuesDetailedMessage}

import java.io.File
import java.nio.charset.StandardCharsets

class TableSourceTest
    extends AnyFunSuite
    with Matchers
    with PatientScalaFutures
    with LoneElement
    with ValidatedValuesDetailedMessage
    with BeforeAndAfterAll {

  import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
  import pl.touk.nussknacker.engine.spel.SpelExtension._

  private lazy val tablesDefinition =
    s"""CREATE DATABASE testdb;
       |
       |CREATE TABLE testdb.tablewithqualifiedname (
       |      `quantity` INT
       |) WITH (
       |    'connector' = 'datagen',
       |    'number-of-rows' = '1'
       |);
       |""".stripMargin

  private lazy val sqlTablesDefinitionFilePath = {
    val tempFile = File.createTempFile("tables-definition", ".sql")
    tempFile.deleteOnExit()
    FileUtils.writeStringToFile(tempFile, tablesDefinition, StandardCharsets.UTF_8)
    tempFile.toPath
  }

  private lazy val tableComponentsConfig: Config = ConfigFactory.parseString(
    s"""{
       |  tableDefinitionFilePath: $sqlTablesDefinitionFilePath
       |}""".stripMargin
  )

  private lazy val tableComponents: List[ComponentDefinition] = new FlinkTableComponentProvider().create(
    tableComponentsConfig,
    ProcessObjectDependencies.withConfig(tableComponentsConfig)
  )

  private lazy val flinkMiniClusterWithServices = FlinkMiniClusterFactory.createUnitTestsMiniClusterWithServices()

  private lazy val runner: FlinkTestScenarioRunner = TestScenarioRunner
    .flinkBased(ConfigFactory.empty(), flinkMiniClusterWithServices)
    .withExecutionMode(ExecutionMode.Batch)
    .withExtraComponents(tableComponents)
    .build()

  override protected def afterAll(): Unit = {
    super.afterAll()
    flinkMiniClusterWithServices.close()
  }

  test("be possible to use table declared inside a database other than the default one") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("start", "table", "Table" -> s"'`default_catalog`.`testdb`.`tablewithqualifiedname`'".spel)
      .emptySink(s"end", TestScenarioRunner.testResultSink, "value" -> "#input".spel)

    val result = runner.runWithoutData[Row](scenario).validValue
    result.errors shouldBe empty
    result.successes.loneElement
  }

  test("be possible to use nodes deployment data") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("start", "table", "Table" -> s"'`default_catalog`.`testdb`.`tablewithqualifiedname`'".spel)
      .emptySink(s"end", TestScenarioRunner.testResultSink, "value" -> "#input".spel)

    val result = runner
      .runWithoutData[Row](
        scenario,
        nodesData = NodesDeploymentData(Map(NodeId("start") -> Map(SQL_EXPRESSION_PARAMETER_NAME -> "true = true")))
      )
      .validValue
    result.errors shouldBe empty
    result.successes.loneElement
  }

  test("be possible combine nodes deployment data with catalogs configuration") {
    val configWithCatalogConfiguration = ConfigFactory.parseString(
      s"""catalogConfiguration {
        |  type: ${StubbedCatalogFactory.catalogName}
        |}""".stripMargin
    )

    val tableComponentsBasedOnCatalogConfiguration: List[ComponentDefinition] =
      new FlinkTableComponentProvider().create(
        configWithCatalogConfiguration,
        ProcessObjectDependencies.withConfig(configWithCatalogConfiguration)
      )

    val runnerWithCatalogConfiguration: FlinkTestScenarioRunner = TestScenarioRunner
      .flinkBased(ConfigFactory.empty(), flinkMiniClusterWithServices)
      .withExecutionMode(ExecutionMode.Batch)
      .withExtraComponents(tableComponentsBasedOnCatalogConfiguration)
      .build()

    val scenario = ScenarioBuilder
      .streaming("test")
      .source(
        "start",
        "table",
        "Table" -> (s"'`${FlinkDataDefinition.internalCatalogName}`." +
          s"`${StubbedCatalogFactory.sampleBoundedTablePath.getDatabaseName}`." +
          s"`${StubbedCatalogFactory.sampleBoundedTablePath.getObjectName}`'").spel
      )
      .emptySink(s"end", TestScenarioRunner.testResultSink, "value" -> "#input".spel)

    val resultWithoutFiltering = runnerWithCatalogConfiguration
      .runWithoutData[Row](
        scenario,
        nodesData = NodesDeploymentData(Map(NodeId("start") -> Map(SQL_EXPRESSION_PARAMETER_NAME -> "true = true")))
      )
      .validValue
    resultWithoutFiltering.errors shouldBe empty
    resultWithoutFiltering.successes should have size StubbedCatalogFactory.sampleBoundedTableNumberOfRows

    val resultWithFiltering = runnerWithCatalogConfiguration
      .runWithoutData[Row](
        scenario,
        nodesData = NodesDeploymentData(Map(NodeId("start") -> Map(SQL_EXPRESSION_PARAMETER_NAME -> "true = false")))
      )
      .validValue
    resultWithFiltering.errors shouldBe empty
    resultWithFiltering.successes shouldBe empty
  }

}
