package pl.touk.nussknacker.engine.customs.deployment

import pl.touk.nussknacker.engine.api.deployment.{StateStatusEnumeration, StatusState}

object CustomStateStatus extends StateStatusEnumeration {
  import StatusState._

  val Unknown = new StateStatus("UNKNOWN")
  val NotDeployed = new StateStatus("NOT_DEPLOYED")
  val Restarting = new StateStatus("RESTARTING")
  val Failed = new StateStatus("FAILED")
  val DuringCancel = new StateStatus("DURING_CANCEL")
  val Canceled = new StateStatus("CANCELED")
}
