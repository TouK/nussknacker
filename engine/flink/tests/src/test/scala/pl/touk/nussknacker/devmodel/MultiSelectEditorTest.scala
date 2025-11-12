package pl.touk.nussknacker.devmodel

import com.typesafe.config.ConfigFactory
import io.circe.syntax.EncoderOps
import org.scalatest.LoneElement
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{MultiSelectInvalidFormat, MultiSelectUnallowedValue}
import pl.touk.nussknacker.engine.api.editor.{Editor, EditorType, MultiSelectLabeledValue}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterFactory
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner.FlinkTestScenarioRunnerExt
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner.RunnerListResult
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage.convertValidatedToValuable

import scala.concurrent.Future

class MultiSelectEditorTest extends AnyFunSuite with FlinkSpec with Matchers with LoneElement {

  private lazy val flinkMiniClusterWithServices = FlinkMiniClusterFactory.createUnitTestsMiniClusterWithServices()

  private lazy val testScenarioRunner =
    TestScenarioRunner
      .flinkBased(ConfigFactory.empty(), flinkMiniClusterWithServices)
      .withExtraComponents(ComponentDefinition("multipleSelectEditorService", MultiSelectEditorServiceForTest) :: Nil)
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

object MultiSelectEditorServiceForTest extends Service with Serializable {

  @MethodToInvoke
  def invoke(
              @ParamName("multiSelectParam")
              @Editor(
                `type` = EditorType.MULTI_SELECT_EDITOR,
                possibleMultiSelectValues = Array(
                  new MultiSelectLabeledValue(value = "option1", label = "option1"),
                  new MultiSelectLabeledValue(value = "option2", label = "option2")
                )
              )
              @Editor(`type` = EditorType.JSON_EDITOR)
              multiSelect: Any,
            ): Future[Any] = {
    Future.successful(multiSelect)
  }

}
