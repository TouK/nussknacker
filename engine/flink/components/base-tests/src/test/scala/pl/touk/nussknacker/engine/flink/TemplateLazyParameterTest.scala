package pl.touk.nussknacker.engine.flink

import com.typesafe.config.ConfigFactory
import org.apache.flink.api.connector.source.Boundedness
import org.apache.flink.streaming.api.datastream.{DataStream, DataStreamSink}
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.sink
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.functions.sink.v2.DiscardingSink
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.LazyParameter.TemplateLazyParameter
import pl.touk.nussknacker.engine.api.LazyParameter.TemplateLazyParameter.TemplateExpressionPart.{Literal, Placeholder}
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, MethodToInvoke, NodeId, Params, ValueWithContext}
import pl.touk.nussknacker.engine.api.component.{BoundedStreamComponent, Component, ComponentDefinition}
import pl.touk.nussknacker.engine.api.context.{OutputVar, ValidationContext}
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedLazyParameter,
  NodeDependencyValue,
  SingleInputDynamicComponent
}
import pl.touk.nussknacker.engine.api.definition.{
  NodeDependency,
  OutputVariableNameDependency,
  ParameterDeclaration,
  SpelTemplateParameterEditor
}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.{Sink, SinkFactory, Source, SourceFactory}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkSink, StandardFlinkSource}
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.process.FlinkJobConfig.ExecutionMode
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

class TemplateLazyParameterTest extends AnyFunSuite with FlinkSpec with Matchers with ValidatedValuesDetailedMessage {

  private lazy val runner = TestScenarioRunner
    .flinkBased(ConfigFactory.empty(), flinkMiniCluster)
    .withExecutionMode(ExecutionMode.Batch)
    .withExtraComponents(
      List(ComponentDefinition("templateAstOperationSink", SpelTemplateAstOperationSink))
    )
    .build()

  test("should use spel template ast operation parameter") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("source", TestScenarioRunner.testDataSource)
      .emptySink(
        "end",
        "templateAstOperationSink",
        "templateOutput" -> Expression.spelTemplate(s"Hello#{#input}")
      )

    val result = runner.runWithData(scenario, List(1, 2, 3), Boundedness.BOUNDED)
    println(result)
    result.validValue.successes shouldBe List(
      "[Hello]-literal[1]-templated",
      "[Hello]-literal[2]-templated",
      "[Hello]-literal[3]-templated"
    )
  }

}

object SpelTemplateAstOperationSink
    extends SingleInputDynamicComponent[Sink]
    with SinkFactory
    with BoundedStreamComponent {

  private val spelTemplateParameter = ParameterDeclaration
    .lazyOptional[String](ParameterName("templateOutput"))
    .withCreator(modify =
      _.copy(
        editor = Some(SpelTemplateParameterEditor)
      )
    )

  override type State = Unit

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): SpelTemplateAstOperationSink.ContextTransformationDefinition = {
    case TransformationStep(Nil, _) => NextParameters(List(spelTemplateParameter.createParameter()))
    case TransformationStep((ParameterName("templateOutput"), DefinedLazyParameter(_)) :: Nil, _) =>
      FinalResults(context, List.empty)
  }

  override def nodeDependencies: List[NodeDependency] = List.empty

  override def implementation(params: Params, dependencies: List[NodeDependencyValue], finalState: Option[Unit]): Sink =
    new FlinkSink {
      override type Value = String

      val valueFromParam: LazyParameter[String] = spelTemplateParameter.extractValueUnsafe(params)

      override def prepareValue(
          dataStream: DataStream[Context],
          flinkCustomNodeContext: FlinkCustomNodeContext
      ): DataStream[ValueWithContext[Value]] = {
        dataStream.flatMap(
          flinkCustomNodeContext.lazyParameterHelper.lazyMapFunction(valueFromParam),
          flinkCustomNodeContext.valueWithContextInfo.forType(valueFromParam.returnType)
        )
      }

      override def registerSink(
          dataStream: DataStream[ValueWithContext[String]],
          flinkNodeContext: FlinkCustomNodeContext
      ): DataStreamSink[_] = {
        println(dataStream)
        dataStream.addSink(new SinkFunction[ValueWithContext[String]] {
          override def invoke(value: ValueWithContext[String], context: SinkFunction.Context): Unit = {
            println(value)
            println("debug")

          }
        })
      }

    }

}
