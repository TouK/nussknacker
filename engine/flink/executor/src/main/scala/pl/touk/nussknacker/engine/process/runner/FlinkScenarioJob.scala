package pl.touk.nussknacker.engine.process.runner

import org.apache.flink.api.common.JobExecutionResult
import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.ModelData.BaseModelDataExt
import pl.touk.nussknacker.engine.{BaseModelData, ModelData}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerDataFactory
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.process.{ExecutionConfigPreparer, FlinkJobConfig}

object FlinkScenarioJob {

  def run(
      modelData: BaseModelData,
      scenario: CanonicalProcess,
      processVersion: ProcessVersion,
      deploymentData: DeploymentData,
      savepointPathOpt: Option[String],
      env: StreamExecutionEnvironment
  ): JobExecutionResult =
    new FlinkScenarioJob(modelData.asInvokableModelData).run(
      scenario,
      processVersion,
      deploymentData,
      savepointPathOpt,
      env
    )

}

class FlinkScenarioJob(modelData: ModelData) {

  def run(
      scenario: CanonicalProcess,
      processVersion: ProcessVersion,
      deploymentData: DeploymentData,
      savepointPathOpt: Option[String],
      env: StreamExecutionEnvironment
  ): JobExecutionResult = {
    val compilerFactory         = new FlinkProcessCompilerDataFactory(modelData)
    val executionConfigPreparer = ExecutionConfigPreparer.defaultChain(modelData)
    val registrar =
      FlinkProcessRegistrar(compilerFactory, FlinkJobConfig.parse(modelData.modelConfig), executionConfigPreparer)
    registrar.register(env, scenario, processVersion, deploymentData)
    val streamGraph = env.getStreamGraph
    savepointPathOpt.foreach { savepointPath =>
      streamGraph.setSavepointRestoreSettings(SavepointRestoreSettings.forPath(savepointPath, true))
    }
    val preparedName = modelData.namingStrategy.prepareName(scenario.name.value)
    streamGraph.setJobName(preparedName)
    env.execute(streamGraph)
  }

}
