package pl.touk.nussknacker.engine.api.deployment

import io.circe.generic.JsonCodec

@JsonCodec case class DeploymentVersion(deploymentId: DeploymentId,
                                        user: User,
                                        additionalDeploymentData: Map[String, String])

object DeploymentVersion {

  val systemUser: User = User("system", "system")

  val empty: DeploymentVersion = DeploymentVersion(DeploymentId(""), systemUser, Map.empty)


}