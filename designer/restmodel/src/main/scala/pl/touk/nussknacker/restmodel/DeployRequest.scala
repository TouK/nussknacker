package pl.touk.nussknacker.restmodel

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.deployment.ScenarioGraphSource

@JsonCodec final case class DeployRequest(
    comment: Option[String],
    nodesDeploymentData: Option[NodesDeploymentData],
    // TODO: The Option is taken for this compatibility reason, but should be changed to a required field:
    scenarioGraphSource: Option[ScenarioGraphSource],
)
