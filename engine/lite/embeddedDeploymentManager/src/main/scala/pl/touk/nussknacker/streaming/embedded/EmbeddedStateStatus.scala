package pl.touk.nussknacker.streaming.embedded

import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus

object EmbeddedStateStatus  {
  val Restarting: StateStatus = NotEstablishedStateStatus("Restarting")
}
