package pl.touk.nussknacker.engine.flink.table.io.source

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.flink.types.Row
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, LoneElement}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, NodesDeploymentData}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterFactory
import pl.touk.nussknacker.engine.flink.table.io.FlinkTableIOComponentProvider
import pl.touk.nussknacker.engine.flink.table.io.definition.StubbedCatalogFactory
import pl.touk.nussknacker.engine.flink.table.io.source.TableSource.SQL_FILTERING_EXPRESSION_PARAMETER_NAME
import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner
import pl.touk.nussknacker.engine.process.FlinkJobConfig.ExecutionMode
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.{PatientScalaFutures, ValidatedValuesDetailedMessage}

class TableSourceTest
    extends AnyFunSuite
    with Matchers
    with PatientScalaFutures
    with LoneElement
    with ValidatedValuesDetailedMessage
    with BeforeAndAfterAll {

  import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
  import pl.touk.nussknacker.engine.spel.SpelExtension._

  private lazy val tableComponentsConfig: Config = ConfigFactory.parseString(
    s"""{
       |  tableDefinition: \"\"\"
       |      CREATE TABLE test_table (
       |            `quantity` INT
       |      ) WITH (
       |          'connector' = 'datagen',
       |          'number-of-rows' = '1'
       |      );
       |  \"\"\"
       |}""".stripMargin
  )

  private lazy val tableComponents: List[ComponentDefinition] =
    FlinkTableIOComponentProvider.create(tableComponentsConfig)

  private lazy val flinkMiniClusterWithServices = FlinkMiniClusterFactory.createUnitTestsMiniClusterWithServices()

  override protected def afterAll(): Unit = {
    super.afterAll()
    flinkMiniClusterWithServices.close()
  }

  private lazy val runner: FlinkTestScenarioRunner = TestScenarioRunner
    .flinkBased(ConfigFactory.empty(), flinkMiniClusterWithServices)
    .withExecutionMode(ExecutionMode.Batch)
    .withExtraComponents(tableComponents)
    .build()

  test("be possible to use nodes deployment data") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("start", "table", "Table" -> s"'`default_catalog`.`default_database`.`test_table`'".spel)
      .emptySink(s"end", TestScenarioRunner.testResultSink, "value" -> "#input".spel)

    val result = runner
      .runWithoutData[Row](
        scenario,
        nodesData =
          NodesDeploymentData(Map(NodeId("start") -> Map(SQL_FILTERING_EXPRESSION_PARAMETER_NAME -> "true = true")))
      )
      .validValue
    result.errors shouldBe empty
    result.successes.loneElement
  }

  private def evaluateExpression(expression: String) = {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("start", "table", "Table" -> s"'`default_catalog`.`default_database`.`test_table`'".spel)
      .buildSimpleVariable("sth", "someVariable", expression.spel)
      .emptySink(s"end", TestScenarioRunner.testResultSink, "value" -> "#someVariable".spel)

    val result = runner
      .runWithoutData[Row](
        scenario,
        nodesData =
          NodesDeploymentData(Map(NodeId("start") -> Map(SQL_FILTERING_EXPRESSION_PARAMETER_NAME -> "true = true")))
      )
      .validValue
    result
  }

  test("be possible to use merge") {
    val result = evaluateExpression("#COLLECTION.merge(#input, #input).get('quantity')")
    result.errors shouldBe empty
    result.successes(0) shouldBe a[Int]
  }

  test("be possible to use selection") {
    val result = evaluateExpression("#input.?[(#this.value / 10 + 42 - #this.value / 10) == 42].quantity")
    result.errors shouldBe empty
    result.successes(0) shouldBe a[Int]
  }

  test("be possible to use projection") {
    import scala.jdk.CollectionConverters._
    val result = evaluateExpression("#input.![#this.value / 10 + 42 - #this.value / 10]")
    result.errors shouldBe empty
    result.successes(0) shouldBe List(42).asJava
  }

  test("be possible combine nodes deployment data with catalogs configuration") {
    val configWithCatalogConfiguration = ConfigFactory.parseString(
      s"""catalogConfiguration {
        |  type: ${StubbedCatalogFactory.catalogName}
        |}""".stripMargin
    )

    val tableComponentsBasedOnCatalogConfiguration: List[ComponentDefinition] =
      FlinkTableIOComponentProvider.create(configWithCatalogConfiguration)

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
        "Table" -> (s"'`_nu_catalog`." +
          s"`${StubbedCatalogFactory.sampleBoundedTablePath.getDatabaseName}`." +
          s"`${StubbedCatalogFactory.sampleBoundedTablePath.getObjectName}`'").spel
      )
      .emptySink(s"end", TestScenarioRunner.testResultSink, "value" -> "#input".spel)

    val resultWithoutFiltering = runnerWithCatalogConfiguration
      .runWithoutData[Row](
        scenario,
        nodesData =
          NodesDeploymentData(Map(NodeId("start") -> Map(SQL_FILTERING_EXPRESSION_PARAMETER_NAME -> "true = true")))
      )
      .validValue
    resultWithoutFiltering.errors shouldBe empty
    resultWithoutFiltering.successes should have size StubbedCatalogFactory.sampleBoundedTableNumberOfRows

    val resultWithFiltering = runnerWithCatalogConfiguration
      .runWithoutData[Row](
        scenario,
        nodesData =
          NodesDeploymentData(Map(NodeId("start") -> Map(SQL_FILTERING_EXPRESSION_PARAMETER_NAME -> "true = false")))
      )
      .validValue
    resultWithFiltering.errors shouldBe empty
    resultWithFiltering.successes shouldBe empty
  }

}
