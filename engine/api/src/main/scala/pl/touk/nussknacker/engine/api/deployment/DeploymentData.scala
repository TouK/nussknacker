package pl.touk.nussknacker.engine.api.deployment

import io.circe.generic.JsonCodec

@JsonCodec case class DeploymentData(deploymentId: DeploymentId,
                                     user: User,
                                     additionalDeploymentData: Map[String, String])

object DeploymentData {

  val systemUser: User = User("system", "system")

  val empty: DeploymentData = DeploymentData(DeploymentId(""), systemUser, Map.empty)


}