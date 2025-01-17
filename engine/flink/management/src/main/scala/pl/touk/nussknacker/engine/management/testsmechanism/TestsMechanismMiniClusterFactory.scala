package pl.touk.nussknacker.engine.management.testsmechanism

import org.apache.flink.configuration.{Configuration, CoreOptions, RestOptions, TaskManagerOptions}
import org.apache.flink.core.fs.FileSystem
import org.apache.flink.runtime.minicluster.{MiniCluster, MiniClusterConfiguration}

object TestsMechanismMiniClusterFactory {

  def createConfiguredMiniCluster(parallelism: Int): MiniCluster = {
    val miniClusterConfiguration = prepareMiniClusterConfiguration(numTaskSlots = parallelism)

    // it is required for proper working of HadoopFileSystem
    FileSystem.initialize(miniClusterConfiguration, null)

    createMiniCluster(miniClusterConfiguration, numSlotsPerTaskManager = parallelism)
  }

  private def prepareMiniClusterConfiguration(numTaskSlots: Int) = {
    val configuration: Configuration = new Configuration
    configuration.set[Integer](TaskManagerOptions.NUM_TASK_SLOTS, numTaskSlots)
    configuration.set[Integer](RestOptions.PORT, 0)

    // FIXME: reversing flink default order
    configuration.set(CoreOptions.CLASSLOADER_RESOLVE_ORDER, "parent-first")
    configuration
  }

  private def createMiniCluster(configuration: Configuration, numSlotsPerTaskManager: Int) = {
    val miniCluster = new MiniCluster(
      new MiniClusterConfiguration.Builder()
        .setNumSlotsPerTaskManager(numSlotsPerTaskManager)
        .setConfiguration(configuration)
        .build()
    )
    miniCluster.start()
    miniCluster
  }

}
