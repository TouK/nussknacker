package pl.touk.nussknacker.restmodel.displayedgraph

import io.circe.Encoder
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{MetaData, ProcessAdditionalFields, TypeSpecificData}
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode._
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult
import pl.touk.nussknacker.restmodel.process.{ProcessIdWithName, ProcessingType}
import pl.touk.nussknacker.engine.graph.NodeDataCodec._

//it would be better to have two classes but it would either to derivce from each other, which is not easy for case classes
//or we'd have to do composition which would break many things in client
// todo: id type should be ProcessName
@JsonCodec case class DisplayableProcess(id: String,
                                         properties: ProcessProperties,
                                         nodes: List[NodeData],
                                         edges: List[Edge],
                                         processingType: ProcessingType,
                                         category: String) {

  val metaData: MetaData = properties.toMetaData(id)

  val processName: ProcessName = ProcessName(id)

}

@JsonCodec case class ValidatedDisplayableProcess(id: String,
                                                  properties: ProcessProperties,
                                                  nodes: List[NodeData],
                                                  edges: List[Edge],
                                                  processingType: ProcessingType,
                                                  category: String,
                                                  validationResult: ValidationResult) {

  def this(displayableProcess: DisplayableProcess, validationResult: ValidationResult) =
    this(
      displayableProcess.id,
      displayableProcess.properties,
      displayableProcess.nodes,
      displayableProcess.edges,
      displayableProcess.processingType,
      displayableProcess.category,
      validationResult
    )

  def toDisplayable: DisplayableProcess = DisplayableProcess(id, properties, nodes, edges, processingType, category)

}

@JsonCodec(decodeOnly = true)
case class ProcessProperties(additionalFields: ProcessAdditionalFields) {

  def toMetaData(id: String): MetaData = MetaData(
    id = id,
    additionalFields = additionalFields
  )
  val isSubprocess: Boolean = additionalFields.typeSpecificProperties.isSubprocess

  def typeSpecificProperties: TypeSpecificData = additionalFields.typeSpecificProperties

}

object ProcessProperties {

  def apply(typeSpecificProperties: TypeSpecificData,
            additionalFields: ProcessAdditionalFields): ProcessProperties = {
    ProcessProperties(ProcessAdditionalFields(additionalFields.description, additionalFields.properties ++ typeSpecificProperties.toMap, typeSpecificProperties.scenarioType))
  }

  def apply(typeSpecificProperties: TypeSpecificData): ProcessProperties = {
    ProcessProperties(typeSpecificProperties, ProcessAdditionalFields(None, typeSpecificProperties.toMap, typeSpecificProperties.scenarioType))
  }

  implicit val encodeProcessProperties: Encoder[ProcessProperties] =
    Encoder.forProduct2( "isSubprocess", "additionalFields") { p =>
      (p.isSubprocess, p.additionalFields)
  }
}
