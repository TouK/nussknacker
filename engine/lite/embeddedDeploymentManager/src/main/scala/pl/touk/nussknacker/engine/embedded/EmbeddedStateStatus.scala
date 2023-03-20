package pl.touk.nussknacker.engine.embedded

import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus

object EmbeddedStateStatus  {
  def failed(ex: Throwable): StateStatus = DetailedFailedStateStatus(ex.getMessage)

  case class DetailedFailedStateStatus(message: String) extends CustomStateStatus(SimpleStateStatus.Failed.name) {
    override def isFailed: Boolean = true
  }
}
