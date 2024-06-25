package pl.touk.nussknacker.engine.newdeployment

import java.util.UUID
import scala.util.Try

final case class DeploymentId(value: UUID) {
  override def toString: String = value.toString
}

object DeploymentId {

  def fromString(str: String): Option[DeploymentId] = Try(UUID.fromString(str)).toOption.map(DeploymentId(_))

  def generate: DeploymentId = DeploymentId(UUID.randomUUID())

}
