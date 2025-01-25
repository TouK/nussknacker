package pl.touk.nussknacker.engine.process.scenariotesting

import org.apache.flink.configuration.{Configuration, CoreOptions, RestOptions, TaskManagerOptions}
import org.apache.flink.core.fs.FileSystem
import org.apache.flink.runtime.minicluster.{MiniCluster, MiniClusterConfiguration}

object ScenarioTestingMiniClusterFactory {

  def createConfiguredMiniCluster(numTaskSlots: Int, miniClusterConfig: Configuration): MiniCluster = {
    adjustConfiguration(miniClusterConfig, numTaskSlots = numTaskSlots)

    // it is required for proper working of HadoopFileSystem
    FileSystem.initialize(miniClusterConfig, null)

    createMiniCluster(miniClusterConfig, numSlotsPerTaskManager = numTaskSlots)
  }

  private def adjustConfiguration(configuration: Configuration, numTaskSlots: Int): Unit = {
    configuration.set[Integer](TaskManagerOptions.NUM_TASK_SLOTS, numTaskSlots)
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
