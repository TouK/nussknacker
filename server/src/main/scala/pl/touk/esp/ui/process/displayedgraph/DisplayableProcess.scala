package pl.touk.esp.ui.process.displayedgraph

import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.esp.ui.process.displayedgraph.displayablenode._

case class DisplayableProcess(metaData: MetaData,
                              exceptionHandlerRef: ExceptionHandlerRef,
                              nodes: List[DisplayableNode],
                              edges: List[Edge])