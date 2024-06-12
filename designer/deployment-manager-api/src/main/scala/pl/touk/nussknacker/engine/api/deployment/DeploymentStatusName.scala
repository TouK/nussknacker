package pl.touk.nussknacker.engine.api.deployment

import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveUnwrappedCodec

sealed trait DeploymentStatus {
  def name: DeploymentStatusName
}

final case class NoAttributesDeploymentStatus(override val name: DeploymentStatusName) extends DeploymentStatus

final case class ProblemDeploymentStatus(description: String) extends DeploymentStatus {
  override def name: DeploymentStatusName = ProblemDeploymentStatus.name
}

object ProblemDeploymentStatus {
  def name: DeploymentStatusName = DeploymentStatusName("PROBLEM")

  def extractDescription(status: DeploymentStatus): Option[String] =
    status match {
      case ProblemDeploymentStatus(description) =>
        Some(description)
      case _ =>
        None
    }

}

final case class DeploymentStatusName(value: String) {
  override def toString: String = value
}

object DeploymentStatusName {

  implicit val codec: Codec[DeploymentStatusName] = deriveUnwrappedCodec[DeploymentStatusName]

}
