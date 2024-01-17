package pl.touk.nussknacker.restmodel.validation

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.displayedgraph.displayablenode.Edge
import pl.touk.nussknacker.engine.api.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.engine.api.process.{ProcessName, ProcessingType}
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult

@JsonCodec final case class ValidatedDisplayableProcess(
    properties: ProcessProperties,
    nodes: List[NodeData],
    edges: List[Edge],
    validationResult: Option[ValidationResult]
) {
  def toDisplayable: DisplayableProcess =
    DisplayableProcess(properties, nodes, edges)

}

object ValidatedDisplayableProcess {

  def withValidationResult(
      displayableProcess: DisplayableProcess,
      validationResult: ValidationResult
  ): ValidatedDisplayableProcess =
    new ValidatedDisplayableProcess(
      displayableProcess.properties,
      displayableProcess.nodes,
      displayableProcess.edges,
      Some(validationResult)
    )

  def withEmptyValidationResult(displayableProcess: DisplayableProcess): ValidatedDisplayableProcess =
    new ValidatedDisplayableProcess(
      displayableProcess.properties,
      displayableProcess.nodes,
      displayableProcess.edges,
      None
    )

}
