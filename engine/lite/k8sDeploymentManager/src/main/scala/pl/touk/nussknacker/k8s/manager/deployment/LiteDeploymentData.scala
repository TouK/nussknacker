package pl.touk.nussknacker.k8s.manager.deployment

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData

@JsonCodec
case class LiteDeploymentData(tasksCount: Int, nodesData: NodesDeploymentData)
