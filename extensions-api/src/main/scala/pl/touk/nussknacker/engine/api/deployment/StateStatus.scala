package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName

object StateStatus {
  type StatusName = String

  // Temporary methods to simplify status creation
  def apply(statusName: StatusName): StateStatus = NoAttributesStateStatus(statusName)

}

trait StateStatus {
  // Status identifier, should be unique among all states registered within all processing types.
  def name: StatusName
}

case class NoAttributesStateStatus(name: StatusName) extends StateStatus {
  override def toString: String = name
}
