package pl.touk.nussknacker.engine.flink.util.test

import com.typesafe.config.Config
import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, EmptyProcessConfigCreator, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.test.FlinkMiniClusterHolder
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.flink.util.test.testComponents.{noopSourceComponent, testDataSourceComponent, testResultServiceComponent}
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner.{RunnerListResult, RunnerResult}
import pl.touk.nussknacker.engine.util.test._

import scala.reflect.ClassTag

private object testComponents {

  def testDataSourceComponent[T: ClassTag : TypeInformation](data: List[T], timestampAssigner: Option[TimestampWatermarkHandler[T]]): ComponentDefinition = {
    ComponentDefinition(TestScenarioRunner.testDataSource, SourceFactory.noParamFromClassTag[T](new CollectionSource[T](data, timestampAssigner, Typed.apply[T])))
  }

  def noopSourceComponent: ComponentDefinition = {
    implicit val typeInf: TypeInformation[Any] = TypeInformation.of(classOf[Any])
    ComponentDefinition(TestScenarioRunner.noopSource, SourceFactory.noParamFromClassTag[Any](new CollectionSource[Any](List.empty, None, typing.Unknown)))
  }

  def testResultServiceComponent: ComponentDefinition = {
    ComponentDefinition(TestScenarioRunner.testResultService, new TestResultService)
  }

}

class FlinkTestScenarioRunner(val components: List[ComponentDefinition],
                              val globalProcessVariables: Map[String, WithCategories[AnyRef]],
                              val config: Config,
                              flinkMiniCluster: FlinkMiniClusterHolder,
                              componentUseCase: ComponentUseCase
                             ) extends ClassBasedTestScenarioRunner {
  override def runWithData[I: ClassTag, R](scenario: CanonicalProcess, data: List[I]): RunnerListResult[R] = {
    implicit val typeInf: TypeInformation[I] = TypeInformation.of(implicitly[ClassTag[I]].runtimeClass.asInstanceOf[Class[I]])
    runWithTestSourceComponent(scenario, testDataSourceComponent(data, None))
  }

  /**
   * Can be used to test Flink aggregates where record timestamp is crucial
   */
  def runWithDataAndTimestampAssigner[I: ClassTag, R](scenario: CanonicalProcess, data: List[I], timestampAssigner: TimestampWatermarkHandler[I]): RunnerListResult[R] = {
    implicit val typeInf: TypeInformation[I] = TypeInformation.of(implicitly[ClassTag[I]].runtimeClass.asInstanceOf[Class[I]])
    runWithTestSourceComponent(scenario, testDataSourceComponent(data, Some(timestampAssigner)))
  }

  private def runWithTestSourceComponent[I: ClassTag, R](scenario: CanonicalProcess, testDataSourceComponent: ComponentDefinition): RunnerListResult[R] = {
    val testComponents = testDataSourceComponent :: noopSourceComponent :: testResultServiceComponent :: Nil
    val testComponentHolder = TestComponentsHolder.registerTestComponents(components ++ testComponents, globalProcessVariables)
    run(scenario, testComponentHolder).map { _ =>
      collectResults(testComponentHolder)
    }
  }

  /**
   * Can be used to test Flink bounded sources - we wait for the scenario to finish.
   */
  def runWithoutData[R](scenario: CanonicalProcess): RunnerListResult[R] = {
    val testComponents = noopSourceComponent :: testResultServiceComponent :: Nil
    val testComponentHolder = TestComponentsHolder.registerTestComponents(components ++ testComponents, globalProcessVariables)
    run(scenario, testComponentHolder).map { _ =>
      collectResults(testComponentHolder)
    }
  }

  /**
   * Can be used to test Flink based sinks.
   */
  def runWithDataIgnoringResults[I: ClassTag](scenario: CanonicalProcess, data: List[I]): RunnerResult[Unit] = {
    implicit val typeInf: TypeInformation[I] = TypeInformation.of(implicitly[ClassTag[I]].runtimeClass.asInstanceOf[Class[I]])
    val testComponents = testDataSourceComponent(data, None) :: noopSourceComponent :: Nil
    val testComponentHolder = TestComponentsHolder.registerTestComponents(components ++ testComponents, globalProcessVariables)
    run(scenario, testComponentHolder)
  }

  private def run(scenario: CanonicalProcess,
                  testComponentHolder: TestComponentsHolder): RunnerResult[Unit] = {
    val modelData = LocalModelData(config, new EmptyProcessConfigCreator)

    //todo get flink mini cluster through composition
    val env = flinkMiniCluster.createExecutionEnvironment()

    //It's copied from registrar.register only for handling compilation errors..
    //TODO: figure how to get compilation result on highest level - registrar.register?
    val compiler = new FlinkProcessCompilerWithTestComponents(testComponentHolder, modelData, componentUseCase)
    val compileProcessData = compiler.compileProcess(scenario, ProcessVersion.empty, ProductionServiceInvocationCollector, getClass.getClassLoader)

    compileProcessData.compileProcess().map { _ =>
      val registrar = FlinkProcessRegistrar(compiler, ExecutionConfigPreparer.unOptimizedChain(modelData))
      registrar.register(env, scenario, ProcessVersion.empty, DeploymentData.empty, testRunId = None)
      env.executeAndWaitForFinished(scenario.id)()
      RunUnitResult(errors = Nil)
    }
  }

  private def collectResults[R](testComponentHolder: TestComponentsHolder): RunListResult[R] = {
    val results = TestResultService.extractFromTestComponentsHolder(testComponentHolder)
    //TODO: add runtime errors handling
    RunResult.successes(results)
  }

}

object FlinkTestScenarioRunner {

  implicit class FlinkTestScenarioRunnerExt(testScenarioRunner: TestScenarioRunner.type) {
    def flinkBased(config: Config, flinkMiniCluster: FlinkMiniClusterHolder): FlinkTestScenarioRunnerBuilder = {
      FlinkTestScenarioRunnerBuilder(List.empty, Map.empty, config, flinkMiniCluster, testRuntimeMode = false)
    }
  }

}

case class FlinkTestScenarioRunnerBuilder(components: List[ComponentDefinition], globalProcessVariables: Map[String, WithCategories[AnyRef]], config: Config, flinkMiniCluster: FlinkMiniClusterHolder, testRuntimeMode: Boolean)
  extends TestScenarioRunnerBuilder[FlinkTestScenarioRunner, FlinkTestScenarioRunnerBuilder] {

  import TestScenarioRunner._

  override def withExtraComponents(components: List[ComponentDefinition]): FlinkTestScenarioRunnerBuilder =
    copy(components = components)

  override def withGlobalProcessVariables(globalProcessVariables: Map[String, WithCategories[AnyRef]]): FlinkTestScenarioRunnerBuilder =
    copy(globalProcessVariables = globalProcessVariables)

  override def inTestRuntimeMode: FlinkTestScenarioRunnerBuilder =
    copy(testRuntimeMode = true)

  override def build() = new FlinkTestScenarioRunner(components, globalProcessVariables, config, flinkMiniCluster, componentUseCase(testRuntimeMode))

}
