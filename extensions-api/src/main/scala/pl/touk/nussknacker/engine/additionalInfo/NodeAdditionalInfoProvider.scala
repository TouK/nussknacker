package pl.touk.nussknacker.engine.additionalInfo

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.graph.node.NodeData

import scala.concurrent.Future

/**
  * Trait allowing models to prepare additional info for nodes (e.g. links, sample data etc.)
  * Implementations have to be registered via ServiceLoader mechanism.
  *
  * additionalInfo method is invoked when node changes, so it should be relatively fast.
  */
trait NodeAdditionalInfoProvider {

  def additionalInfo(config: Config)(node: NodeData): Future[Option[NodeAdditionalInfo]]

}
