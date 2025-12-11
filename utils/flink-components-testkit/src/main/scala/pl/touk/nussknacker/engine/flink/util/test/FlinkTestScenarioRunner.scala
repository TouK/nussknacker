package pl.touk.nussknacker.engine.flink.util.test

import com.typesafe.config.{Config, ConfigValueFactory}
import org.apache.flink.api.common.JobID
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.connector.source.Boundedness
import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings
import org.scalatest.concurrent.ScalaFutures.{convertScalaFuture, scaled, PatienceConfig}
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.nussknacker.defaultmodel.DefaultConfigCreator
import pl.touk.nussknacker.engine.RuntimeMode
import pl.touk.nussknacker.engine.api.{JobData, NodeId, ProcessVersion}
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, NodesDeploymentData}
import pl.touk.nussknacker.engine.api.process.SourceFactory
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.{FlinkBaseUnboundedComponentProvider, FlinkScenarioCompilationDependencies}
import pl.touk.nussknacker.engine.flink.api.serialization.SerializersRegistrar
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterWithServices
import pl.touk.nussknacker.engine.flink.minicluster.MiniClusterJobStatusCheckingOps._
import pl.touk.nussknacker.engine.flink.minicluster.util.DurationToRetryPolicyConverter
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner.ScenarioVerificationFixture
import pl.touk.nussknacker.engine.flink.util.test.TestResultSinkFactory.Output
import pl.touk.nussknacker.engine.flink.util.test.testComponents._
import pl.touk.nussknacker.engine.flink.util.transformer.FlinkBaseComponentProvider
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.process.{ExecutionConfigPreparer, FlinkJobConfig}
import pl.touk.nussknacker.engine.process.FlinkJobConfig.ExecutionMode.ExecutionMode
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListener, TestProcess, TestRunId}
import pl.touk.nussknacker.engine.util.test._
import pl.touk.nussknacker.engine.util.test.TestScenarioCollectorHandler.TestScenarioCollectorHandler
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner.{RunnerListResult, RunnerResultUnit}

import scala.concurrent.{blocking, Future}
import scala.concurrent.duration.DurationInt
import scala.reflect.ClassTag
import scala.util.Using

private object testComponents {

