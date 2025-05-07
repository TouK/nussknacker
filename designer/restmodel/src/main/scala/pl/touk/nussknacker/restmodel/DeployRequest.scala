package pl.touk.nussknacker.restmodel

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData

@JsonCodec final case class DeployRequest(
    comment: Option[String],
    // ... tu potencjalnie flaga czy deploy/redeploy?
    nodesDeploymentData: Option[NodesDeploymentData]
)
