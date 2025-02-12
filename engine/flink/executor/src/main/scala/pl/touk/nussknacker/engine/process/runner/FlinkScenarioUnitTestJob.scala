package pl.touk.nussknacker.engine.process.runner

import org.apache.flink.api.common.JobExecutionResult
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerDataFactory
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.process.{ExecutionConfigPreparer, FlinkJobConfig}

// This is a temporary solution for unit tests purpose. It is in production code, because we don't have flink-executor-test-utils
// module with dependency to flink-executor. We have flink-test-utils but there is a dependency from flink-executor to flink-test-utils.
// At the end we should rewrite all tests to TestScenarioRunner.flinkBased
class FlinkScenarioUnitTestJob(modelData: ModelData) {

  def run(
      scenario: CanonicalProcess,
      env: StreamExecutionEnvironment,
      deploymentData: DeploymentData = DeploymentData.empty
  ): JobExecutionResult = {
    registerInEnvironmentWithModel(scenario, env, deploymentData)
    env.execute(scenario.name.value)
  }

  def registerInEnvironmentWithModel(
      scenario: CanonicalProcess,
      env: StreamExecutionEnvironment,
      deploymentData: DeploymentData = DeploymentData.empty
  ): Unit = {
    val version = ProcessVersion.empty
    val registrar =
      FlinkProcessRegistrar(
        new FlinkProcessCompilerDataFactory(modelData),
        FlinkJobConfig.parse(modelData.modelConfig),
        ExecutionConfigPreparer.unOptimizedChain(modelData)
      )
    registrar.register(env, scenario, version, deploymentData)
  }

}
