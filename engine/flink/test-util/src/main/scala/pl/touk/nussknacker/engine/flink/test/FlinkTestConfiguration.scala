package pl.touk.nussknacker.engine.flink.test

import org.apache.flink.configuration._

object FlinkTestConfiguration {
//FIXME: make ports range dynamic and smaller.
  private val QueryStateServerPortLow = 9167
  private val QueryStateServerPortHigh = 9267

  private val QueryStateProxyPortLow = 9369
  private val QueryStateProxyPortHigh = 9469

  // better to create each time because is mutable
  def configuration(taskManagersCount: Int = 2, taskSlotsCount: Int = 8): Configuration = {
    val config = new Configuration
    config.setInteger(ConfigConstants.LOCAL_NUMBER_TASK_MANAGER, taskManagersCount)
    config.setInteger(TaskManagerOptions.NUM_TASK_SLOTS, taskSlotsCount)
    // to prevent OutOfMemoryError: Could not allocate enough memory segments for NetworkBufferPool on low memory env (like Travis)
    config.setString(NettyShuffleEnvironmentOptions.NETWORK_BUFFERS_MEMORY_MIN, "16m")
    config.setString(NettyShuffleEnvironmentOptions.NETWORK_BUFFERS_MEMORY_MAX, "16m")
    addQueryableStatePortRanges(config)
  }

  def setQueryableStatePortRangesBySystemProperties(): Unit = {
    System.setProperty(QueryableStateOptions.SERVER_PORT_RANGE.key(), s"$QueryStateServerPortLow-$QueryStateServerPortHigh")
    System.setProperty(QueryableStateOptions.PROXY_PORT_RANGE.key(), s"$QueryStateProxyPortLow-$QueryStateProxyPortHigh")
  }

  // ranges for parallel tests
  def addQueryableStatePortRanges(config: Configuration): Configuration = {
    config.setString(QueryableStateOptions.SERVER_PORT_RANGE, s"$QueryStateServerPortLow-$QueryStateServerPortHigh")
    config.setString(QueryableStateOptions.PROXY_PORT_RANGE, s"$QueryStateProxyPortLow-$QueryStateProxyPortHigh")
    config
  }

}
