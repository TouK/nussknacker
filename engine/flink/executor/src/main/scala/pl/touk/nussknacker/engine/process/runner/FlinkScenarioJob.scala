package pl.touk.nussknacker.engine.process.runner

import org.apache.flink.api.common.JobExecutionResult
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.{BaseModelData, ModelData}
import pl.touk.nussknacker.engine.ModelConfig.LiveDataPreviewMode
import pl.touk.nussknacker.engine.ModelData.BaseModelDataExt
import pl.touk.nussknacker.engine.api.{ProcessListener, ProcessVersion}
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.livedata.{LiveDataCollectingListener, LiveDataResultCollector}
import pl.touk.nussknacker.engine.process.{ExecutionConfigPreparer, FlinkJobConfig}
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerDataFactory
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector

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
    val processIdWithName = ProcessIdWithName(processVersion.processId, processVersion.processName)
    val (liveDataCollectingListener, resultCollector) = modelData.modelConfig.liveDataPreviewMode match {
      case enabledConfig: LiveDataPreviewMode.Enabled =>
        (
          Some(
            LiveDataCollectingListener.createListenerFor(
              processIdWithName,
              deploymentData.deploymentId.toNewDeploymentIdOpt,
              enabledConfig,
              skipLiveDataUploaderWithReason
            )
          ),
          new LiveDataResultCollector(
            processIdWithName.name,
            enabledConfig.maxNumberOfRecords,
            enabledConfig.retentionTime,
            enabledConfig.throughputTimeWindow
          )
        )
      case LiveDataPreviewMode.Disabled =>
        (
          None,
          ProductionServiceInvocationCollector
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
    registrar.register(env, scenario, processVersion, deploymentData, resultCollector)
    val preparedName = modelData.namingStrategy.prepareName(scenario.name.value)
    env.execute(preparedName)
  }

}
