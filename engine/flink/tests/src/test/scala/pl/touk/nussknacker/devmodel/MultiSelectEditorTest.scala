package pl.touk.nussknacker.devmodel

import com.typesafe.config.ConfigFactory
import io.circe.Json
import org.scalatest.LoneElement
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{
  MultiSelectInvalidFormat,
  MultiSelectUnallowedValue
}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedEagerParameter,
  NodeDependencyValue,
  SingleInputDynamicComponent
}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor.{Editor, EditorType, MultiSelectLabeledValue}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.ComponentUseContext
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterFactory
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner.FlinkTestScenarioRunnerExt
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner.RunnerListResult
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage.convertValidatedToValuable

import scala.concurrent.{ExecutionContext, Future}

class MultiSelectEditorTest extends AnyFunSuite with FlinkSpec with Matchers with LoneElement {

  private lazy val flinkMiniClusterWithServices = FlinkMiniClusterFactory.createUnitTestsMiniClusterWithServices()

  private lazy val testScenarioRunner =
    TestScenarioRunner
      .flinkBased(ConfigFactory.empty(), flinkMiniClusterWithServices)
      .withExtraComponents(
        List(
          ComponentDefinition("multiSelectDynamicParametersBasedComponent", MultiSelectDynamicParametersBasedComponent),
          ComponentDefinition("multiSelectAnnotationBasedComponent", MultiSelectAnnotationBasedComponent)
        )
      )
      .build()

  override protected def afterAll(): Unit = {
    super.afterAll()
    flinkMiniClusterWithServices.close()
  }

  test(
    "multi select parameter should allow multiple values from allowed values list for dynamic parameters API component"
  ) {
    val testJson = """ [ "stringOption", 3, [1,2,3], { "key": "value" } ] """
    val result   = evaluateMutliSelectParameterByRunningScenario(testJson).validValue
    result.successes.loneElement shouldBe Json.arr(
      Json.fromString("stringOption"),
      Json.fromInt(3),
      Json.arr(Json.fromInt(1), Json.fromInt(2), Json.fromInt(3)),
      Json.obj("key" -> Json.fromString("value"))
    )
  }

  test("multi select parameter should allow multiple values from allowed values list for annotation based component") {
    val testJson = """ [ "option1", "option2" ] """
    val result =
      evaluateMutliSelectParameterByRunningScenario(testJson, "multiSelectAnnotationBasedComponent").validValue
    result.successes.loneElement shouldBe Json.arr(
      Json.fromString("option1"),
      Json.fromString("option2")
    )
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

  def evaluateMutliSelectParameterByRunningScenario(
      jsonExpressionString: String,
      multiSelectComponentName: String = "multiSelectDynamicParametersBasedComponent"
  ): RunnerListResult[AnyRef] = {
    val scenario = ScenarioBuilder
      .streaming("testScenario")
      .parallelism(1)
      .source("start", TestScenarioRunner.testDataSource)
      .enricher(
        "service",
        "serviceOut",
        multiSelectComponentName,
        "multiSelectParam" -> jsonExpressionString.jsonExpression
      )
      .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#serviceOut".spel)
    testScenarioRunner.runWithData[Int, AnyRef](scenario, List(1))
  }

}

object MultiSelectDynamicParametersBasedComponent extends EagerService with SingleInputDynamicComponent {

  private val multiSelectParameterName = ParameterName("multiSelectParam")

  import scala.jdk.CollectionConverters._

  private val multiSelectParameter = Parameter
    // When defining a `MULTI_SELECT_EDITON` parameter, the expected type should be set to `Any`, not `io.circe.Json`,
    // even though the runtime value will be `io.circe.Json`
    .optional[Any](multiSelectParameterName)
    .copy(
      editors = List(
        MultiSelectEditor(
          List(
            MultiSelectFixedValue(Json.fromString("stringOption"), "stringOption"),
            MultiSelectFixedValue(Json.fromInt(3), "intOption"),
            MultiSelectFixedValue(Json.arr(Json.fromInt(1), Json.fromInt(2), Json.fromInt(3)), "listOption"),
            MultiSelectFixedValue(Json.obj("key" -> Json.fromString("value")), "recordOption")
          )
        )
      )
    )

  override type Implementation = ServiceInvoker
  override type State          = Unit

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): MultiSelectDynamicParametersBasedComponent.ContextTransformationDefinition = {
    case TransformationStep(Nil, _) => NextParameters(List(multiSelectParameter))
    case TransformationStep((multiSelectParameterName, DefinedEagerParameter(_, returnType)) :: Nil, _) =>
      val outName = OutputVariableNameDependency.extract(dependencies)
      FinalResults(context.withVariableUnsafe(outName, returnType), List.empty)
  }

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[Unit]
  ): MultiSelectDynamicParametersBasedComponent.Implementation = {
    val paramValue = params.extractDeclaredParamUnsafe[Json](multiSelectParameterName)
    new ServiceInvoker {
      override def invoke(context: Context)(
          implicit ec: ExecutionContext,
          collector: InvocationCollectors.ServiceInvocationCollector,
          componentUseContext: ComponentUseContext
      ): Future[Json] = Future(paramValue)
    }
  }

  override def nodeDependencies: List[NodeDependency] = List.empty
}

object MultiSelectAnnotationBasedComponent extends Service with Serializable {

  @MethodToInvoke
  def invoke(
      @ParamName("multiSelectParam")
      @Editor(
        `type` = EditorType.MULTI_SELECT_EDITOR,
        // Only String values may be configured using annotation based component API
        possibleMultiSelectValues = Array(
          new MultiSelectLabeledValue(value = "option1", label = "option1"),
          new MultiSelectLabeledValue(value = "option2", label = "option2")
        )
      )
      @Editor(`type` = EditorType.JSON_EDITOR)
      // When defining a `MULTI_SELECT_EDITON` parameter, the expected type should be set to `Any`, not `io.circe.Json`,
      // even though the runtime value will be `io.circe.Json`
      multiSelect: Any,
  ): Future[Any] = {
    Future.successful(multiSelect)
  }

}
