package pl.touk.nussknacker.engine.flink.util.test

import com.typesafe.config.Config
import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.api.connector.source.Boundedness
import pl.touk.nussknacker.defaultmodel.DefaultConfigCreator
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, SourceFactory}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.FlinkBaseUnboundedComponentProvider
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.test.FlinkMiniClusterHolder
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.flink.util.test.TestResultSinkFactory.Output
import pl.touk.nussknacker.engine.flink.util.test.testComponents._
import pl.touk.nussknacker.engine.flink.util.transformer.FlinkBaseComponentProvider
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.process.{ExecutionConfigPreparer, FlinkJobConfig}
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.TestRunId
import pl.touk.nussknacker.engine.util.test.TestScenarioCollectorHandler.TestScenarioCollectorHandler
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner.{RunnerListResult, RunnerResultUnit}
import pl.touk.nussknacker.engine.util.test._

import scala.reflect.ClassTag
import scala.util.Using

private object testComponents {

  def testDataSourceComponent[T](
      data: List[T],
      inputType: TypingResult,
      timestampAssigner: Option[TimestampWatermarkHandler[T]],
      boundedness: Boundedness = Boundedness.CONTINUOUS_UNBOUNDED,
      flinkExecutionMode: Option[RuntimeExecutionMode] = None
  ): ComponentDefinition = ComponentDefinition(
    TestScenarioRunner.testDataSource,
    SourceFactory.noParamUnboundedStreamFactory(
      new CollectionSource[T](
        list = data,
        timestampAssigner = timestampAssigner,
        returnType = inputType,
        boundedness = boundedness,
        flinkRuntimeMode = flinkExecutionMode
      ),
      inputType
    )
  )

  def noopSourceComponent: ComponentDefinition = {
    ComponentDefinition(
      TestScenarioRunner.noopSource,
      SourceFactory.noParamUnboundedStreamFactory(
        new CollectionSource[Any](List.empty, None, Unknown),
        Unknown
      )
    )
  }

  def testResultSinkComponentCreator: TestRunId => ComponentDefinition = { runId =>
    ComponentDefinition(TestScenarioRunner.testResultSink, new TestResultSinkFactory(runId))
  }

}

