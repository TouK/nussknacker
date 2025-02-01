package pl.touk.nussknacker.engine.flink.util.test

import com.typesafe.config.{Config, ConfigValueFactory}
import org.apache.flink.api.connector.source.Boundedness
import org.scalatest.concurrent.ScalaFutures.{PatienceConfig, convertScalaFuture, scaled}
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.nussknacker.defaultmodel.DefaultConfigCreator
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, NodesDeploymentData}
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, SourceFactory}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.FlinkBaseUnboundedComponentProvider
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterWithServices
import pl.touk.nussknacker.engine.flink.minicluster.MiniClusterJobStatusCheckingOps._
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner.toRetryPolicy
import pl.touk.nussknacker.engine.flink.util.test.TestResultSinkFactory.Output
import pl.touk.nussknacker.engine.flink.util.test.testComponents._
import pl.touk.nussknacker.engine.flink.util.transformer.FlinkBaseComponentProvider
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.process.FlinkJobConfig.ExecutionMode.ExecutionMode
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.process.{ExecutionConfigPreparer, FlinkJobConfig}
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.TestRunId
import pl.touk.nussknacker.engine.util.test.TestScenarioCollectorHandler.TestScenarioCollectorHandler
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner.{RunnerListResult, RunnerResultUnit}
import pl.touk.nussknacker.engine.util.test._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.ClassTag
import scala.util.Using

private object testComponents {

