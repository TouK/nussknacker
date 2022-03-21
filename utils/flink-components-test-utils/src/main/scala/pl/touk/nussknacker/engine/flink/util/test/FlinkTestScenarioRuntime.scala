package pl.touk.nussknacker.engine.flink.util.test

import com.typesafe.config.Config
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.component.Component
import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, WithCategories}
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.test.FlinkMiniClusterHolder
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.compiler.MockedComponentsFlinkProcessCompiler
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.MockService
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.MockComponentsHolder
import pl.touk.nussknacker.engine.util.test.TestScenarioRuntime

class FlinkTestScenarioRuntime(val components: Map[String, WithCategories[Component]], testConfig: Config, flinkMiniCluster: FlinkMiniClusterHolder) extends TestScenarioRuntime {

  override def run(scenario: EspProcess): Unit = {
    //model
    val modelData = LocalModelData(config, new EmptyProcessConfigCreator)
    val components = MockComponentsHolder.registerMockComponents(this.components)

    //todo get flink mini cluster through composition
    val env = flinkMiniCluster.createExecutionEnvironment()
    val registrar = FlinkProcessRegistrar(new MockedComponentsFlinkProcessCompiler(components, modelData), ExecutionConfigPreparer.unOptimizedChain(modelData))
    registrar.register(new StreamExecutionEnvironment(env), scenario, ProcessVersion.empty, DeploymentData.empty, Some(MockComponentsHolder.testRunId))
    env.executeAndWaitForFinished(scenario.id)()
  }

  override val config: Config = this.testConfig

  override def results(): Any = MockService.data
}