class FlinkTestScenarioRunner(
    val components: List[ComponentDefinition],
    val globalVariables: Map[String, AnyRef],
    val config: Config,
    flinkMiniCluster: FlinkMiniClusterHolder,
    componentUseCase: ComponentUseCase
) extends ClassBasedTestScenarioRunner {

  override def runWithData[I: ClassTag, R](scenario: CanonicalProcess, data: List[I]): RunnerListResult[R] = {
    runWithTestSourceComponent(scenario, testDataSourceComponent(data, Typed.typedClass[I], None))
  }

  def runWithData[I: ClassTag, R](
      scenario: CanonicalProcess,
      data: List[I],
      boundedness: Boundedness = Boundedness.CONTINUOUS_UNBOUNDED,
      flinkExecutionMode: Option[RuntimeExecutionMode] = None,
      timestampAssigner: Option[TimestampWatermarkHandler[I]] = None
  ): RunnerListResult[R] = {
    runWithTestSourceComponent(
      scenario,
      testDataSourceComponent(data, Typed.typedClass[I], timestampAssigner, boundedness, flinkExecutionMode)
    )
  }

  def runWithDataWithType[I, R](
      scenario: CanonicalProcess,
      data: List[I],
      inputType: TypingResult,
      boundedness: Boundedness = Boundedness.CONTINUOUS_UNBOUNDED,
      flinkExecutionMode: Option[RuntimeExecutionMode] = None,
      timestampAssigner: Option[TimestampWatermarkHandler[I]] = None
  ): RunnerListResult[R] = {
    runWithTestSourceComponent(
      scenario,
      testDataSourceComponent(data, inputType, timestampAssigner, boundedness, flinkExecutionMode)
    )
  }

  private def runWithTestSourceComponent[I: ClassTag, R](
      scenario: CanonicalProcess,
      testDataSourceComponent: ComponentDefinition
  ): RunnerListResult[R] = {
    val testComponents = testDataSourceComponent :: noopSourceComponent :: Nil
    Using.resource(
      TestExtensionsHolder
        .registerTestExtensions(components ++ testComponents, testResultSinkComponentCreator :: Nil, globalVariables)
    ) { testComponentHolder =>
      run[R](scenario, testComponentHolder)
    }
  }

  /**
   * Can be used to test Flink bounded sources - we wait for the scenario to finish.
   */
  def runWithoutData[R](scenario: CanonicalProcess): RunnerListResult[R] = {
    val testComponents = noopSourceComponent :: Nil
    Using.resource(
      TestExtensionsHolder
        .registerTestExtensions(components ++ testComponents, testResultSinkComponentCreator :: Nil, globalVariables)
    ) { testComponentHolder =>
      run[R](scenario, testComponentHolder)
    }
  }

  /**
   * Can be used to test Flink based sinks.
   */
  def runWithDataIgnoringResults[I: ClassTag](scenario: CanonicalProcess, data: List[I]): RunnerResultUnit = {
    val testComponents = testDataSourceComponent(data, Typed.typedClass[I], None) :: noopSourceComponent :: Nil
    Using.resource(
      TestExtensionsHolder.registerTestExtensions(components ++ testComponents, List.empty, globalVariables)
    ) { testComponentHolder =>
      run[AnyRef](scenario, testComponentHolder).map { case RunListResult(errors, _) =>
        RunUnitResult(errors)
      }
    }
  }

  private def run[OUTPUT](
      scenario: CanonicalProcess,
      testExtensionsHolder: TestExtensionsHolder
  ): RunnerListResult[OUTPUT] = {
    val modelData = LocalModelData(
      inputConfig = config,
      // We can't just pass extra components here because we don't want Flink to serialize them.
      // We also don't want user to make them serializable
      components = FlinkBaseComponentProvider.Components ::: FlinkBaseUnboundedComponentProvider.Components,
      configCreator = new DefaultConfigCreator
    )

    // TODO: get flink mini cluster through composition
    val env = flinkMiniCluster.createExecutionEnvironment()

    Using.resource(TestScenarioCollectorHandler.createHandler(componentUseCase)) { testScenarioCollectorHandler =>
      val compilerFactory =
        FlinkProcessCompilerDataFactoryWithTestComponents(
          testExtensionsHolder,
          testScenarioCollectorHandler.resultsCollectingListener,
          modelData,
          componentUseCase
        )

      // We directly use Compiler even if registrar already do this to return compilation errors
      // TODO: figure how to get compilation result on highest level - registrar.register?
      val compileProcessData = compilerFactory.prepareCompilerData(
        scenario.metaData,
        ProcessVersion.empty,
        testScenarioCollectorHandler.resultCollector,
        getClass.getClassLoader
      )

      compileProcessData.compileProcess(scenario).map { _ =>
        val registrar = FlinkProcessRegistrar(
          compilerFactory,
          FlinkJobConfig.parse(modelData.modelConfig),
          ExecutionConfigPreparer.unOptimizedChain(modelData)
        )

        registrar.register(
          env,
          scenario,
          ProcessVersion.empty,
          DeploymentData.empty,
          testScenarioCollectorHandler.resultCollector
        )

        env.executeAndWaitForFinished(scenario.name.value)()

        val successes = TestResultSinkFactory.extractOutputFor(testExtensionsHolder.runId) match {
          case Output.NotAvailable =>
            // we assume that if there is no output, maybe we can try to get from the external invocation results
            tryToCollectResultsFromExternalInvocationResults(scenario, testScenarioCollectorHandler)
          case Output.Available(results) =>
            results.toList
        }
        RunListResult(
          successes = successes.asInstanceOf[List[OUTPUT]],
          errors = testScenarioCollectorHandler.resultsCollectingListener.results.exceptions
        )
      }
    }
  }

  private def tryToCollectResultsFromExternalInvocationResults(
      scenario: CanonicalProcess,
      testScenarioCollectorHandler: TestScenarioCollectorHandler
  ) = {
    val allSinks           = scenario.collectAllNodes.collect { case sink: node.Sink => sink }
    def isSink(id: String) = allSinks.exists(_.id == id)
    testScenarioCollectorHandler.resultsCollectingListener.results.externalInvocationResults.flatMap {
      case (id, externalInvocationResults) if isSink(id) =>
        externalInvocationResults.map(_.value)
      case _ =>
        List.empty
    }.toList
  }

}

object FlinkTestScenarioRunner {

  implicit class FlinkTestScenarioRunnerExt(testScenarioRunner: TestScenarioRunner.type) {

    def flinkBased(config: Config, flinkMiniCluster: FlinkMiniClusterHolder): FlinkTestScenarioRunnerBuilder = {
      FlinkTestScenarioRunnerBuilder(List.empty, Map.empty, config, flinkMiniCluster, testRuntimeMode = false)
    }

  }

}

case class FlinkTestScenarioRunnerBuilder(
    components: List[ComponentDefinition],
    globalVariables: Map[String, AnyRef],
    config: Config,
    flinkMiniCluster: FlinkMiniClusterHolder,
    testRuntimeMode: Boolean
) extends TestScenarioRunnerBuilder[FlinkTestScenarioRunner, FlinkTestScenarioRunnerBuilder] {

  import TestScenarioRunner._

  override def withExtraComponents(components: List[ComponentDefinition]): FlinkTestScenarioRunnerBuilder = {
    copy(components = components)
  }

  override def withExtraGlobalVariables(
      globalVariables: Map[String, AnyRef]
  ): FlinkTestScenarioRunnerBuilder =
    copy(globalVariables = globalVariables)

  override def inTestRuntimeMode: FlinkTestScenarioRunnerBuilder =
    copy(testRuntimeMode = true)

  override def build() =
    new FlinkTestScenarioRunner(
      components,
      globalVariables,
      config,
      flinkMiniCluster,
      componentUseCase(testRuntimeMode)
    )

}
