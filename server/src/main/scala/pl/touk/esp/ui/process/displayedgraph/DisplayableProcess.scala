package pl.touk.esp.ui.process.displayedgraph

import pl.touk.esp.engine.api.UserDefinedProcessAdditionalFields
import pl.touk.esp.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.esp.engine.graph.node.NodeData
import pl.touk.esp.ui.api.ProcessValidation.ValidationResult
import pl.touk.esp.ui.process.displayedgraph.displayablenode._

case class DisplayableProcess(id: String,
                              properties: ProcessProperties,
                              nodes: List[NodeData],
                              edges: List[Edge],
                              validationResult: ValidationResult)

case class ProcessProperties(parallelism: Option[Int],
                             splitStateToDisk: Option[Boolean],
                             exceptionHandler: ExceptionHandlerRef,
                             additionalFields: Option[UserDefinedProcessAdditionalFields])