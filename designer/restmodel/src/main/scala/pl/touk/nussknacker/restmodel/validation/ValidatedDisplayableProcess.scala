package pl.touk.nussknacker.restmodel.validation

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.displayedgraph.displayablenode.Edge
import pl.touk.nussknacker.engine.api.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.engine.api.process.{ProcessName, ProcessingType}
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult

@JsonCodec final case class ValidatedDisplayableProcess(
    // TODO: remove - it is already available in ScenarioWithDetails, it is never used in BE, we only need to change FE
    //       to doesn't use it as well
    name: ProcessName,
    properties: ProcessProperties,
    nodes: List[NodeData],
    edges: List[Edge],
    // TODO: remove both processingType and category - they are already available in ScenarioWithDetails
    processingType: ProcessingType,
    category: String,
    validationResult: Option[ValidationResult]
) {
  def toDisplayable(processName: ProcessName): DisplayableProcess =
    DisplayableProcess(processName, properties, nodes, edges, processingType, category)

}

object ValidatedDisplayableProcess {

  def withValidationResult(
      displayableProcess: DisplayableProcess,
      validationResult: ValidationResult
  ): ValidatedDisplayableProcess =
    new ValidatedDisplayableProcess(
      displayableProcess.name,
      displayableProcess.properties,
      displayableProcess.nodes,
      displayableProcess.edges,
      displayableProcess.processingType,
      displayableProcess.category,
      Some(validationResult)
    )

  def withEmptyValidationResult(displayableProcess: DisplayableProcess): ValidatedDisplayableProcess =
    new ValidatedDisplayableProcess(
      displayableProcess.name,
      displayableProcess.properties,
      displayableProcess.nodes,
      displayableProcess.edges,
      displayableProcess.processingType,
      displayableProcess.category,
      None
    )

}
