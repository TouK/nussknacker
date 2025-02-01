package pl.touk.nussknacker.engine.process.runner

import com.typesafe.config.Config
import org.apache.flink.api.common.JobExecutionResult
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.{ModelConfigs, ModelData}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.process.{ExecutionConfigPreparer, FlinkJobConfig}
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerDataFactory
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar

object FlinkScenarioJob {

  def runScenario(
      scenario: CanonicalProcess,
      processVersion: ProcessVersion,
      deploymentData: DeploymentData,
      modelConfig: Config,
      env: StreamExecutionEnvironment
  ): JobExecutionResult = {
    val modelData = ModelData.duringFlinkExecution(ModelConfigs(modelConfig, deploymentData.additionalModelConfigs))
    val compilerFactory         = new FlinkProcessCompilerDataFactory(modelData)
    val executionConfigPreparer = ExecutionConfigPreparer.defaultChain(modelData)
    val registrar =
      FlinkProcessRegistrar(compilerFactory, FlinkJobConfig.parse(modelData.modelConfig), executionConfigPreparer)
    registrar.register(env, scenario, processVersion, deploymentData)
    val preparedName = modelData.namingStrategy.prepareName(scenario.name.value)
    env.execute(preparedName)
  }

}
