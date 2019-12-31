package pl.touk.nussknacker.engine.additionalInfo

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.graph.node.{Node, NodeData}

import scala.concurrent.Future

trait NodeAdditionalInfoProvider {

  def additionalInfo(config: Config)(node: NodeData): Future[Option[NodeAdditionalInfo]]

}
