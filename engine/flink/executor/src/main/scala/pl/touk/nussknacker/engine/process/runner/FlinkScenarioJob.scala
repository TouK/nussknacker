package pl.touk.nussknacker.engine.process.runner

import org.apache.flink.api.common.JobExecutionResult
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.{BaseModelData, ModelData}
import pl.touk.nussknacker.engine.ModelConfig.LiveDataPreviewMode
import pl.touk.nussknacker.engine.ModelConfig.LiveDataPreviewMode.LiveDataStorage
import pl.touk.nussknacker.engine.ModelData.BaseModelDataExt
import pl.touk.nussknacker.engine.api.{ProcessListener, ProcessVersion}
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.livedata.LiveDataCollectingListener
import pl.touk.nussknacker.engine.livedata.LiveDataUploader.LiveDataUploaderConfig
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
      skipLiveDataUploaderWithReason: Option[String],
  ): JobExecutionResult =
    new FlinkScenarioJob(modelData.asInvokableModelData).run(
      scenario = scenario,
      processVersion = processVersion,
      deploymentData = deploymentData,
      env = env,
      processListeners = processListeners,
      skipLiveDataUploaderWithReason = skipLiveDataUploaderWithReason
    )

}

class FlinkScenarioJob(modelData: ModelData) {

  def run(
      scenario: CanonicalProcess,
      processVersion: ProcessVersion,
      deploymentData: DeploymentData,
      env: StreamExecutionEnvironment,
      processListeners: List[ProcessListener],
      skipLiveDataUploaderWithReason: Option[String],
  ): JobExecutionResult = {
    val liveDataCollectingListener = modelData.modelConfig.liveDataPreviewMode match {
      case enabledConfig: LiveDataPreviewMode.Enabled =>
        Some(
          LiveDataCollectingListener.createListenerFor(
            ProcessIdWithName(processVersion.processId, processVersion.processName),
            deploymentData.deploymentId.toNewDeploymentIdOpt,
            enabledConfig,
            skipLiveDataUploaderWithReason
          )
        )
      case LiveDataPreviewMode.Disabled =>
        None
    }
    val compilerFactory = new FlinkProcessCompilerDataFactory(
      modelData,
      deploymentData,
      processListeners ++ liveDataCollectingListener.toList
    )
    val executionConfigPreparer = ExecutionConfigPreparer.defaultChain(modelData)
    val registrar =
      FlinkProcessRegistrar(compilerFactory, FlinkJobConfig.parse(modelData.modelConfig), executionConfigPreparer)
    registrar.register(env, scenario, processVersion, deploymentData)
    val preparedName = modelData.namingStrategy.prepareName(scenario.name.value)
    env.execute(preparedName)
  }

}
