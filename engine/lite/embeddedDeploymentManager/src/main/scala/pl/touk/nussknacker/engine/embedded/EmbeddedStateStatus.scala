package pl.touk.nussknacker.engine.embedded

import pl.touk.nussknacker.engine.api.deployment._

import java.net.URI

object EmbeddedStateStatus  {
  def failed(ex: Throwable): StateStatus = DetailedFailedStateStatus(ex.getMessage)

  case class DetailedFailedStateStatus(message: String) extends CustomStateStatus("Failed") {
    override def isFailed: Boolean = true
  }

  val customStateDefinitions: Set[StateDefinition] = Set(
    StateDefinition(
      name = "Failed",
      displayableName = "Problems detected",
      icon = Some(URI.create("/assets/states/failed.svg")),
      tooltip = Some("Problems detected"),
      description = Some("There are some problems with scenario.")
    )
  )
}
