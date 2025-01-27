package pl.touk.nussknacker.engine.process.scenariotesting

import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment

object ScenarioTestingStreamExecutionEnvironmentFactory {

  def createStreamExecutionEnvironment(parallelism: Int, configuration: Configuration): StreamExecutionEnvironment = {
    val env = StreamExecutionEnvironment.createLocalEnvironment(
      parallelism,
      configuration
    )
    // Checkpoints are disabled to prevent waiting for checkpoint to happen
    // before finishing execution.
    env.getCheckpointConfig.disableCheckpointing()
    env
  }

}
