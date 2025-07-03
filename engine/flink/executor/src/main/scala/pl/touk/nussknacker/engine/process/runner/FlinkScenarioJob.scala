package pl.touk.nussknacker.engine.process.runner

import org.apache.flink.api.common.JobExecutionResult
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.{BaseModelData, ModelData}
import pl.touk.nussknacker.engine.ModelConfig.LiveDataPreviewMode
import pl.touk.nussknacker.engine.ModelConfig.LiveDataPreviewMode.LiveDataStorage.{DesignerDb, DesignerJvm}
import pl.touk.nussknacker.engine.ModelData.BaseModelDataExt
import pl.touk.nussknacker.engine.api.{ProcessListener, ProcessVersion}
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.livedata.LiveDataCollectingListenerHolder
import pl.touk.nussknacker.engine.process.{ExecutionConfigPreparer, FlinkJobConfig}
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerDataFactory
import pl.touk.nussknacker.engine.process.livedata.PeriodicLiveDataUploader
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
    val liveDataCollectingListener =
      modelData.modelConfig.liveDataPreviewMode match {
        case LiveDataPreviewMode.Disabled =>
          None
        case LiveDataPreviewMode.Enabled(maxNumberOfSamples, throughputTimeWindowInSeconds, _) =>
          Some(
            LiveDataCollectingListenerHolder.createListenerFor(
              processVersion.processName,
              maxNumberOfSamples,
              throughputTimeWindowInSeconds
            )
          )
      }
    val periodicLiveDataUploader =
      modelData.modelConfig.liveDataPreviewMode match {
        case LiveDataPreviewMode.Disabled | LiveDataPreviewMode.Enabled(_, _, DesignerJvm) =>
          None
        case LiveDataPreviewMode.Enabled(_, _, storage: DesignerDb) =>
          Some(
            new PeriodicLiveDataUploader(
              ProcessIdWithName(processVersion.processId, processVersion.processName),
              storage.uploadIntervalInSeconds,
              storage.url,
              storage.user,
              storage.password,
              storage.schema
            )
          )
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
    periodicLiveDataUploader.foreach(_.start())
    val result = env.execute(preparedName)
    periodicLiveDataUploader.foreach(_.close())
    result
  }

}
