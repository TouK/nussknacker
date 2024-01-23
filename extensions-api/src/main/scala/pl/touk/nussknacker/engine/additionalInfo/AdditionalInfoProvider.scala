package pl.touk.nussknacker.engine.additionalInfo

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.graph.node.NodeData

import scala.concurrent.Future

/**
  * Trait allowing models to prepare additional info for nodes (e.g. links, sample data etc.)
  * Implementations have to be registered via ServiceLoader mechanism.
  *
  * additionalInfo method is invoked when node changes, so it should be relatively fast.
  */
trait AdditionalInfoProvider {

  def nodeAdditionalInfo(config: Config)(node: NodeData): Future[Option[AdditionalInfo]]

  // TODO We should pass here only scenario properties
  def propertiesAdditionalInfo(config: Config)(metaData: MetaData): Future[Option[AdditionalInfo]]

}
