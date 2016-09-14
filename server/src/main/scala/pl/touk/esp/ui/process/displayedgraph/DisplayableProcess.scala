package pl.touk.esp.ui.process.displayedgraph

import pl.touk.esp.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.esp.ui.process.displayedgraph.displayablenode._

case class DisplayableProcess(id: String,
                              properties: ProcessProperties,
                              nodes: List[DisplayableNode],
                              edges: List[Edge])

case class ProcessProperties(parallelism: Option[Int], exceptionHandler: ExceptionHandlerRef)