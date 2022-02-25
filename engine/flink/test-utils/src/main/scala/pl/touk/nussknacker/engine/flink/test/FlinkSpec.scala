package pl.touk.nussknacker.engine.flink.test

import com.typesafe.config.Config
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
    flinkMiniCluster = createFlinkMiniClusterHolder()
    flinkMiniCluster.start()
  }

  override protected def resolveConfig(config: Config): Config =
    RecordingExceptionConsumerProvider.configWithProvider(super.resolveConfig(config), runId)

  /**
    * Override this when you use own Configuration implementation (e.g. Flink 1.9)
    */
  protected def prepareFlinkConfiguration(): Configuration = {
    FlinkTestConfiguration.configuration()
  }

  protected def prepareEnvConfig(): AdditionalEnvironmentConfig = {
    AdditionalEnvironmentConfig()
  }

  /**
    * Override this when you use own FlikMiniClusterHolder implementation (e.g. Flink 1.9)
    */
  protected def createFlinkMiniClusterHolder(): FlinkMiniClusterHolder = {
    FlinkMiniClusterHolder(prepareFlinkConfiguration(), prepareEnvConfig())
  }

  override protected def afterAll(): Unit = {
    try {
      flinkMiniCluster.stop()
    } finally {
      super.afterAll()
    }
  }

  after {
    RecordingExceptionConsumer.clearData(runId)
  }
}
