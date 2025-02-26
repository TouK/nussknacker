package pl.touk.nussknacker.engine.process.scenariotesting

import org.apache.flink.api.common.JobExecutionResult
import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.process.{ExecutionConfigPreparer, FlinkJobConfig}
import pl.touk.nussknacker.engine.process.compiler.VerificationFlinkProcessCompilerDataFactory
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListenerHolder, TestServiceInvocationCollector}

object FlinkScenarioStateVerificationJob {

  def run(
      modelData: ModelData,
      scenario: CanonicalProcess,
      processVersion: ProcessVersion,
      savepointPath: String,
      streamExecutionEnv: StreamExecutionEnvironment
  ): JobExecutionResult =
    new FlinkScenarioStateVerificationJob(modelData).run(
      scenario,
      processVersion,
      savepointPath,
      streamExecutionEnv
    )

}

private class FlinkScenarioStateVerificationJob(modelData: ModelData) {

  def run(
      scenario: CanonicalProcess,
      processVersion: ProcessVersion,
      savepointPath: String,
      streamExecutionEnv: StreamExecutionEnvironment
  ): JobExecutionResult = {
    val resultCollector = new TestServiceInvocationCollector(ResultsCollectingListenerHolder.noopListener)
    val registrar       = prepareRegistrar(scenario)
    val deploymentData  = DeploymentData.empty

    registrar.register(
      streamExecutionEnv,
      scenario,
      processVersion,
      deploymentData,
      resultCollector
    )
    streamExecutionEnv.getCheckpointConfig.disableCheckpointing()
    val streamGraph = streamExecutionEnv.getStreamGraph
    streamGraph.setSavepointRestoreSettings(SavepointRestoreSettings.forPath(savepointPath, true))

    streamExecutionEnv.execute(streamGraph)
  }

  protected def prepareRegistrar(scenario: CanonicalProcess): FlinkProcessRegistrar = {
    FlinkProcessRegistrar(
      VerificationFlinkProcessCompilerDataFactory(scenario, modelData),
      FlinkJobConfig.parse(modelData.modelConfig),
      ExecutionConfigPreparer.defaultChain(modelData)
    )
  }

}
