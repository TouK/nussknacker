package pl.touk.esp.ui.process.displayedgraph

import pl.touk.esp.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.esp.ui.api.ProcessValidation.ValidationResult
import pl.touk.esp.ui.process.displayedgraph.displayablenode._

case class DisplayableProcess(id: String,
                              properties: ProcessProperties,
                              nodes: List[DisplayableNode],
                              edges: List[Edge],
                              validationErrors: Option[ValidationResult])

case class ProcessProperties(parallelism: Option[Int], exceptionHandler: ExceptionHandlerRef)