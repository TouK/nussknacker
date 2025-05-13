package pl.touk.nussknacker.engine.lite.app

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData

@JsonCodec
case class LiteDeploymentData(tasksCount: Option[Int], nodesData: NodesDeploymentData) {
  def tasksCountUnsafe: Int = tasksCount.getOrElse(throw new IllegalArgumentException("tasksCount is missing"))
}
