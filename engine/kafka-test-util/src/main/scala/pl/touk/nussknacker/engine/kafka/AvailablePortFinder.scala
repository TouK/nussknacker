package pl.touk.nussknacker.engine.kafka

import java.net.ServerSocket

object AvailablePortFinder {

  def findAvailablePorts(n: Int): List[Int] = {
    val sockets = (0 until n).map { _ =>
      val socket = new ServerSocket(0)

      socket.setReuseAddress(true)
      (socket.getLocalPort, socket)
    }

    try {
      sockets.map(_._1).toList
    } finally {
      sockets.foreach(_._2.close())
    }
  }

  def withAvailablePortsBlocked(n: Int)(listen: List[Int] => Unit): Unit = {
    synchronized {
      listen(findAvailablePorts(n))
    }
  }

}
