package pl.touk.nussknacker.engine.process.scenariotesting

import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.process.compiler.VerificationFlinkProcessCompilerDataFactory
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.process.{ExecutionConfigPreparer, FlinkJobConfig}
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListenerHolder, TestServiceInvocationCollector}

object FlinkStateStateVerificationJob {

  def run(
      modelData: ModelData,
      scenario: CanonicalProcess,
      processVersion: ProcessVersion,
      savepointPath: String,
      streamExecutionEnv: StreamExecutionEnvironment
  ): Unit =
    new FlinkStateStateVerificationJob(modelData).run(
      scenario,
      processVersion,
      savepointPath,
      streamExecutionEnv
    )

}

class FlinkStateStateVerificationJob(modelData: ModelData) {

  def run(
      scenario: CanonicalProcess,
      processVersion: ProcessVersion,
      savepointPath: String,
      streamExecutionEnv: StreamExecutionEnvironment
  ): Unit = {
    val collectingListener = ResultsCollectingListenerHolder.registerTestEngineListener
    try {
      val resultCollector = new TestServiceInvocationCollector(collectingListener)
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
    } finally {
      collectingListener.clean()
    }
  }

  protected def prepareRegistrar(scenario: CanonicalProcess): FlinkProcessRegistrar = {
    FlinkProcessRegistrar(
      VerificationFlinkProcessCompilerDataFactory(scenario, modelData),
      FlinkJobConfig.parse(modelData.modelConfig),
      ExecutionConfigPreparer.defaultChain(modelData)
    )
  }

}
