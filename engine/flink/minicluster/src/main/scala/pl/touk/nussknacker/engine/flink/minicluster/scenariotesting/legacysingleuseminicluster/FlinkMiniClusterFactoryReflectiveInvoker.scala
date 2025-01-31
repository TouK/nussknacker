package pl.touk.nussknacker.engine.flink.minicluster.scenariotesting.legacysingleuseminicluster

import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.minicluster.MiniCluster
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.util.ReflectiveMethodInvoker

import java.net.URLClassLoader

object FlinkMiniClusterFactoryReflectiveInvoker {

  private val factoryInvoker = new ReflectiveMethodInvoker[(MiniCluster, Boolean => StreamExecutionEnvironment)](
    getClass.getClassLoader,
    "pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterFactory",
    "createMiniClusterWithServicesRaw"
  )

  def createMiniClusterWithServicesRaw(
      modelClassLoader: URLClassLoader,
      miniClusterConfigOverrides: Configuration,
      streamExecutionConfigOverrides: Configuration
  ): (MiniCluster, Boolean => StreamExecutionEnvironment) =
    factoryInvoker.invokeStaticMethod(modelClassLoader, miniClusterConfigOverrides, streamExecutionConfigOverrides)

}
