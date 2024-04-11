package pl.touk.nussknacker.engine.deployment

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.component.NodesEventsFilteringRules

@JsonCodec case class DeploymentData(
    deploymentId: DeploymentId,
    user: User,
    additionalDeploymentData: Map[String, String],
    nodesEventsFilteringRules: NodesEventsFilteringRules
)

object DeploymentData {

  val systemUser: User = User("system", "system")

  val empty: DeploymentData =
    DeploymentData(DeploymentId(""), systemUser, Map.empty, NodesEventsFilteringRules.PassAllEventsForEveryNode)

  def withDeploymentId(deploymentIdString: String) =
    DeploymentData(
      DeploymentId(deploymentIdString),
      systemUser,
      Map.empty,
      NodesEventsFilteringRules.PassAllEventsForEveryNode
    )

}
