package pl.touk.nussknacker.engine.flink.test

import org.apache.flink.configuration.Configuration
import org.scalatest.{BeforeAndAfterAll, Suite}
import pl.touk.nussknacker.engine.flink.test.FlinkMiniClusterHolder.AdditionalEnvironmentConfig

trait FlinkSpec extends BeforeAndAfterAll { self: Suite =>

  var flinkMiniCluster: FlinkMiniClusterHolder = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    flinkMiniCluster = FlinkMiniClusterHolder(prepareFlinkConfiguration(), prepareEnvConfig())
    flinkMiniCluster.start()
  }

  protected def prepareFlinkConfiguration(): Configuration = {
    FlinkTestConfiguration.configuration()
  }

  protected def prepareEnvConfig(): AdditionalEnvironmentConfig = {
    AdditionalEnvironmentConfig()
  }

  override protected def afterAll(): Unit = {
    try {
      flinkMiniCluster.stop()
    } finally {
      super.afterAll()
    }
  }

}
