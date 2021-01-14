package pl.touk.nussknacker.engine.management.periodic

import slick.lifted.MappedTo

case class PeriodicProcessId(value: Long) extends AnyVal with MappedTo[Long]

case class PeriodicProcessDeploymentId(value: Long) extends AnyVal with MappedTo[Long]

object PeriodicProcessDeploymentStatus extends Enumeration {
  type PeriodicProcessDeploymentStatus = Value

  val Scheduled, Deployed, Finished, Failed = Value
}
