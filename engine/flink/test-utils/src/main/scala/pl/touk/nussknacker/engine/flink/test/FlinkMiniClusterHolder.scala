package pl.touk.nussknacker.engine.flink.test

import org.apache.flink.configuration._
import org.apache.flink.runtime.minicluster.MiniCluster
import org.scalatest.concurrent.Eventually.{scaled, _}
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.nussknacker.engine.flink.minicluster.{FlinkMiniClusterFactory, FlinkMiniClusterWithServices}
import pl.touk.nussknacker.engine.flink.test.FlinkMiniClusterHolder._
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader

import scala.util.Using

class FlinkMiniClusterHolder(
    miniClusterWithServices: FlinkMiniClusterWithServices,
    protected val envConfig: AdditionalEnvironmentConfig
) extends AutoCloseable {

  def miniCluster: MiniCluster = miniClusterWithServices.miniCluster

  override def close(): Unit = {
    miniClusterWithServices.close()
  }

  def withExecutionEnvironment[T](action: MiniClusterExecutionEnvironment => T): T = {
    Using.resource(createExecutionEnvironment)(action)
  }

  private def createExecutionEnvironment: MiniClusterExecutionEnvironment = {
    new MiniClusterExecutionEnvironment(
      miniCluster = miniClusterWithServices.miniCluster,
      envConfig = envConfig,
      env = miniClusterWithServices.createStreamExecutionEnvironment(attached = false)
    )
  }

}

object FlinkMiniClusterHolder {

  def apply(
      userFlinkClusterConfig: Configuration,
      envConfig: AdditionalEnvironmentConfig = AdditionalEnvironmentConfig()
  ): FlinkMiniClusterHolder = {
    val miniclusterWithServices = FlinkMiniClusterFactory.createMiniClusterWithServices(
      ModelClassLoader.flinkWorkAroundEmptyClassloader,
      userFlinkClusterConfig,
      new Configuration()
    )
    new FlinkMiniClusterHolder(miniclusterWithServices, envConfig)
  }

  case class AdditionalEnvironmentConfig(
      // On the CI, 10 seconds is sometimes too low
      defaultWaitForStatePatience: PatienceConfig =
        PatienceConfig(timeout = scaled(Span(20, Seconds)), interval = scaled(Span(10, Millis)))
  )

}
