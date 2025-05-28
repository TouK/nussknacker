package pl.touk.nussknacker.engine.process.runner

import org.apache.flink.api.common.JobExecutionResult
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.{BaseModelData, ModelData}
import pl.touk.nussknacker.engine.ModelData.BaseModelDataExt
import pl.touk.nussknacker.engine.api.{ProcessListener, ProcessVersion}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.process.{ExecutionConfigPreparer, FlinkJobConfig}
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerDataFactory
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar

object FlinkScenarioJob {

  def run(
      modelData: BaseModelData,
      scenario: CanonicalProcess,
      processVersion: ProcessVersion,
      deploymentData: DeploymentData,
      env: StreamExecutionEnvironment,
      processListeners: List[ProcessListener],
  ): JobExecutionResult =
    new FlinkScenarioJob(modelData.asInvokableModelData).run(
      scenario,
      processVersion,
      deploymentData,
      env,
      processListeners,
    )

}

class FlinkScenarioJob(modelData: ModelData) {

  def run(
      scenario: CanonicalProcess,
      processVersion: ProcessVersion,
      deploymentData: DeploymentData,
      env: StreamExecutionEnvironment,
      processListeners: List[ProcessListener],
  ): JobExecutionResult = {
    val compilerFactory         = new FlinkProcessCompilerDataFactory(modelData, deploymentData, processListeners)
    val executionConfigPreparer = ExecutionConfigPreparer.defaultChain(modelData)
    val registrar =
      FlinkProcessRegistrar(compilerFactory, FlinkJobConfig.parse(modelData.modelConfig), executionConfigPreparer)
    registrar.register(env, scenario, processVersion, deploymentData)
    val preparedName = modelData.namingStrategy.prepareName(scenario.name.value)
    env.execute(preparedName)
  }

}
