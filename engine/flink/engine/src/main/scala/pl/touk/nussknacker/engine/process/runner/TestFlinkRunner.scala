package pl.touk.nussknacker.engine.process.runner

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.flink.streaming.api.environment
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator
import pl.touk.nussknacker.engine.api.graph.EspProcess
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.TestRunId

object TestFlinkRunner {

  def registerInEnvironmentWithModel(env: environment.StreamExecutionEnvironment, modelData: ModelData)(scenario: EspProcess,
                                                                                                        deploymentData: DeploymentData = DeploymentData.empty,
                                                                                                        version: ProcessVersion = ProcessVersion.empty,
                                                                                                        testRunId: Option[TestRunId] = None): Unit = {
    val registrar = FlinkProcessRegistrar(new FlinkProcessCompiler(modelData), ExecutionConfigPreparer.unOptimizedChain(modelData))
    registrar.register(new StreamExecutionEnvironment(env), scenario, version, deploymentData, testRunId)
  }

  def registerInEnvironment(env: environment.StreamExecutionEnvironment,
                            configCreator: ProcessConfigCreator, config: Config = ConfigFactory.empty())
                           (scenario: EspProcess,
                            deploymentData: DeploymentData = DeploymentData.empty,
                            version: ProcessVersion = ProcessVersion.empty,
                            testRunId: Option[TestRunId] = None): Unit = {
    val modelData = LocalModelData(config, configCreator)
    registerInEnvironmentWithModel(env, modelData)(scenario, deploymentData, version, testRunId)
  }


}
