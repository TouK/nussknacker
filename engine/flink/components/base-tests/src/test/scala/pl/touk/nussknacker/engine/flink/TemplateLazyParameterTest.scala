package pl.touk.nussknacker.engine.flink

import com.typesafe.config.ConfigFactory
import org.apache.flink.api.connector.source.Boundedness
import org.apache.flink.streaming.api.datastream.DataStream
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
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
  ParameterDeclaration,
  SpelTemplateParameterEditor
}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkCustomStreamTransformation}
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
      List(ComponentDefinition("templateAstOperationCustomTransformer", SpelTemplateAstOperationCustomTransformer))
    )
    .build()

  test("should use spel template ast operation parameter") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("source", TestScenarioRunner.testDataSource)
      .customNode(
        "custom",
        "output",
        "templateAstOperationCustomTransformer",
        "templateOutput" -> Expression.spelTemplate(s"Hello#{#input}")
      )
      .emptySink("sink", TestScenarioRunner.testResultSink, "value" -> "#output".spel)

    val result = runner.runWithData(scenario, List(1, 2, 3), Boundedness.BOUNDED)
    result.validValue.errors shouldBe empty
    result.validValue.successes shouldBe List(
      "[Hello]-literal[1]-templated",
      "[Hello]-literal[2]-templated",
      "[Hello]-literal[3]-templated"
    )
  }

}

object SpelTemplateAstOperationCustomTransformer
    extends CustomStreamTransformer
    with SingleInputDynamicComponent[FlinkCustomStreamTransformation]
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
  ): SpelTemplateAstOperationCustomTransformer.ContextTransformationDefinition = {
    case TransformationStep(Nil, _) => NextParameters(List(spelTemplateParameter.createParameter()))
    case TransformationStep((ParameterName("templateOutput"), DefinedLazyParameter(_)) :: Nil, _) =>
      val outName = OutputVariableNameDependency.extract(dependencies)
      FinalResults(context.withVariableUnsafe(outName, Typed[String]), List.empty)
  }

  override def nodeDependencies: List[NodeDependency] = List(OutputVariableNameDependency)

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[Unit]
  ): FlinkCustomStreamTransformation = {
    val valueFromParam: LazyParameter[String] = spelTemplateParameter.extractValueUnsafe(params)
    FlinkCustomStreamTransformation {
      (dataStream: DataStream[Context], flinkCustomNodeContext: FlinkCustomNodeContext) =>
        dataStream.flatMap(
          flinkCustomNodeContext.lazyParameterHelper.lazyMapFunction(valueFromParam),
          flinkCustomNodeContext.valueWithContextInfo.forType(valueFromParam.returnType)
        )
    }
  }

}
