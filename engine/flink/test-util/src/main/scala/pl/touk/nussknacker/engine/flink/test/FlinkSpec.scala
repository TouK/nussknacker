package pl.touk.nussknacker.engine.flink.test

import org.apache.flink.configuration.Configuration
import org.scalatest.{BeforeAndAfterAll, Suite}
import pl.touk.nussknacker.engine.flink.test.FlinkMiniClusterHolder.AdditionalEnvironmentConfig

trait FlinkSpec extends BeforeAndAfterAll { self: Suite =>

  var flinkMiniCluster: FlinkMiniClusterHolder = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    flinkMiniCluster = createFlinkMiniClusterHolder()
    flinkMiniCluster.start()
  }

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

}
