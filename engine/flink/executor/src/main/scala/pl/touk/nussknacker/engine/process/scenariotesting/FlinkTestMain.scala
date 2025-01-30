package pl.touk.nussknacker.engine.process.scenariotesting

import io.circe.Json
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, blocking}

object FlinkTestMain {

  // This method is invoked via reflection without shared API classes, so simple types should be used
  def run(
      modelData: ModelData,
      scenario: CanonicalProcess,
      scenarioTestData: ScenarioTestData,
      streamExecutionEnv: StreamExecutionEnvironment,
  ): Future[TestResults[Json]] = {
    new FlinkTestMain(modelData).testScenario(scenario, scenarioTestData, streamExecutionEnv)
  }

}

class FlinkTestMain(modelData: ModelData) {

  def testScenario(
      scenario: CanonicalProcess,
      scenarioTestData: ScenarioTestData,
      streamExecutionEnv: StreamExecutionEnvironment,
  ): Future[TestResults[Json]] = {
    val collectingListener = ResultsCollectingListenerHolder.registerTestEngineListener
    val resultCollector    = new TestServiceInvocationCollector(collectingListener)
    // ProcessVersion can't be passed from DM because testing mechanism can be used with not saved scenario
    val processVersion = ProcessVersion.empty.copy(processName = scenario.name)
    val deploymentData = DeploymentData.empty.copy(additionalModelConfigs =
      AdditionalModelConfigs(modelData.additionalConfigsFromProvider)
    )
    val registrar = prepareRegistrar(collectingListener, scenario, scenarioTestData, processVersion)

    registrar.register(
      streamExecutionEnv,
      scenario,
      processVersion,
      deploymentData,
      resultCollector
    )
    streamExecutionEnv.getCheckpointConfig.disableCheckpointing()

    // TODO: Non-blocking future periodically checking if job is finished
    val resultFuture = Future {
      blocking {
        streamExecutionEnv.execute(scenario.name.value)
        collectingListener.results
      }
    }
    resultFuture.onComplete { _ =>
      collectingListener.clean()
    }
    resultFuture
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
