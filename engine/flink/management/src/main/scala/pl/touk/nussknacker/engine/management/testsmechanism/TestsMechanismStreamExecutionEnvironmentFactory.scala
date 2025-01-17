package pl.touk.nussknacker.engine.management.testsmechanism

import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment

object TestsMechanismStreamExecutionEnvironmentFactory {

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
