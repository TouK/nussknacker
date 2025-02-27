package pl.touk.nussknacker.test

import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

trait UniquePortProvider {
  def nextPort(): Int
}

trait DefaultUniquePortProvider extends UniquePortProvider {
  override def nextPort(): Int = DefaultUniquePortProvider.nextPort()
}

// TODO: reuse AvailablePortFinder
object DefaultUniquePortProvider extends UniquePortProvider {
  private val startingPort: Int = 20000

  private val port: AtomicInteger = new AtomicInteger(startingPort)

  def nextPort(): Int = {
    RetryingUniquePortProvider.nextFreePort(() => port.incrementAndGet())
  }

}

private object RetryingUniquePortProvider {

  private val defaultMaxAttempts = 20

  @tailrec
  def nextFreePort(generateNewPort: () => Int, maxAttempts: Int = defaultMaxAttempts): Int = {
    val port = generateNewPort()

    Try(new ServerSocket(port).close()) match {
      case Success(_) =>
        port
      case Failure(_) if maxAttempts <= 0 =>
        throw new Exception("Could not find free port, giving up")
      case Failure(_) =>
        nextFreePort(generateNewPort, maxAttempts - 1)
    }
  }

}
