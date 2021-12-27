package pl.touk.nussknacker.streaming.embedded

import pl.touk.nussknacker.engine.api.deployment._

object EmbeddedStateStatus  {
  val Restarting: StateStatus = NotEstablishedStateStatus("Restarting")
  def failed(ex: Throwable): StateStatus = DetailedFailedStateStatus(ex.getMessage)

  case class DetailedFailedStateStatus(message: String) extends CustomStateStatus("Failed") {
    override def isFailed: Boolean = true
  }
}
