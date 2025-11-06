package pl.touk.nussknacker.engine.process

import com.typesafe.config.ConfigFactory
import io.circe.Json
import io.circe.syntax.EncoderOps
import org.scalatest.LoneElement
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.{ClassLoaderModelData, ConfigWithUnresolvedVersion}
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, DesignerWideComponentId}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{
  ExpressionParserCompilationError,
  JsonRequiredParameter,
  MultiSelectInvalidFormat,
  MultiSelectUnallowedValue
}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.classloader.ModelClassLoader
import pl.touk.nussknacker.engine.definition.component.Components.ComponentDefinitionExtractionMode
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterFactory
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.test.ScalatestMiniClusterJobStatusCheckingOps.miniClusterWithServicesToOps
import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner.FlinkTestScenarioRunnerExt
import pl.touk.nussknacker.engine.management.sample.service.MultiSelectEditorService
import pl.touk.nussknacker.engine.process.runner.FlinkScenarioUnitTestJob
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner.RunnerListResult
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage.convertValidatedToValuable

class MultiSelectEditorTest extends AnyFunSuite with FlinkSpec with Matchers with LoneElement {

  private lazy val flinkMiniClusterWithServices = FlinkMiniClusterFactory.createUnitTestsMiniClusterWithServices()

  private lazy val testScenarioRunner =
    TestScenarioRunner
      .flinkBased(ConfigFactory.empty(), flinkMiniClusterWithServices)
      .withExtraComponents(ComponentDefinition("multipleSelectEditorService", MultiSelectEditorService) :: Nil)
      .build()

  override protected def afterAll(): Unit = {
    super.afterAll()
    flinkMiniClusterWithServices.close()
  }

  test("multi select parameter should allow multiple values from allowed values list") {
    val testJson = """ [ "option1", "option2" ] """
    val result   = evaluateMutliSelectParameterByRunningScenario(testJson).validValue
    result.successes.loneElement shouldBe List("option1", "option2").asJson
  }

  test("multi select parameter should return error for unallowed value") {
    val testJson = """ [ "option3" ] """
    val result   = evaluateMutliSelectParameterByRunningScenario(testJson).invalidValue
    result.toList.loneElement should matchPattern { case _: MultiSelectUnallowedValue =>
    }
  }

  test("multi select parameter should return error for non-list") {
    val number = "123"
    val result = evaluateMutliSelectParameterByRunningScenario(number).invalidValue
    result.toList.loneElement should matchPattern {
      case MultiSelectInvalidFormat(
            "Expected a List, got value: 123",
            _,
            _,
          ) =>
    }
  }

  def evaluateMutliSelectParameterByRunningScenario(jsonExpressionString: String): RunnerListResult[AnyRef] = {
    val scenario = ScenarioBuilder
      .streaming("testScenario")
      .parallelism(1)
      .source("start", TestScenarioRunner.testDataSource)
      .enricher(
        "service",
        "serviceOut",
        "multipleSelectEditorService",
        "multiSelectParam" -> jsonExpressionString.jsonExpression
      )
      .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#serviceOut".spel)
    testScenarioRunner.runWithData[Int, AnyRef](scenario, List(1))
  }

}
