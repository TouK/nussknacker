package pl.touk.nussknacker.engine.flink.util.test

import com.typesafe.config.Config
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.component.Component
import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.test.FlinkMiniClusterHolder
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerWithTestComponents
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.MockService
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.TestComponentsHolder
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner

import scala.reflect.ClassTag

object testComponents {
  def withDataList[T: ClassTag : TypeInformation](data: List[T]) = Map(
    "source" -> WithCategories(SourceFactory.noParamFromClassTag[T](new CollectionSource[T](new ExecutionConfig, data, None, Typed.apply[T]))),
    "noopSource" -> WithCategories(SourceFactory.noParamFromClassTag[T](new CollectionSource[T](new ExecutionConfig, List.empty, None, Typed.apply[T]))),
    "mockService" -> WithCategories(new MockService)
  )
}

class FlinkTestScenarioRunner(val components: Map[String, WithCategories[Component]], val config: Config, flinkMiniCluster: FlinkMiniClusterHolder) extends TestScenarioRunner {

  override def runWithData[T: ClassTag](scenario: EspProcess, data: List[T]): Unit = {

    implicit val a: TypeInformation[T] = TypeInformation.of(implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]])
    val modelData = LocalModelData(config, new EmptyProcessConfigCreator)
    val componentsWithData = testComponents.withDataList(data)
    val componentsWithTestComponents = this.components ++ componentsWithData
    val testComponentHolder = TestComponentsHolder.registerMockComponents(componentsWithTestComponents)

    //todo get flink mini cluster through composition
    val env = flinkMiniCluster.createExecutionEnvironment()
    val registrar = FlinkProcessRegistrar(new FlinkProcessCompilerWithTestComponents(testComponentHolder, modelData), ExecutionConfigPreparer.unOptimizedChain(modelData))
    registrar.register(new StreamExecutionEnvironment(env), scenario, ProcessVersion.empty, DeploymentData.empty, Some(testComponentHolder.runId))
    env.executeAndWaitForFinished(scenario.id)()
  }

  override def results(): Any = MockService.data
}

object NuTestScenarioRunner {
  def flinkBased(config: Config, flinkMiniCluster: FlinkMiniClusterHolder): FlinkTestScenarioRunnerBuilder = {
    FlinkTestScenarioRunnerBuilder(Map(), config, flinkMiniCluster).copy(config = config, flinkMiniCluster = flinkMiniCluster)
  }
}

case class FlinkTestScenarioRunnerBuilder(components: Map[String, WithCategories[Component]], config: Config, flinkMiniCluster: FlinkMiniClusterHolder) {
  def build() = new FlinkTestScenarioRunner(components, config, flinkMiniCluster)

  def withExtraComponents(components: Map[String, WithCategories[Component]]): FlinkTestScenarioRunnerBuilder = {
    copy(components = components)
  }
}