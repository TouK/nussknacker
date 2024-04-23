package pl.touk.nussknacker.ui.process.newdeployment

import sttp.tapir.Codec
import sttp.tapir.Codec.PlainCodec

import java.util.UUID

// TODO: we should replace DeploymentId by this class after we split Deployments and Activities
final case class NewDeploymentId(value: UUID) {
  override def toString: String = value.toString
}

object NewDeploymentId {

  def generate: NewDeploymentId = NewDeploymentId(UUID.randomUUID())

  implicit val deploymentIdCodec: PlainCodec[NewDeploymentId] =
    Codec.uuid.map(NewDeploymentId(_))(_.value)

}
