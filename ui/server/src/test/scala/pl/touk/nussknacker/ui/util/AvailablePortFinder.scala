package pl.touk.nussknacker.ui.util

import java.net.ServerSocket
import scala.util.Using

object AvailablePortFinder {

  def findAvailablePort(): Int = Using.resource(new ServerSocket(0)) { socket =>
    socket.setReuseAddress(true)
    socket.getLocalPort
  }
}
