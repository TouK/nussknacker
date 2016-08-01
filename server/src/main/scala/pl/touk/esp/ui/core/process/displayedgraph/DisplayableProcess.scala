package pl.touk.esp.ui.core.process.displayedgraph

import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.ui.core.process.displayedgraph.displayablenode._

case class DisplayableProcess(metaData: MetaData,
                              nodes: List[DisplayableNode],
                              edges: List[Edge])