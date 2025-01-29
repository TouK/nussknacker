package pl.touk.nussknacker.engine.flink.test

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.apache.flink.configuration.Configuration
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, Suite}
import pl.touk.nussknacker.engine.flink.test.FlinkMiniClusterHolder.AdditionalEnvironmentConfig
import pl.touk.nussknacker.test.WithConfig

import java.util.UUID

trait FlinkSpec extends BeforeAndAfterAll with BeforeAndAfter with WithConfig { self: Suite =>

  /**
    * Used to check consumed errors: RecordingExceptionConsumer.dataFor(runId)
   */
  protected val runId: String = UUID.randomUUID().toString

  var flinkMiniCluster: FlinkMiniClusterHolder = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    flinkMiniCluster = FlinkMiniClusterHolder(prepareFlinkConfiguration(), prepareEnvConfig())
  }

  override protected def resolveConfig(config: Config): Config =
    RecordingExceptionConsumerProvider
      .configWithProvider(super.resolveConfig(config), runId)
      .withValue(
        "checkpointConfig.checkpointInterval",
        fromAnyRef("1s")
      ) // avoid long waits for closing on test Flink minicluster, it's needed for proper testing

  /**
    * Override this when you use own Configuration implementation
    */
  protected def prepareFlinkConfiguration(): Configuration = {
    new Configuration
  }

  protected def prepareEnvConfig(): AdditionalEnvironmentConfig = {
    AdditionalEnvironmentConfig()
  }

  override protected def afterAll(): Unit = {
    try {
      flinkMiniCluster.close()
    } finally {
      super.afterAll()
    }
  }

  after {
    RecordingExceptionConsumer.clearRecordedExceptions(runId)
  }

}
