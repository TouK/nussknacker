package pl.touk.nussknacker.engine.flink

import com.typesafe.config.ConfigFactory
import org.apache.flink.api.common.functions.FlatMapFunction
import org.apache.flink.api.connector.source.Boundedness
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.util.Collector
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.TemplateRenderedPart.{RenderedLiteral, RenderedSubExpression}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.{BoundedStreamComponent, ComponentDefinition}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedLazyParameter,
  NodeDependencyValue,
  SingleInputDynamicComponent
}
import pl.touk.nussknacker.engine.api.definition.{
  NodeDependency,
  OutputVariableNameDependency,
  Parameter,
  SpelTemplateParameterEditor
}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.api.process.{
  AbstractOneParamLazyParameterFunction,
  FlinkCustomNodeContext,
  FlinkCustomStreamTransformation
}
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterFactory
import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.process.FlinkJobConfig.ExecutionMode
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

class SpelTemplateLazyParameterTest
    extends AnyFunSuite
    with Matchers
    with ValidatedValuesDetailedMessage
    with BeforeAndAfterAll {

  private lazy val flinkMiniClusterWithServices = FlinkMiniClusterFactory.createUnitTestsMiniClusterWithServices()

  private lazy val runner = TestScenarioRunner
    .flinkBased(ConfigFactory.empty(), flinkMiniClusterWithServices)
    .withExecutionMode(ExecutionMode.Batch)
    .withExtraComponents(
      List(ComponentDefinition("spelTemplatePartsCustomTransformer", SpelTemplatePartsCustomTransformer))
    )
    .build()

  override protected def afterAll(): Unit = {
    super.afterAll()
    flinkMiniClusterWithServices.close()
  }

  test("flink custom transformer using spel template rendered parts") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("source", TestScenarioRunner.testDataSource)
      .customNode(
        "custom",
        "output",
        "spelTemplatePartsCustomTransformer",
        "template" -> Expression.spelTemplate(s"Hello#{#input}")
      )
      .emptySink("sink", TestScenarioRunner.testResultSink, "value" -> "#output".spel)

    val result = runner.runWithData(scenario, List(1, 2, 3), Boundedness.BOUNDED)
    result.validValue.errors shouldBe empty
    result.validValue.successes shouldBe List(
      "[Hello]-literal[1]-subexpression",
      "[Hello]-literal[2]-subexpression",
      "[Hello]-literal[3]-subexpression"
    )
  }

}

object SpelTemplatePartsCustomTransformer
    extends CustomStreamTransformer
    with SingleInputDynamicComponent[FlinkCustomStreamTransformation]
    with BoundedStreamComponent {

  private val spelTemplateParameterName = ParameterName("template")

  private val spelTemplateParameter = Parameter
    .optional[String](spelTemplateParameterName)
    .copy(
      isLazyParameter = true,
      editor = Some(SpelTemplateParameterEditor)
    )

  override type State = Unit

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): SpelTemplatePartsCustomTransformer.ContextTransformationDefinition = {
    case TransformationStep(Nil, _) => NextParameters(List(spelTemplateParameter))
    case TransformationStep((`spelTemplateParameterName`, DefinedLazyParameter(_)) :: Nil, _) =>
      val outName = OutputVariableNameDependency.extract(dependencies)
      FinalResults(context.withVariableUnsafe(outName, Typed[String]), List.empty)
  }

  override def nodeDependencies: List[NodeDependency] = List(OutputVariableNameDependency)

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[Unit]
  ): FlinkCustomStreamTransformation = {
    val templateLazyParam: LazyParameter[TemplateEvaluationResult] =
      params.extractUnsafe[LazyParameter[TemplateEvaluationResult]](spelTemplateParameterName)
    FlinkCustomStreamTransformation {
      (dataStream: DataStream[Context], flinkCustomNodeContext: FlinkCustomNodeContext) =>
        dataStream
          .flatMap(
            new AbstractOneParamLazyParameterFunction[TemplateEvaluationResult](
              templateLazyParam,
              flinkCustomNodeContext.lazyParameterHelper
            ) with FlatMapFunction[Context, ValueWithContext[String]] {
              override def flatMap(value: Context, out: Collector[ValueWithContext[String]]): Unit = {
                collectHandlingErrors(value, out) {
                  val templateResult = evaluateParameter(value)
                  val result = templateResult.renderedParts.map {
                    case RenderedLiteral(value)       => s"[$value]-literal"
                    case RenderedSubExpression(value) => s"[$value]-subexpression"
                  }.mkString
                  ValueWithContext(result, value)
                }
              }
            },
            flinkCustomNodeContext.valueWithContextInfo.forClass[String]
          )
          .asInstanceOf[DataStream[ValueWithContext[AnyRef]]]
    }
  }

}
