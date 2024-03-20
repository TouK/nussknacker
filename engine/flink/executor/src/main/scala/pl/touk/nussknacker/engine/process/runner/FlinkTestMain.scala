package pl.touk.nussknacker.engine.process.runner

import io.circe.Json
import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.process.compiler.TestFlinkProcessCompilerDataFactory
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.process.{ExecutionConfigPreparer, FlinkJobConfig}
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.engine.testmode.{
  ResultsCollectingListener,
  ResultsCollectingListenerHolder,
  TestServiceInvocationCollector
}

import scala.util.Using

object FlinkTestMain extends FlinkRunner {

  def run(
      modelData: ModelData,
      process: CanonicalProcess,
      scenarioTestData: ScenarioTestData,
      configuration: Configuration,
  ): TestResults[Json] = {
    val processVersion = ProcessVersion.empty.copy(processName =
      ProcessName("snapshot version")
    ) // testing process may be unreleased, so it has no version
    new FlinkTestMain(modelData, process, scenarioTestData, processVersion, DeploymentData.empty, configuration).runTest
  }

}

class FlinkTestMain(
    val modelData: ModelData,
    val process: CanonicalProcess,
    scenarioTestData: ScenarioTestData,
    processVersion: ProcessVersion,
    deploymentData: DeploymentData,
    val configuration: Configuration
) extends FlinkStubbedRunner {

  def runTest: TestResults[Json] =
    Using.resource(ResultsCollectingListenerHolder.registerForTestEngineRunner) { collectingListener =>
      val resultCollector = new TestServiceInvocationCollector(collectingListener)
      val registrar       = prepareRegistrar(collectingListener, scenarioTestData)
      val env             = createEnv

      registrar.register(env, process, processVersion, deploymentData, resultCollector)
      execute(env, SavepointRestoreSettings.none())
      collectingListener.results
    }

  protected def prepareRegistrar(
      collectingListener: ResultsCollectingListener,
      scenarioTestData: ScenarioTestData
  ): FlinkProcessRegistrar = {
    FlinkProcessRegistrar(
      TestFlinkProcessCompilerDataFactory(
        process,
        scenarioTestData,
        modelData,
        collectingListener
      ),
      FlinkJobConfig.parse(modelData.modelConfig).copy(rocksDB = None),
      ExecutionConfigPreparer.defaultChain(modelData)
    )
  }

}
