package pl.touk.nussknacker.engine.process.runner

import io.circe.Json
import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.{JobData, ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
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
import pl.touk.nussknacker.engine.util.MetaDataExtractor

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
    new FlinkTestMain(
      modelData,
      process,
      scenarioTestData,
      processVersion,
      DeploymentData.empty.copy(additionalModelConfigs =
        AdditionalModelConfigs(modelData.additionalConfigsFromProvider)
      ),
      configuration
    ).runTest
  }

}

class FlinkTestMain(
    val modelData: ModelData,
    val process: CanonicalProcess,
    scenarioTestData: ScenarioTestData,
    processVersion: ProcessVersion,
    deploymentData: DeploymentData,
    val configuration: Configuration
) {

  private val stubbedRunner = new FlinkStubbedRunner(modelData.modelClassLoader, configuration)

  def runTest: TestResults[Json] = {
    val collectingListener = ResultsCollectingListenerHolder.registerTestEngineListener
    try {
      val resultCollector = new TestServiceInvocationCollector(collectingListener)
      val registrar       = prepareRegistrar(collectingListener, scenarioTestData)
      val parallelism = MetaDataExtractor
        .extractTypeSpecificDataOrDefault[StreamMetaData](process.metaData, StreamMetaData())
        .parallelism
        .getOrElse(1)
      val env = stubbedRunner.createEnv(parallelism)

      registrar.register(env, process, processVersion, deploymentData, resultCollector)
      stubbedRunner.execute(env, parallelism, process.name, SavepointRestoreSettings.none())
      collectingListener.results
    } finally {
      collectingListener.clean()
    }
  }

  protected def prepareRegistrar(
      collectingListener: ResultsCollectingListener[Json],
      scenarioTestData: ScenarioTestData
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
