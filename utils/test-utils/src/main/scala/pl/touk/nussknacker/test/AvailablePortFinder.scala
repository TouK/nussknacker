package pl.touk.nussknacker.test

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

  def withAvailablePortsBlocked[T](n: Int)(listen: List[Int] => T): T = {
    synchronized {
      listen(findAvailablePorts(n))
    }
  }

}
