package pl.touk.nussknacker.engine.process.scenariotesting

import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.process.compiler.VerificationFlinkProcessCompilerDataFactory
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.process.scenariotesting.legacyadhocminicluster.LegacyAdHocMiniClusterFallbackHandler
import pl.touk.nussknacker.engine.process.{ExecutionConfigPreparer, FlinkJobConfig}
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListenerHolder, TestServiceInvocationCollector}

object FlinkVerificationMain {

  def run(
      sharedMiniClusterServicesOpt: Option[(StreamExecutionEnvironment, Int)],
      modelData: ModelData,
      scenario: CanonicalProcess,
      processVersion: ProcessVersion,
      savepointPath: String
  ): Unit =
    new FlinkVerificationMain(
      sharedMiniClusterServicesOpt.map(StreamExecutionEnvironmentWithParallelismOverride.apply _ tupled),
      modelData
    ).verifyScenarioState(
      scenario,
      processVersion,
      savepointPath
    )

}

class FlinkVerificationMain(
    sharedMiniClusterServicesOpt: Option[StreamExecutionEnvironmentWithParallelismOverride],
    modelData: ModelData,
) {

  private val adHocMiniClusterFallbackHandler =
    new LegacyAdHocMiniClusterFallbackHandler(modelData.modelClassLoader, "scenario state verification")

  def verifyScenarioState(scenario: CanonicalProcess, processVersion: ProcessVersion, savepointPath: String): Unit = {
    val collectingListener = ResultsCollectingListenerHolder.registerTestEngineListener
    try {
      adHocMiniClusterFallbackHandler.handleAdHocMniClusterFallback(
        sharedMiniClusterServicesOpt,
        scenario,
      ) { streamExecutionEnvWithMaxParallelism =>
        import streamExecutionEnvWithMaxParallelism._
        val scenarioWithOverrodeParallelism = streamExecutionEnvWithMaxParallelism.overrideParallelismIfNeeded(scenario)
        val resultCollector                 = new TestServiceInvocationCollector(collectingListener)
        val registrar                       = prepareRegistrar(scenarioWithOverrodeParallelism)
        val deploymentData                  = DeploymentData.empty

        streamExecutionEnv.getCheckpointConfig.disableCheckpointing()
        registrar.register(
          streamExecutionEnv,
          scenarioWithOverrodeParallelism,
          processVersion,
          deploymentData,
          resultCollector
        )
        val streamGraph = streamExecutionEnv.getStreamGraph
        streamGraph.setSavepointRestoreSettings(SavepointRestoreSettings.forPath(savepointPath, true))
        streamExecutionEnv.execute(streamGraph)
      }
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
