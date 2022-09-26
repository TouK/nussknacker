package pl.touk.nussknacker.engine.flink.util.test

import com.typesafe.config.Config
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, SourceFactory}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
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

  def testDataSourceComponent[T: ClassTag : TypeInformation](data: List[T]): ComponentDefinition = {
    ComponentDefinition(TestScenarioRunner.testDataSource, SourceFactory.noParamFromClassTag[T](new CollectionSource[T](data, None, Typed.apply[T])))
  }

  def noopSourceComponent: ComponentDefinition = {
    implicit val typeInf: TypeInformation[Any] = TypeInformation.of(classOf[Any])
    ComponentDefinition(TestScenarioRunner.noopSource, SourceFactory.noParamFromClassTag[Any](new CollectionSource[Any](List.empty, None, typing.Unknown)))
  }

  def testResultServiceComponent: ComponentDefinition = {
    ComponentDefinition(TestScenarioRunner.testResultService, new TestResultService)
  }

}

class FlinkTestScenarioRunner(val components: List[ComponentDefinition], val config: Config, flinkMiniCluster: FlinkMiniClusterHolder) extends ClassBasedTestScenarioRunner {

  override def runWithData[I: ClassTag, R](scenario: CanonicalProcess, data: List[I]): RunnerListResult[R] = {
    implicit val typeInf: TypeInformation[I] = TypeInformation.of(implicitly[ClassTag[I]].runtimeClass.asInstanceOf[Class[I]])
    val testComponents = testDataSourceComponent(data) :: noopSourceComponent :: testResultServiceComponent :: Nil
    val testComponentHolder = TestComponentsHolder.registerTestComponents(components ++ testComponents)
    run(scenario, testComponentHolder).map { _ =>
      collectResults(testComponentHolder)
    }
  }

  /**
   * Can be used to test Flink bounded sources - we wait for the scenario to finish.
   */
  def runWithoutData[R](scenario: CanonicalProcess): RunnerListResult[R] = {
    val testComponents = noopSourceComponent :: testResultServiceComponent :: Nil
    val testComponentHolder = TestComponentsHolder.registerTestComponents(components ++ testComponents)
    run(scenario, testComponentHolder).map { _ =>
      collectResults(testComponentHolder)
    }
  }

  /**
   * Can be used to test Flink based sinks.
   */
  def runWithDataIgnoringResults[I: ClassTag](scenario: CanonicalProcess, data: List[I]): RunnerResult[Unit] = {
    implicit val typeInf: TypeInformation[I] = TypeInformation.of(implicitly[ClassTag[I]].runtimeClass.asInstanceOf[Class[I]])
    val testComponents = testDataSourceComponent(data) :: noopSourceComponent :: Nil
    val testComponentHolder = TestComponentsHolder.registerTestComponents(components ++ testComponents)
    run(scenario, testComponentHolder)
  }

  private def run(scenario: CanonicalProcess,
                  testComponentHolder: TestComponentsHolder): RunnerResult[Unit] = {
    val modelData = LocalModelData(config, new EmptyProcessConfigCreator)

    //todo get flink mini cluster through composition
    val env = flinkMiniCluster.createExecutionEnvironment()

    //It's copied from registrar.register only for handling compilation errors..
    //TODO: figure how to get compilation result on highest level - registrar.register?
    val compiler = new FlinkProcessCompilerWithTestComponents(testComponentHolder, modelData)
    val compileProcessData = compiler.compileProcess(scenario, ProcessVersion.empty, DeploymentData.empty, ProductionServiceInvocationCollector, getClass.getClassLoader)

    compileProcessData.compileProcess().map { _ =>
      val registrar = FlinkProcessRegistrar(compiler, ExecutionConfigPreparer.unOptimizedChain(modelData))
      registrar.register(new StreamExecutionEnvironment(env), scenario, ProcessVersion.empty, DeploymentData.empty, Some(testComponentHolder.runId))
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
      FlinkTestScenarioRunnerBuilder(List.empty, config, flinkMiniCluster)
    }
  }

}

object NuTestScenarioRunner {
  @deprecated("Use: import FlinkTestScenarioRunner._; TestScenarioRunner.flinkBased(...) instead", "1.6")
  def flinkBased(config: Config, flinkMiniCluster: FlinkMiniClusterHolder): FlinkTestScenarioRunnerBuilder = {
    FlinkTestScenarioRunnerBuilder(List.empty, config, flinkMiniCluster)
  }
}

case class FlinkTestScenarioRunnerBuilder(components: List[ComponentDefinition], config: Config, flinkMiniCluster: FlinkMiniClusterHolder)
  extends TestScenarioRunnerBuilder[FlinkTestScenarioRunner, FlinkTestScenarioRunnerBuilder] {

  override def withExtraComponents(components: List[ComponentDefinition]): FlinkTestScenarioRunnerBuilder = {
    copy(components = components)
  }

  override def build() = new FlinkTestScenarioRunner(components, config, flinkMiniCluster)

}
