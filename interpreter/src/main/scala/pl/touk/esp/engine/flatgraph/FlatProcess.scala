package pl.touk.esp.engine.flatgraph

import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.flatgraph.flatnode.FlatNode

case class FlatProcess(metaData: MetaData, nodes: List[FlatNode])
