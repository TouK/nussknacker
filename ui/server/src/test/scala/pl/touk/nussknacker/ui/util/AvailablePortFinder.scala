package pl.touk.nussknacker.ui.util

import pl.touk.nussknacker.engine.util.exception.WithResources

import java.net.ServerSocket

object AvailablePortFinder {

  def findAvailablePort(): Int = WithResources.use(new ServerSocket(0)) { socket =>
    socket.setReuseAddress(true)
    socket.getLocalPort
  }
}
