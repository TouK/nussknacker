package pl.touk.nussknacker.engine.lite.util.test

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, TypedNodeDependency}
import pl.touk.nussknacker.engine.api.process.{SinkFactory, Source, SourceFactory}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.lite.api.interpreterTypes.{ScenarioInputBatch, SourceId}
import pl.touk.nussknacker.engine.lite.api.utils.sinks.LazyParamSink
import pl.touk.nussknacker.engine.lite.api.utils.sources.BaseLiteSource
import pl.touk.nussknacker.engine.lite.util.test.LiteTestScenarioRunner.{sinkName, sourceName}
import pl.touk.nussknacker.engine.util.test.{ModelWithTestComponents, TestScenarioRunner}

import scala.reflect.ClassTag

object LiteTestScenarioRunner {

  val sourceName = "test-source"

  val sinkName = "test-sink"

}

/*
  This is simplistic Lite engine runner. It can be used to test enrichers, lite custom components.
  For testing specific source/sink implementations (e.g. request-response, kafka etc.) other runners should be used
 */
case class LiteTestScenarioRunner(val components: List[ComponentDefinition], val config: Config) extends TestScenarioRunner {

  override def runWithData[T: ClassTag, Result](scenario: EspProcess, data: List[T]): List[Result] = {
    val testSource = ComponentDefinition(sourceName, new SimpleSourceFactory(Typed[T]))
    val testSink = ComponentDefinition(sinkName, SimpleSinkFactory)
    val modelData = ModelWithTestComponents.prepareModelWithTestComponents(config, testSource :: testSink :: components)
    val inputId = scenario.roots.head.id

    minimalLiteRuntime
      .run(modelData, scenario, ScenarioInputBatch(data.map(d => (SourceId(inputId), d))))
      .run
      ._2.map(_.result.asInstanceOf[Result])
  }
}

private[test] class SimpleSourceFactory(result: TypingResult) extends SourceFactory with SingleInputGenericNodeTransformation[Source] {

  override type State = Nothing

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep(Nil, _) => FinalResults(ValidationContext(Map(VariableConstants.InputVariableName -> result)))
  }

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[Nothing]): Source = {
    new BaseLiteSource[Any] {
      override val nodeId: NodeId = TypedNodeDependency[NodeId].extract(dependencies)

      override def transform(record: Any): Context = Context(contextIdGenerator.nextContextId(), Map(VariableConstants.InputVariableName -> record), None)
    }
  }

  override def nodeDependencies: List[NodeDependency] = TypedNodeDependency[NodeId] :: Nil
}

private[test] object SimpleSinkFactory extends SinkFactory {
  @MethodToInvoke
  def create(@ParamName("value") value: LazyParameter[AnyRef]): LazyParamSink[AnyRef] = (_: LazyParameterInterpreter) => value
}