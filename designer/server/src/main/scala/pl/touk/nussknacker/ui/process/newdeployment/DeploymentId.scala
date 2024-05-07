package pl.touk.nussknacker.ui.process.newdeployment

import sttp.tapir.Codec
import sttp.tapir.Codec.PlainCodec

import java.util.UUID

final case class DeploymentId(value: UUID) {
  override def toString: String = value.toString
}

object DeploymentId {

  def generate: DeploymentId = DeploymentId(UUID.randomUUID())

  implicit val deploymentIdCodec: PlainCodec[DeploymentId] =
    Codec.uuid.map(DeploymentId(_))(_.value)

}