  def testDataSourceComponent[T](
      data: List[T],
      inputType: TypingResult,
      watermarkStrategy: Option[WatermarkStrategy[T]],
      boundedness: Boundedness = Boundedness.CONTINUOUS_UNBOUNDED
  ): ComponentDefinition = ComponentDefinition(
    TestScenarioRunner.testDataSource,
    SourceFactory.noParamUnboundedStreamFactory(
      new CollectionSource[T](
        list = data,
        watermarkStrategy = watermarkStrategy,
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
    components: List[ComponentDefinition],
    serializersRegistrars: List[SerializersRegistrar],
    globalVariables: Map[String, AnyRef],
    modelConfig: Config,
    flinkMiniClusterWithServices: FlinkMiniClusterWithServices,
    runnerRuntimeMode: RuntimeMode,
) extends ClassBasedTestScenarioRunner {

  private val modelData = LocalModelData(
    inputConfig = modelConfig,
    // We can't just pass extra components here because we don't want Flink to serialize them.
    // We also don't want user to make them serializable
    components = FlinkBaseComponentProvider.Components ::: FlinkBaseUnboundedComponentProvider.Components,
    configCreator = new DefaultConfigCreator
  )

  private implicit val WaitForJobStatusPatience: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(20, Seconds)), interval = scaled(Span(50, Millis)))

  private val WaitForJobStatusRetryPolicy = DurationToRetryPolicyConverter.toPausePolicy(
    WaitForJobStatusPatience.timeout - 3.seconds,
    WaitForJobStatusPatience.interval * 2
  )

  override def runWithData[I: ClassTag, R](
      scenario: CanonicalProcess,
      data: List[I]
  ): RunnerListResult[R] = {
    runWithTestSourceComponent(
      scenario,
      NodesDeploymentData.empty,
      labels = List.empty,
      testDataSourceComponent(data, Typed.typedClass[I], None)
    )
  }

  def runWithData[I: ClassTag, R](
      scenario: CanonicalProcess,
      data: List[I],
      boundedness: Boundedness = Boundedness.CONTINUOUS_UNBOUNDED,
      watermarkStrategy: Option[WatermarkStrategy[I]] = None,
      nodesData: NodesDeploymentData = NodesDeploymentData.empty,
      labels: List[String] = List.empty,
  ): RunnerListResult[R] = {
    runWithTestSourceComponent(
      scenario,
      nodesData,
      labels,
      testDataSourceComponent(data, Typed.typedClass[I], watermarkStrategy, boundedness)
    )
  }

  def runWithDataWithType[I, R](
      scenario: CanonicalProcess,
      data: List[I],
      inputType: TypingResult,
      boundedness: Boundedness = Boundedness.CONTINUOUS_UNBOUNDED,
      watermarkStrategy: Option[WatermarkStrategy[I]] = None,
      nodesData: NodesDeploymentData = NodesDeploymentData.empty,
      labels: List[String] = List.empty,
  ): RunnerListResult[R] = {
    runWithTestSourceComponent(
      scenario,
      nodesData,
      labels,
      testDataSourceComponent(data, inputType, watermarkStrategy, boundedness)
    )
  }

  def runWithTestRecords[R](
      scenario: CanonicalProcess,
      records: List[TestDataRecord]
  ): RunnerListResult[R] = {
    Using.resource(
      TestExtensionsHolder.registerTestExtensions(components, testResultSinkComponentCreator :: Nil, globalVariables)
    ) { testComponentHolder =>
      run[R](scenario, NodesDeploymentData.empty, List.empty, testComponentHolder, Some(records))
    }
  }

  private def runWithTestSourceComponent[I, R](
      scenario: CanonicalProcess,
      nodesData: NodesDeploymentData,
      labels: List[String],
      testDataSourceComponent: ComponentDefinition
  ): RunnerListResult[R] = {
    val testComponents = testDataSourceComponent :: noopSourceComponent :: Nil
    Using.resource(
      TestExtensionsHolder
        .registerTestExtensions(components ++ testComponents, testResultSinkComponentCreator :: Nil, globalVariables)
    ) { testComponentHolder =>
      run[R](scenario, nodesData, labels, testComponentHolder, None)
    }
  }

  /**
   * Can be used to test Flink bounded sources - we wait for the scenario to finish.
   */
  def runWithoutData[R](
      scenario: CanonicalProcess,
      nodesData: NodesDeploymentData = NodesDeploymentData.empty,
      labels: List[String] = List.empty,
  ): RunnerListResult[R] = {
    val testComponents = noopSourceComponent :: Nil
    Using.resource(
      TestExtensionsHolder
        .registerTestExtensions(components ++ testComponents, testResultSinkComponentCreator :: Nil, globalVariables)
    ) { testComponentHolder =>
      run[R](scenario, nodesData, labels, testComponentHolder, None)
    }
  }

  /**
   * Can be used to test Flink based sinks.
   */
  def runWithDataIgnoringResults[I: ClassTag](
      scenario: CanonicalProcess,
      data: List[I],
      nodesData: NodesDeploymentData = NodesDeploymentData.empty,
      labels: List[String] = List.empty,
  ): RunnerResultUnit = {
    val testComponents = testDataSourceComponent(data, Typed.typedClass[I], None) :: noopSourceComponent :: Nil
    Using.resource(
      TestExtensionsHolder.registerTestExtensions(components ++ testComponents, List.empty, globalVariables)
    ) { testComponentHolder =>
      run[AnyRef](scenario, nodesData, labels, testComponentHolder, None).map { case RunListResult(errors, _) =>
        RunUnitResult(errors)
      }
    }
  }

  def withRunningScenario[R](
      scenario: CanonicalProcess,
      savepointRestoreSettings: SavepointRestoreSettings = SavepointRestoreSettings.none(),
      nodesData: NodesDeploymentData = NodesDeploymentData.empty,
      labels: List[String] = List.empty
  )(f: ScenarioVerificationFixture => R): R = {
    Using.resource(
      TestExtensionsHolder.registerTestExtensions(components, testResultSinkComponentCreator :: Nil, globalVariables)
    ) { testExtensionsHolder =>
      flinkMiniClusterWithServices.withDetachedStreamExecutionEnvironment { env =>
        TestScenarioCollectorHandler.withHandler(runnerRuntimeMode) { testScenarioCollectorHandler =>
          val compilerFactory =
            FlinkProcessCompilerDataFactoryWithTestComponents(
              testExtensionsHolder,
              testScenarioCollectorHandler.resultsCollectingListener,
              modelData,
              runnerRuntimeMode,
              None,
              nodesData
            )

          val processVersion = ProcessVersion.empty.copy(
            processName = scenario.metaData.name,
            labels = labels
          )

          val registrar = FlinkProcessRegistrar(
            compilerFactory,
            FlinkJobConfig.parse(modelData.modelConfig),
            ExecutionConfigPreparer.unOptimizedChain(modelData, serializersRegistrars),
          )
          registrar.register(
            env,
            scenario,
            processVersion,
            DeploymentData.empty.copy(nodesData = nodesData),
            testScenarioCollectorHandler.servicesResultCollector
          )
          val streamGraph = env.getStreamGraph
          streamGraph.setSavepointRestoreSettings(savepointRestoreSettings)

          val jobExecutionResult = env.execute(streamGraph)
          flinkMiniClusterWithServices
            .withRunningJob(jobExecutionResult.getJobID)(
              WaitForJobStatusRetryPolicy,
              Some(WaitForJobStatusRetryPolicy)
            ) {
              Future {
                blocking {
                  f(
                    new ScenarioVerificationFixture(
                      jobExecutionResult.getJobID,
                      testExtensionsHolder.runId,
                      testScenarioCollectorHandler.resultsCollectingListener
                    )
                  )
                }
              }
            }
            .futureValue
            .toTry
            .get
        }
      }
    }
  }

  private def run[OUTPUT](
      scenario: CanonicalProcess,
      nodesData: NodesDeploymentData,
      labels: List[String],
      testExtensionsHolder: TestExtensionsHolder,
      testRecords: Option[List[TestDataRecord]]
  ): RunnerListResult[OUTPUT] = {
    flinkMiniClusterWithServices.withDetachedStreamExecutionEnvironment { env =>
      TestScenarioCollectorHandler.withHandler(runnerRuntimeMode) { testScenarioCollectorHandler =>
        val processVersion = ProcessVersion.empty.copy(
          processName = scenario.metaData.name,
          labels = labels
        )

        val (compilerFactory, compileProcessData) = prepareCompilerData(
          testExtensionsHolder,
          testScenarioCollectorHandler,
          JobData(scenario.metaData, processVersion),
          nodesData,
          testRecords
        )

        flinkMiniClusterWithServices.withAttachedStreamExecutionEnvironment { envForCompilation =>
          compileProcessData.compileProcess(scenario)(new FlinkScenarioCompilationDependencies(envForCompilation)).map {
            _ =>
              val registrar = FlinkProcessRegistrar(
                compilerFactory,
                FlinkJobConfig.parse(modelData.modelConfig),
                ExecutionConfigPreparer.unOptimizedChain(modelData, serializersRegistrars)
              )

              registrar.register(
                env,
                scenario,
                processVersion,
                DeploymentData.empty.copy(nodesData = nodesData),
                testScenarioCollectorHandler.servicesResultCollector
              )

              val jobExecutionResult = env.execute(scenario.name.value)
              flinkMiniClusterWithServices
                .waitForJobIsFinished(jobExecutionResult.getJobID)(
                  WaitForJobStatusRetryPolicy,
                  Some(WaitForJobStatusRetryPolicy)
                )
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
  }

  private def prepareCompilerData(
      testExtensionsHolder: TestExtensionsHolder,
      testScenarioCollectorHandler: TestScenarioCollectorHandler,
      jobData: JobData,
      nodesData: NodesDeploymentData,
      testRecords: Option[List[TestDataRecord]]
  ) = {
    val compilerFactory =
      FlinkProcessCompilerDataFactoryWithTestComponents(
        testExtensionsHolder,
        testScenarioCollectorHandler.resultsCollectingListener,
        modelData,
        testRecords.map(_ => RuntimeMode.Test).getOrElse(runnerRuntimeMode),
        testRecords,
        nodesData
      )

    val compilerData = compilerFactory.prepareCompilerData(
      jobData.metaData,
      jobData.processVersion,
      testScenarioCollectorHandler.servicesResultCollector,
      getClass.getClassLoader,
    )
    (compilerFactory, compilerData)
  }

  private def tryToCollectResultsFromExternalInvocationResults(
      scenario: CanonicalProcess,
      testScenarioCollectorHandler: TestScenarioCollectorHandler
  ) = {
    val allSinks           = scenario.collectAllNodes.collect { case sink: node.Sink => sink }
    def isSink(id: NodeId) = allSinks.exists(_.id == id.id)
    testScenarioCollectorHandler.resultsCollectingListener.results.externalServiceInvocationResults.flatMap {
      case (id, externalServiceInvocationResults) if isSink(id) =>
        externalServiceInvocationResults.map(_.value)
      case _ =>
        List.empty
    }.toList
  }

}

object FlinkTestScenarioRunner {

  implicit class FlinkTestScenarioRunnerExt(testScenarioRunner: TestScenarioRunner.type) {

    def flinkBased(
        modelConfig: Config,
        flinkMiniClusterWithServices: FlinkMiniClusterWithServices
    ): FlinkTestScenarioRunnerBuilder = {
      FlinkTestScenarioRunnerBuilder(
        components = List.empty,
        serializersRegistrars = List.empty,
        globalVariables = Map.empty,
        modelConfig = modelConfig,
        flinkMiniClusterWithServices = flinkMiniClusterWithServices,
        testRuntimeMode = false
      )
    }

  }

  class ScenarioVerificationFixture(
      val jobId: JobID,
      runId: TestRunId,
      private val testResultsCollectionHandler: ResultsCollectingListener[_]
  ) {

    def testSinkResults: Seq[AnyRef] = TestResultSinkFactory.extractOutputFor(runId) match {
      case Output.NotAvailable =>
        List.empty
      case Output.Available(results) =>
        results.toList
    }

    def errors: List[TestProcess.ExceptionResult[_]] = testResultsCollectionHandler.results.exceptions

  }

}

final case class FlinkTestScenarioRunnerBuilder(
    components: List[ComponentDefinition],
    serializersRegistrars: List[SerializersRegistrar],
    globalVariables: Map[String, AnyRef],
    modelConfig: Config,
    flinkMiniClusterWithServices: FlinkMiniClusterWithServices,
    testRuntimeMode: Boolean
) extends TestScenarioRunnerBuilder[FlinkTestScenarioRunner, FlinkTestScenarioRunnerBuilder] {

  import TestScenarioRunner._

  override def withExtraComponents(components: List[ComponentDefinition]): FlinkTestScenarioRunnerBuilder = {
    copy(components = components)
  }

  def withExtraSerializersRegistrars(
      serializersRegistrars: List[SerializersRegistrar]
  ): FlinkTestScenarioRunnerBuilder =
    copy(serializersRegistrars = serializersRegistrars)

  override def withExtraGlobalVariables(
      globalVariables: Map[String, AnyRef]
  ): FlinkTestScenarioRunnerBuilder =
    copy(globalVariables = globalVariables)

  override def inTestRuntimeMode: FlinkTestScenarioRunnerBuilder =
    copy(testRuntimeMode = true)

  def withExecutionMode(mode: ExecutionMode): FlinkTestScenarioRunnerBuilder =
    copy(modelConfig = modelConfig.withValue("executionMode", ConfigValueFactory.fromAnyRef(mode.toString)))

  override def build() =
    new FlinkTestScenarioRunner(
      components,
      serializersRegistrars,
      globalVariables,
      modelConfig,
      flinkMiniClusterWithServices,
      runtimeMode(testRuntimeMode)
    )

}
