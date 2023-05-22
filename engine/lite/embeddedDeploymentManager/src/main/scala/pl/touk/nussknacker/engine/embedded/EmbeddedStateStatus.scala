package pl.touk.nussknacker.engine.embedded

import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus

object EmbeddedStateStatus  {
  def failed(ex: Throwable): StateStatus = DetailedFailedStateStatus(ex.getMessage)

  case class DetailedFailedStateStatus(message: String) extends StateStatus {
    override def name: StatusName = ProblemStateStatus.name
    override def isFailed: Boolean = true
  }
}
