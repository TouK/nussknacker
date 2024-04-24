package pl.touk.nussknacker.ui.process.newdeployment

import sttp.tapir.Codec
import sttp.tapir.Codec.PlainCodec

import java.util.UUID

// TODO: we should replace DeploymentId by this class after we split Deployments and Activities
final case class DeploymentIdNG(value: UUID) {
  override def toString: String = value.toString
}

object DeploymentIdNG {

  def generate: DeploymentIdNG = DeploymentIdNG(UUID.randomUUID())

  implicit val deploymentIdCodec: PlainCodec[DeploymentIdNG] =
    Codec.uuid.map(DeploymentIdNG(_))(_.value)

}
