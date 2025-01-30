package pl.touk.nussknacker.engine.flink.minicluster

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.configuration._
import org.apache.flink.core.fs.FileSystem
import org.apache.flink.runtime.minicluster.{MiniCluster, MiniClusterConfiguration}
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader

import java.net.URLClassLoader

object FlinkMiniClusterFactory extends LazyLogging {

  private[nussknacker] val DefaultTaskSlots = 8

  // It is method instead of value because Configuration is mutable
  private def DefaultMiniClusterConfig: Configuration = {
    val config = new Configuration
    // To avoid ports collisions
    config.set[Integer](JobManagerOptions.PORT, 0)
    config.set[Integer](RestOptions.PORT, 0)
    // Without this we have problems such SampleComponentProvider cannot be cast to class ComponentProvider
    // TODO: find the reason why it occurs, and either resolve it or write a comment describing the issue
    config.set(CoreOptions.CLASSLOADER_RESOLVE_ORDER, "parent-first")
    // In some setups we create a few Flink DMs. Each of them creates its own mini cluster.
    // To reduce footprint we decrease off-heap memory buffers size and managed memory
    config.set(TaskManagerOptions.NETWORK_MEMORY_MIN, MemorySize.parse("16m"))
    config.set(TaskManagerOptions.NETWORK_MEMORY_MAX, MemorySize.parse("16m"))
    config.set(TaskManagerOptions.MANAGED_MEMORY_SIZE, MemorySize.parse("100m"))
    // Reasonable number of available parallel slots
    config.set[Integer](TaskManagerOptions.NUM_TASK_SLOTS, DefaultTaskSlots)
    config
  }

  def createMiniClusterWithServicesIfConfigured(
      modelClassLoader: URLClassLoader,
      config: FlinkMiniClusterConfig
  ): Option[FlinkMiniClusterWithServices] = {
    if (config.useForScenarioTesting || config.useForScenarioStateVerification) {
      Some(createMiniClusterWithServices(modelClassLoader, config.config, config.streamExecutionEnvConfig))
    } else {
      None
    }
  }

  def createUnitTestsMiniClusterWithServices(
      miniClusterConfigOverrides: Configuration = new Configuration,
      streamExecutionConfigOverrides: Configuration = new Configuration
  ): FlinkMiniClusterWithServices = {
    createMiniClusterWithServices(
      ModelClassLoader.flinkWorkAroundEmptyClassloader,
      miniClusterConfigOverrides,
      streamExecutionConfigOverrides
    )
  }

  def createMiniClusterWithServices(
      modelClassLoader: URLClassLoader,
      miniClusterConfigOverrides: Configuration,
      streamExecutionConfigOverrides: Configuration
  ): FlinkMiniClusterWithServices = {
    val miniClusterConfig = DefaultMiniClusterConfig
    miniClusterConfig.addAll(miniClusterConfigOverrides)

    logger.debug(s"Creating MiniCluster with configuration: $miniClusterConfig")
    val miniCluster = createMiniCluster(miniClusterConfig)
    // We have to setup classloader that contains flink-runtime as a context classloader,
    // because otherwise sometimes MiniCluster couldn't load any RpcSystemLoader
    ThreadUtils.withThisAsContextClassLoader(getClass.getClassLoader) {
      miniCluster.start()
    }

    def createStreamExecutionEnv(attached: Boolean): StreamExecutionEnvironment = {
      FlinkMiniClusterStreamExecutionEnvironmentFactory.createStreamExecutionEnvironment(
        miniCluster,
        modelClassLoader,
        streamExecutionConfigOverrides,
        attached
      )
    }

    new FlinkMiniClusterWithServices(miniCluster, createStreamExecutionEnv)
  }

  private def createMiniCluster(configuration: Configuration) = {
    // it is required for proper working of HadoopFileSystem
    FileSystem.initialize(configuration, null)
    new MiniCluster(
      new MiniClusterConfiguration.Builder()
        .setNumTaskManagers(configuration.get(TaskManagerOptions.MINI_CLUSTER_NUM_TASK_MANAGERS))
        .setNumSlotsPerTaskManager(configuration.get(TaskManagerOptions.NUM_TASK_SLOTS))
        .setConfiguration(configuration)
        .build()
    )
  }

}

class FlinkMiniClusterWithServices(
    val miniCluster: MiniCluster,
    streamExecutionEnvironmentFactory: Boolean => StreamExecutionEnvironment
) extends AutoCloseable {

  def createStreamExecutionEnvironment(attached: Boolean): StreamExecutionEnvironment =
    streamExecutionEnvironmentFactory(attached)

  override def close(): Unit = miniCluster.close()

}
