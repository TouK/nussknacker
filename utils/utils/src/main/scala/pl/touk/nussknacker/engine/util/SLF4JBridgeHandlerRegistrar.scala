package pl.touk.nussknacker.engine.util

import org.slf4j.bridge.SLF4JBridgeHandler

object SLF4JBridgeHandlerRegistrar {

  // For some reasons method via logging.properties doesn't work so we do it programmatically as described here:
  // https://www.slf4j.org/api/org/slf4j/bridge/SLF4JBridgeHandler.html
  def register(): Unit = {
    SLF4JBridgeHandler.removeHandlersForRootLogger()
    SLF4JBridgeHandler.install()
  }

}
