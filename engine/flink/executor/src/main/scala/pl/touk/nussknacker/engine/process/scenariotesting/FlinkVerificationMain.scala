package pl.touk.nussknacker.engine.process.scenariotesting

import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.process.compiler.VerificationFlinkProcessCompilerDataFactory
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.process.{ExecutionConfigPreparer, FlinkJobConfig}
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListenerHolder, TestServiceInvocationCollector}

object FlinkVerificationMain {

  def run(
      miniClusterWrapperOpt: Option[ScenarioTestingMiniClusterWrapper],
      modelData: ModelData,
      scenario: CanonicalProcess,
      processVersion: ProcessVersion,
      savepointPath: String
  ): Unit =
    new FlinkVerificationMain(miniClusterWrapperOpt, modelData).runTest(scenario, processVersion, savepointPath)

}

class FlinkVerificationMain(
    miniClusterWrapperOpt: Option[ScenarioTestingMiniClusterWrapper],
    modelData: ModelData,
) {

  def runTest(scenario: CanonicalProcess, processVersion: ProcessVersion, savepointPath: String): Unit = {
    val collectingListener = ResultsCollectingListenerHolder.registerTestEngineListener
    try {
      AdHocMiniClusterFallbackHandler.handleAdHocMniClusterFallback(miniClusterWrapperOpt, scenario) {
        miniClusterWrapper =>
          val alignedScenario = miniClusterWrapper.alignParallelism(scenario)
          val resultCollector = new TestServiceInvocationCollector(collectingListener)
          val registrar       = prepareRegistrar(alignedScenario)
          val deploymentData  = DeploymentData.empty

          registrar.register(miniClusterWrapper.env, alignedScenario, processVersion, deploymentData, resultCollector)
          miniClusterWrapper.submitJobAndCleanEnv(
            alignedScenario.name,
            SavepointRestoreSettings.forPath(savepointPath, true),
            modelData.modelClassLoader
          )
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
