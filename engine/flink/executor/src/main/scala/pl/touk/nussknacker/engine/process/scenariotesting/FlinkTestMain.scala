package pl.touk.nussknacker.engine.process.scenariotesting

import io.circe.Json
import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.api.{JobData, ProcessVersion}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{AdditionalModelConfigs, DeploymentData}
import pl.touk.nussknacker.engine.process.compiler.TestFlinkProcessCompilerDataFactory
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.process.{ExecutionConfigPreparer, FlinkJobConfig}
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.engine.testmode.{
  ResultsCollectingListener,
  ResultsCollectingListenerHolder,
  TestServiceInvocationCollector
}

object FlinkTestMain {

  def run(
      miniClusterWrapperOpt: Option[ScenarioTestingMiniClusterWrapper],
      modelData: ModelData,
      scenario: CanonicalProcess,
      scenarioTestData: ScenarioTestData
  ): TestResults[Json] = {
    new FlinkTestMain(miniClusterWrapperOpt, modelData).testScenario(scenario, scenarioTestData)
  }

}

class FlinkTestMain(miniClusterWrapperOpt: Option[ScenarioTestingMiniClusterWrapper], modelData: ModelData) {

  def testScenario(scenario: CanonicalProcess, scenarioTestData: ScenarioTestData): TestResults[Json] = {
    val collectingListener = ResultsCollectingListenerHolder.registerTestEngineListener
    try {
      AdHocMiniClusterFallbackHandler.handleAdHocMniClusterFallback(miniClusterWrapperOpt, scenario) {
        miniClusterWrapper =>
          val alignedScenario = miniClusterWrapper.alignParallelism(scenario)
          val resultCollector = new TestServiceInvocationCollector(collectingListener)
          // ProcessVersion can't be passed from DM because testing mechanism can be used with not saved scenario
          val processVersion = ProcessVersion.empty.copy(processName = alignedScenario.name)
          val deploymentData = DeploymentData.empty.copy(additionalModelConfigs =
            AdditionalModelConfigs(modelData.additionalConfigsFromProvider)
          )
          val registrar = prepareRegistrar(collectingListener, alignedScenario, scenarioTestData, processVersion)
          registrar.register(miniClusterWrapper.env, alignedScenario, processVersion, deploymentData, resultCollector)
          miniClusterWrapper.submitJobAndCleanEnv(
            alignedScenario.name,
            SavepointRestoreSettings.none(),
            modelData.modelClassLoader
          )
          collectingListener.results
      }
    } finally {
      collectingListener.clean()
    }
  }

  protected def prepareRegistrar(
      collectingListener: ResultsCollectingListener[Json],
      process: CanonicalProcess,
      scenarioTestData: ScenarioTestData,
      processVersion: ProcessVersion,
  ): FlinkProcessRegistrar = {
    FlinkProcessRegistrar(
      TestFlinkProcessCompilerDataFactory(
        process,
        scenarioTestData,
        modelData,
        JobData(process.metaData, processVersion),
        collectingListener
      ),
      FlinkJobConfig.parse(modelData.modelConfig).copy(rocksDB = None),
      ExecutionConfigPreparer.defaultChain(modelData)
    )
  }

}
