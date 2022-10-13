package pl.touk.nussknacker.engine.lite.util.test

import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, TypedNodeDependency}
import pl.touk.nussknacker.engine.api.process.{SinkFactory, Source, SourceFactory}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.lite.api.interpreterTypes.{ScenarioInputBatch, SourceId}
import pl.touk.nussknacker.engine.lite.api.utils.sinks.LazyParamSink
import pl.touk.nussknacker.engine.lite.api.utils.sources.BaseLiteSource
import pl.touk.nussknacker.engine.lite.util.test.SynchronousLiteInterpreter.SynchronousResult
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner.RunnerListResult
import pl.touk.nussknacker.engine.util.test._

import scala.reflect.ClassTag

object LiteTestScenarioRunner {
    implicit class LiteTestScenarioRunnerExt(testScenarioRunner: TestScenarioRunner.type) {

    def liteBased(config: Config = ConfigFactory.load()): LiteTestScenarioRunnerBuilder = {
      LiteTestScenarioRunnerBuilder(List.empty, config)
    }

  }

}

case class LiteTestScenarioRunnerBuilder(extraComponents: List[ComponentDefinition], config: Config)
  extends TestScenarioRunnerBuilder[LiteTestScenarioRunner, LiteTestScenarioRunnerBuilder] {

  override def withExtraComponents(extraComponents: List[ComponentDefinition]): LiteTestScenarioRunnerBuilder =
    copy(extraComponents = extraComponents)

  override def build(): LiteTestScenarioRunner = new LiteTestScenarioRunner(extraComponents, config)

}


// TODO: expose like kafkaLite and flink version + add docs
/*
  This is simplistic Lite engine runner. It can be used to test enrichers, lite custom components.
  For testing specific source/sink implementations (e.g. request-response, kafka etc.) other runners should be used
 */
class LiteTestScenarioRunner(components: List[ComponentDefinition], config: Config) extends ClassBasedTestScenarioRunner {

  /**
    *  Additional source TestScenarioRunner.testDataSource and sink TestScenarioRunner.testResultSink are provided,
    *  so sample scenario should look like:
    *  {{{
    *  .source("source", TestScenarioRunner.testDataSource)
    *    (...)
    *  .emptySink("sink", TestScenarioRunner.testResultSink, "value" -> "#result")
    *  }}}
    */
  override def runWithData[I:ClassTag, R](scenario: CanonicalProcess, data: List[I]): RunnerListResult[R] =
    runWithDataReturningDetails(scenario, data)
    .map{ result => RunListResult(result._1, result._2.map(_.result.asInstanceOf[R])) }

  def runWithDataReturningDetails[T: ClassTag](scenario: CanonicalProcess, data: List[T]): SynchronousResult = {
    val testSource = ComponentDefinition(TestScenarioRunner.testDataSource, new SimpleSourceFactory(Typed[T]))
    val testSink = ComponentDefinition(TestScenarioRunner.testResultSink, SimpleSinkFactory)
    val inputId = scenario.nodes.head.id
    val inputBatch = ScenarioInputBatch(data.map(d => (SourceId(inputId), d: Any)))

    ModelWithTestComponents.withTestComponents(config, testSource :: testSink :: components) { modelData =>
      SynchronousLiteInterpreter.run(modelData, scenario, inputBatch)
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
