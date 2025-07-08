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
import pl.touk.nussknacker.engine.livedata.LiveDataCollectingListenerHolder
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
    val liveDataPreviewMode = modelData.modelConfig.liveDataPreviewMode
    lazy val liveDataUploaderConfigOpt = liveDataPreviewMode match {
      case LiveDataPreviewMode.Enabled(_, _, dbStorage: LiveDataStorage.DesignerDb) =>
        Some(
          LiveDataUploaderConfig(
            intervalSeconds = dbStorage.uploadIntervalInSeconds,
            dbUrl = dbStorage.url,
            dbUser = dbStorage.user,
            dbPassword = dbStorage.password,
            dbSchema = dbStorage.schema,
          )
        )
      case _ =>
        None
    }
    val liveDataCollectingListener = liveDataPreviewMode match {
      case LiveDataPreviewMode.Enabled(maxNumberOfSamples, throughputTimeWindowInSeconds, _) =>
        Some(
          LiveDataCollectingListenerHolder.createListenerFor(
            ProcessIdWithName(processVersion.processId, processVersion.processName),
            deploymentData.deploymentId.toNewDeploymentIdOpt,
            liveDataUploaderConfigOpt,
            maxNumberOfSamples,
            throughputTimeWindowInSeconds
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