  def testDataSourceComponent[T](
      data: List[T],
      inputType: TypingResult,
      timestampAssigner: Option[TimestampWatermarkHandler[T]],
      boundedness: Boundedness = Boundedness.CONTINUOUS_UNBOUNDED
  ): ComponentDefinition = ComponentDefinition(
    TestScenarioRunner.testDataSource,
    SourceFactory.noParamUnboundedStreamFactory(
      new CollectionSource[T](
        list = data,
        timestampAssigner = timestampAssigner,
        returnType = inputType,
        boundedness = boundedness
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
    flinkMiniClusterWithServices: FlinkMiniClusterWithServices,
    componentUseCase: ComponentUseCase,
) extends ClassBasedTestScenarioRunner {

  private implicit val WaitForJobStatusPatience: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(20, Seconds)), interval = scaled(Span(10, Millis)))

  override def runWithData[I: ClassTag, R](scenario: CanonicalProcess, data: List[I]): RunnerListResult[R] = {
    runWithTestSourceComponent(
      scenario,
      NodesDeploymentData.empty,
      ProcessVersion.empty.copy(processName = scenario.metaData.name),
      testDataSourceComponent(data, Typed.typedClass[I], None)
    )
  }

  def runWithData[I: ClassTag, R](
      scenario: CanonicalProcess,
      data: List[I],
      boundedness: Boundedness = Boundedness.CONTINUOUS_UNBOUNDED,
      timestampAssigner: Option[TimestampWatermarkHandler[I]] = None,
      nodesData: NodesDeploymentData = NodesDeploymentData.empty,
      processVersion: ProcessVersion = ProcessVersion.empty,
  ): RunnerListResult[R] = {
    runWithTestSourceComponent(
      scenario,
      nodesData,
      processVersion,
      testDataSourceComponent(data, Typed.typedClass[I], timestampAssigner, boundedness)
    )
  }

  def runWithDataWithType[I, R](
      scenario: CanonicalProcess,
      data: List[I],
      inputType: TypingResult,
      boundedness: Boundedness = Boundedness.CONTINUOUS_UNBOUNDED,
      timestampAssigner: Option[TimestampWatermarkHandler[I]] = None,
      nodesData: NodesDeploymentData = NodesDeploymentData.empty,
      processVersion: ProcessVersion = ProcessVersion.empty,
  ): RunnerListResult[R] = {
    runWithTestSourceComponent(
      scenario,
      nodesData,
      processVersion,
      testDataSourceComponent(data, inputType, timestampAssigner, boundedness)
    )
  }

  private def runWithTestSourceComponent[I: ClassTag, R](
      scenario: CanonicalProcess,
      nodesData: NodesDeploymentData = NodesDeploymentData.empty,
      processVersion: ProcessVersion = ProcessVersion.empty,
      testDataSourceComponent: ComponentDefinition
  ): RunnerListResult[R] = {
    val testComponents = testDataSourceComponent :: noopSourceComponent :: Nil
    Using.resource(
      TestExtensionsHolder
        .registerTestExtensions(components ++ testComponents, testResultSinkComponentCreator :: Nil, globalVariables)
    ) { testComponentHolder =>
      run[R](scenario, nodesData, processVersion, testComponentHolder)
    }
  }

  /**
   * Can be used to test Flink bounded sources - we wait for the scenario to finish.
   */
  def runWithoutData[R](
      scenario: CanonicalProcess,
      nodesData: NodesDeploymentData = NodesDeploymentData.empty,
      processVersion: ProcessVersion = ProcessVersion.empty,
  ): RunnerListResult[R] = {
    val testComponents = noopSourceComponent :: Nil
    Using.resource(
      TestExtensionsHolder
        .registerTestExtensions(components ++ testComponents, testResultSinkComponentCreator :: Nil, globalVariables)
    ) { testComponentHolder =>
      run[R](scenario, nodesData, processVersion, testComponentHolder)
    }
  }

  /**
   * Can be used to test Flink based sinks.
   */
  def runWithDataIgnoringResults[I: ClassTag](
      scenario: CanonicalProcess,
      data: List[I],
      nodesData: NodesDeploymentData = NodesDeploymentData.empty,
      processVersion: ProcessVersion = ProcessVersion.empty,
  ): RunnerResultUnit = {
    val testComponents = testDataSourceComponent(data, Typed.typedClass[I], None) :: noopSourceComponent :: Nil
    Using.resource(
      TestExtensionsHolder.registerTestExtensions(components ++ testComponents, List.empty, globalVariables)
    ) { testComponentHolder =>
      run[AnyRef](scenario, nodesData, processVersion, testComponentHolder).map { case RunListResult(errors, _) =>
        RunUnitResult(errors)
      }
    }
  }

  private def run[OUTPUT](
      scenario: CanonicalProcess,
      nodesData: NodesDeploymentData,
      processVersion: ProcessVersion,
      testExtensionsHolder: TestExtensionsHolder
  ): RunnerListResult[OUTPUT] = {
    val modelData = LocalModelData(
      inputConfig = config,
      // We can't just pass extra components here because we don't want Flink to serialize them.
      // We also don't want user to make them serializable
      components = FlinkBaseComponentProvider.Components ::: FlinkBaseUnboundedComponentProvider.Components,
      configCreator = new DefaultConfigCreator
    )

    flinkMiniClusterWithServices.withDetachedStreamExecutionEnvironment { env =>
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
          processVersion,
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
            processVersion,
            DeploymentData.empty.copy(nodesData = nodesData),
            testScenarioCollectorHandler.resultCollector
          )

          val jobExecutionResult = env.execute(scenario.name.value)
          flinkMiniClusterWithServices.miniCluster
            .waitForFinished(jobExecutionResult.getJobID)(toRetryPolicy(WaitForJobStatusPatience))
            .futureValue
            .toTry
            .get

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

    def flinkBased(
        config: Config,
        flinkMiniClusterWithServices: FlinkMiniClusterWithServices
    ): FlinkTestScenarioRunnerBuilder = {
      FlinkTestScenarioRunnerBuilder(
        List.empty,
        Map.empty,
        config,
        flinkMiniClusterWithServices,
        testRuntimeMode = false
      )
    }

  }

  // FIXME abr: unit test
  private def toRetryPolicy(patience: PatienceConfig) = {
    val maxAttempts = Math.max(Math.round(patience.timeout / patience.interval).toInt, 1)
    val delta       = Span(50, Millis)
    val interval    = (patience.timeout - delta) / maxAttempts
    retry.Pause(maxAttempts, interval)
  }

}

case class FlinkTestScenarioRunnerBuilder(
    components: List[ComponentDefinition],
    globalVariables: Map[String, AnyRef],
    config: Config,
    flinkMiniClusterWithServices: FlinkMiniClusterWithServices,
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

  def withExecutionMode(mode: ExecutionMode): FlinkTestScenarioRunnerBuilder =
    copy(config = config.withValue("executionMode", ConfigValueFactory.fromAnyRef(mode.toString)))

  override def build() =
    new FlinkTestScenarioRunner(
      components,
      globalVariables,
      config,
      flinkMiniClusterWithServices,
      componentUseCase(testRuntimeMode)
    )

}
