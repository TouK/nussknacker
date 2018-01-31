package pl.touk.nussknacker.engine.flink.test

import org.apache.flink.configuration.{Configuration, QueryableStateOptions}

object FlinkTestConfiguration {
//FIXME: make ports range dynamic and smaller.
  private val QueryStateServerPortLow = 9167
  private val QueryStateServerPortHigh = 9267

  private val QueryStateProxyPortLow = 9369
  private val QueryStateProxyPortHigh = 9469

  // better to create each time because is mutable
  def configuration: Configuration = addQueryableStatePortRanges(new Configuration)

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
