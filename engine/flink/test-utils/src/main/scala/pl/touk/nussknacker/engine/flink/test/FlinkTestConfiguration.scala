package pl.touk.nussknacker.engine.flink.test

import org.apache.flink.configuration._

object FlinkTestConfiguration {

  // better to create each time because is mutable
  def configuration(taskManagersCount: Int = 2, taskSlotsCount: Int = 8): Configuration = {
    val config = new Configuration
    config.setInteger(ConfigConstants.LOCAL_NUMBER_TASK_MANAGER, taskManagersCount)
    config.setInteger(TaskManagerOptions.NUM_TASK_SLOTS, taskSlotsCount)
    // to prevent OutOfMemoryError: Could not allocate enough memory segments for NetworkBufferPool on low memory env (like Travis)
    config.set(TaskManagerOptions.NETWORK_MEMORY_MIN, MemorySize.parse("16m"))
    config.set(TaskManagerOptions.NETWORK_MEMORY_MAX, MemorySize.parse("16m"))
  }

}
