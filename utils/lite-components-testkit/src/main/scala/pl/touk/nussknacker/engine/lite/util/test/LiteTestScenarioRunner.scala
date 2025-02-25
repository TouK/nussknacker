package pl.touk.nussknacker.engine.lite.util.test

import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.engine.ComponentUseCase
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, UnboundedStreamComponent}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, SingleInputDynamicComponent}
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, TypedNodeDependency, WithExplicitTypesToExtract}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.lite.api.interpreterTypes.{ScenarioInputBatch, SourceId}
import pl.touk.nussknacker.engine.lite.api.utils.sinks.LazyParamSink
import pl.touk.nussknacker.engine.lite.api.utils.sources.BaseLiteSource
import pl.touk.nussknacker.engine.lite.components.LiteBaseComponentProvider
import pl.touk.nussknacker.engine.lite.util.test.SynchronousLiteInterpreter.SynchronousResult
import pl.touk.nussknacker.engine.testmode.TestProcess.ExceptionResult
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner.RunnerListResult
import pl.touk.nussknacker.engine.util.test._

import scala.reflect.ClassTag

object LiteTestScenarioRunner {

  implicit class LiteTestScenarioRunnerExt(testScenarioRunner: TestScenarioRunner.type) {

    def liteBased(config: Config = ConfigFactory.load()): LiteTestScenarioRunnerBuilder = {
      LiteTestScenarioRunnerBuilder(List.empty, Map.empty, config, testRuntimeMode = false)
    }

  }

}

case class LiteTestScenarioRunnerBuilder(
    components: List[ComponentDefinition],
    globalVariables: Map[String, AnyRef],
    config: Config,
    testRuntimeMode: Boolean
) extends TestScenarioRunnerBuilder[LiteTestScenarioRunner, LiteTestScenarioRunnerBuilder] {

  import TestScenarioRunner._

  override def withExtraComponents(components: List[ComponentDefinition]): LiteTestScenarioRunnerBuilder =
    copy(components = components)

  override def withExtraGlobalVariables(
      globalVariables: Map[String, AnyRef]
  ): LiteTestScenarioRunnerBuilder =
    copy(globalVariables = globalVariables)

  override def inTestRuntimeMode: LiteTestScenarioRunnerBuilder =
    copy(testRuntimeMode = true)

  override def build(): LiteTestScenarioRunner =
    new LiteTestScenarioRunner(components, globalVariables, config, componentUseCase(testRuntimeMode))

}

// TODO: expose like kafkaLite and flink version + add docs
/*
  This is simplistic Lite engine runner. It can be used to test enrichers, lite custom components.
  For testing specific source/sink implementations (e.g. request-response, kafka etc.) other runners should be used
 */
class LiteTestScenarioRunner(
    components: List[ComponentDefinition],
    globalVariables: Map[String, AnyRef],
    config: Config,
    componentUseCase: ComponentUseCase
) extends ClassBasedTestScenarioRunner {

  /**
    *  Additional source TestScenarioRunner.testDataSource and sink TestScenarioRunner.testResultSink are provided,
    *  so sample scenario should look like:
    *  {{{
    *  .source("source", TestScenarioRunner.testDataSource)
    *    (...)
    *  .emptySink("sink", TestScenarioRunner.testResultSink, "value" -> "#result")
    *  }}}
    */
  override def runWithData[INPUT: ClassTag, OUTPUT](
      scenario: CanonicalProcess,
      data: List[INPUT]
  ): RunnerListResult[OUTPUT] =
    runWithDataReturningDetails(scenario, data)
      .map { case (errors, endResults) =>
        RunListResult(
          errors.map(ExceptionResult.fromNuExceptionInfo(_, identity)),
          endResults.map(_.result.asInstanceOf[OUTPUT])
        )
      }

  def runWithDataReturningDetails[INPUT: ClassTag](scenario: CanonicalProcess, data: List[INPUT]): SynchronousResult = {
    val testSource = ComponentDefinition(TestScenarioRunner.testDataSource, new SimpleSourceFactory(Typed[INPUT]))
    val testSink   = ComponentDefinition(TestScenarioRunner.testResultSink, SimpleSinkFactory)
    val inputId    = scenario.nodes.head.id
    val inputBatch = ScenarioInputBatch(data.map(d => (SourceId(inputId), d: Any)))
    val jobData    = JobData(scenario.metaData, ProcessVersion.empty.copy(processName = scenario.metaData.name))

    val allComponents = testSource ::
      testSink ::
      LiteBaseComponentProvider.Components :::
      components
    val modelData = ModelWithTestExtensions(config, allComponents, globalVariables)
    SynchronousLiteInterpreter.run(modelData, jobData, scenario, inputBatch, componentUseCase)
  }

}

private[test] class SimpleSourceFactory(result: TypingResult)
    extends SourceFactory
    with SingleInputDynamicComponent[Source]
    with WithExplicitTypesToExtract
    with UnboundedStreamComponent {

  override type State = Nothing

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = { case TransformationStep(Nil, _) =>
    val finalInitializer = new BasicContextInitializer(result)
    FinalResults.forValidation(context, Nil, None)(finalInitializer.validationContext)
  }

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[Nothing]
  ): Source = {
    new BaseLiteSource[Any] {
      override val nodeId: NodeId = TypedNodeDependency[NodeId].extract(dependencies)

      override def transform(record: Any): Context =
        Context(
          contextIdGenerator.nextContextId(),
          Map(VariableConstants.InputVariableName -> record),
          None
        )
    }
  }

  override def nodeDependencies: List[NodeDependency] = TypedNodeDependency[NodeId] :: Nil

  override def typesToExtract: List[typing.TypingResult] = List(result)

}

private[test] object SimpleSinkFactory extends SinkFactory {

  @MethodToInvoke
  def create(@ParamName("value") value: LazyParameter[AnyRef]): LazyParamSink[AnyRef] = {
    new LazyParamSink[AnyRef] {
      override def prepareResponse: LazyParameter[AnyRef] = value
    }
  }

}
