package pl.touk.nussknacker.restmodel.validation

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.displayedgraph.displayablenode.Edge
import pl.touk.nussknacker.engine.api.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.engine.graph.NodeDataCodec._

@JsonCodec final case class ValidatedDisplayableProcess(
    id: String,
    properties: ProcessProperties,
    nodes: List[NodeData],
    edges: List[Edge],
    processingType: ProcessingType,
    category: String,
    validationResult: Option[ValidationResult]
) {

  def toDisplayable: DisplayableProcess = DisplayableProcess(id, properties, nodes, edges, processingType, category)

}

object ValidatedDisplayableProcess {

  def apply(displayableProcess: DisplayableProcess, validationResult: ValidationResult): ValidatedDisplayableProcess =
    new ValidatedDisplayableProcess(
      displayableProcess.id,
      displayableProcess.properties,
      displayableProcess.nodes,
      displayableProcess.edges,
      displayableProcess.processingType,
      displayableProcess.category,
      Some(validationResult)
    )

  def withEmptyValidationResult(displayableProcess: DisplayableProcess): ValidatedDisplayableProcess =
    new ValidatedDisplayableProcess(
      displayableProcess.id,
      displayableProcess.properties,
      displayableProcess.nodes,
      displayableProcess.edges,
      displayableProcess.processingType,
      displayableProcess.category,
      None
    )

}
