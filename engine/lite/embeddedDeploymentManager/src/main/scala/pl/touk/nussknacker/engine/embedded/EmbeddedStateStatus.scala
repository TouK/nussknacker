package pl.touk.nussknacker.engine.embedded

import pl.touk.nussknacker.engine.api.deployment._

object EmbeddedStateStatus  {
  def failed(ex: Throwable): StateStatus = DetailedFailedStateStatus(ex.getMessage)

  case class DetailedFailedStateStatus(message: String) extends CustomStateStatus("Failed") {
    override def isFailed: Boolean = true
  }
}
