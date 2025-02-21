package pl.touk.nussknacker.engine.process.scenariotesting

import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import org.apache.flink.api.common.JobExecutionResult
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.api.{JobData, ProcessVersion}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{AdditionalModelConfigs, DeploymentData}
import pl.touk.nussknacker.engine.process.compiler.TestFlinkProcessCompilerDataFactory
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.process.{ExecutionConfigPreparer, FlinkJobConfig}
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListener, TestServiceInvocationCollector}

object FlinkScenarioTestingJob {

  // This method is invoked via reflection without shared API classes, so simple types should be used
  def run(
      modelData: ModelData,
      scenario: CanonicalProcess,
      scenarioTestData: ScenarioTestData,
      collectingListener: ResultsCollectingListener[Json],
      streamExecutionEnv: StreamExecutionEnvironment,
  ): JobExecutionResult = {
    new FlinkScenarioTestingJob(modelData).run(
      scenario,
      scenarioTestData,
      collectingListener.asInstanceOf[ResultsCollectingListener[Json]],
      streamExecutionEnv
    )
  }

}

private class FlinkScenarioTestingJob(modelData: ModelData) extends LazyLogging {

  def run(
      scenario: CanonicalProcess,
      scenarioTestData: ScenarioTestData,
      collectingListener: ResultsCollectingListener[Json],
      streamExecutionEnv: StreamExecutionEnvironment,
  ): JobExecutionResult = {
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
      new TestServiceInvocationCollector(collectingListener)
    )
    streamExecutionEnv.getCheckpointConfig.disableCheckpointing()

    streamExecutionEnv.execute(scenario.name.value)
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
