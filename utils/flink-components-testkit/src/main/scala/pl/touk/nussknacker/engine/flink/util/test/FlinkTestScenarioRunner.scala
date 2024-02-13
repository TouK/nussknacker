package pl.touk.nussknacker.engine.flink.util.test

import com.typesafe.config.Config
import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.defaultmodel.DefaultConfigCreator
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, SourceFactory}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.test.FlinkMiniClusterHolder
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.flink.util.test.testComponents.{
  noopSourceComponent,
  testDataSourceComponent,
  testResultServiceComponent
}
import pl.touk.nussknacker.engine.flink.util.transformer.FlinkBaseComponentProvider
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.process.{ExecutionConfigPreparer, FlinkJobConfig}
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner.{RunnerListResult, RunnerResult}
import pl.touk.nussknacker.engine.util.test._

import scala.reflect.ClassTag
import scala.util.Using

private object testComponents {

  def testDataSourceComponent[T: ClassTag: TypeInformation](
      data: List[T],
      timestampAssigner: Option[TimestampWatermarkHandler[T]]
  ): ComponentDefinition = {
    ComponentDefinition(
      TestScenarioRunner.testDataSource,
      SourceFactory.noParamUnboundedStreamFromClassTag[T](
        new CollectionSource[T](data, timestampAssigner, Typed.apply[T])
      )
    )
  }

  def noopSourceComponent: ComponentDefinition = {
    implicit val typeInf: TypeInformation[Any] = TypeInformation.of(classOf[Any])
    ComponentDefinition(
      TestScenarioRunner.noopSource,
      SourceFactory.noParamUnboundedStreamFromClassTag[Any](
        new CollectionSource[Any](List.empty, None, typing.Unknown)
      )
    )
  }

  def testResultServiceComponent: ComponentDefinition = {
    ComponentDefinition(TestScenarioRunner.testResultService, new TestResultService)
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
    implicit val typeInf: TypeInformation[I] =
      TypeInformation.of(implicitly[ClassTag[I]].runtimeClass.asInstanceOf[Class[I]])
    runWithTestSourceComponent(scenario, testDataSourceComponent(data, None))
  }

  /**
   * Can be used to test Flink aggregates where record timestamp is crucial
   */
  def runWithDataAndTimestampAssigner[I: ClassTag, R](
      scenario: CanonicalProcess,
      data: List[I],
      timestampAssigner: TimestampWatermarkHandler[I]
  ): RunnerListResult[R] = {
    implicit val typeInf: TypeInformation[I] =
      TypeInformation.of(implicitly[ClassTag[I]].runtimeClass.asInstanceOf[Class[I]])
    runWithTestSourceComponent(scenario, testDataSourceComponent(data, Some(timestampAssigner)))
  }

  private def runWithTestSourceComponent[I: ClassTag, R](
      scenario: CanonicalProcess,
      testDataSourceComponent: ComponentDefinition
  ): RunnerListResult[R] = {
    val testComponents = testDataSourceComponent :: noopSourceComponent :: testResultServiceComponent :: Nil
    Using.resource(TestExtensionsHolder.registerTestExtensions(components ++ testComponents, globalVariables)) {
      testComponentHolder =>
        run(scenario, testComponentHolder).map { runResult =>
          collectResults(testComponentHolder, runResult)
        }
    }
  }

  /**
   * Can be used to test Flink bounded sources - we wait for the scenario to finish.
   */
  def runWithoutData[R](scenario: CanonicalProcess): RunnerListResult[R] = {
    val testComponents = noopSourceComponent :: testResultServiceComponent :: Nil
    Using.resource(TestExtensionsHolder.registerTestExtensions(components ++ testComponents, globalVariables)) {
      testComponentHolder =>
        run(scenario, testComponentHolder).map { runResult =>
          collectResults(testComponentHolder, runResult)
        }
    }
  }

  /**
   * Can be used to test Flink based sinks.
   */
  def runWithDataIgnoringResults[I: ClassTag](scenario: CanonicalProcess, data: List[I]): RunnerResult = {
    implicit val typeInf: TypeInformation[I] =
      TypeInformation.of(implicitly[ClassTag[I]].runtimeClass.asInstanceOf[Class[I]])
    val testComponents = testDataSourceComponent(data, None) :: noopSourceComponent :: Nil
    Using.resource(TestExtensionsHolder.registerTestExtensions(components ++ testComponents, globalVariables)) {
      testComponentHolder =>
        run(scenario, testComponentHolder)
    }
  }

  private def run(scenario: CanonicalProcess, testExtensionsHolder: TestExtensionsHolder): RunnerResult = {
    val modelData = LocalModelData(
      inputConfig = config,
      // We can't just pass extra components here because we don't want Flink to serialize them.
      // We also don't want user to make them serializable
      components = FlinkBaseComponentProvider.Components,
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

        RunUnitResult(errors = testScenarioCollectorHandler.resultsCollectingListener.results.exceptions)
      }
    }
  }

  private def collectResults[R](
      testExtensionsHolder: TestExtensionsHolder,
      runResult: RunResult
  ): RunListResult[R] = {
    val results = TestResultService.extractFromTestComponentsHolder(testExtensionsHolder)
    RunListResult(successes = results, errors = runResult.errors)
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
