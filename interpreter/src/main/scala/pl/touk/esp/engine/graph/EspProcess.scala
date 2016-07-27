package pl.touk.esp.engine.graph

import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.graph.node.Source

case class EspProcess(metaData: MetaData, root: Source) {
  def id = metaData.id
}

