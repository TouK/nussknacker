package pl.touk.esp.engine.compiledgraph

import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.compiledgraph.node.Source

case class CompiledProcess(metaData: MetaData, root: Source) {
  def id = metaData.id
}

