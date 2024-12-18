package pl.touk.nussknacker.engine.deployment

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData

@JsonCodec case class DeploymentData(
    deploymentId: DeploymentId,
    user: User,
    additionalDeploymentData: Map[String, String],
    nodesData: NodesDeploymentData,
    additionalModelConfigs: AdditionalModelConfigs
)

object DeploymentData {

  val systemUser: User = User("system", "system")

  val empty: DeploymentData =
    DeploymentData(
      DeploymentId(""),
      systemUser,
      Map.empty,
      NodesDeploymentData.empty,
      AdditionalModelConfigs.empty
    )

  def withDeploymentId(deploymentIdString: String) =
    DeploymentData(
      DeploymentId(deploymentIdString),
      systemUser,
      Map.empty,
      NodesDeploymentData.empty,
      AdditionalModelConfigs.empty
    )

}
