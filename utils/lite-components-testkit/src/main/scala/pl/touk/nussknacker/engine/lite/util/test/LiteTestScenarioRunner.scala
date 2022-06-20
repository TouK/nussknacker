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
import pl.touk.nussknacker.engine.lite.util.test.SynchronousLiteInterpreter.SynchronousResult
import pl.touk.nussknacker.engine.testmode.TestComponentsHolder
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner.RunnerResult
import pl.touk.nussknacker.engine.util.test.{ClassBaseTestScenarioRunner, ModelWithTestComponents, RunResult}

import scala.reflect.ClassTag

object LiteTestScenarioRunner {

  val sourceName = "test-source"

  val sinkName = "test-sink"

}

/*
  This is simplistic Lite engine runner. It can be used to test enrichers, lite custom components.
  For testing specific source/sink implementations (e.g. request-response, kafka etc.) other runners should be used
 */
case class LiteTestScenarioRunner(components: List[ComponentDefinition], config: Config) extends ClassBaseTestScenarioRunner {

  /**
    *  Additional source LiteTestScenarioRunner.sourceName and sink LiteTestScenarioRunner.sinkName are provided,
    *  so sample scenario should look like:
    *  {{{
    *  .source("source", LiteTestScenarioRunner.sourceName)
    *    (...)
    *  .emptySink("sink", LiteTestScenarioRunner.sinkName, "value" -> "#result")
    *  }}}
    */
  override def runWithData[I:ClassTag, R](scenario: EspProcess, data: List[I]): RunnerResult[R] =
    runWithDataReturningDetails(scenario, data)
    .map{ result => RunResult(result._1, result._2.map(_.result.asInstanceOf[R])) }

  def runWithDataReturningDetails[T: ClassTag](scenario: EspProcess, data: List[T]): SynchronousResult = {
    val testSource = ComponentDefinition(sourceName, new SimpleSourceFactory(Typed[T]))
    val testSink = ComponentDefinition(sinkName, SimpleSinkFactory)
    val (modelData, runId) = ModelWithTestComponents.prepareModelWithTestComponents(config, testSource :: testSink :: components)
    val inputId = scenario.roots.head.id

    try {
      SynchronousLiteInterpreter
        .run(modelData, scenario, ScenarioInputBatch(data.map(d => (SourceId(inputId), d))))
    } finally {
      TestComponentsHolder.clean(runId)
    }
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
