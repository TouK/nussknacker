package pl.touk.nussknacker.restmodel.displayedgraph

import io.circe.Encoder
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{MetaData, ProcessAdditionalFields, TypeSpecificData}
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode._
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.engine.graph.NodeDataCodec._

//it would be better to have two classes but it would either to derivce from each other, which is not easy for case classes
//or we'd have to do composition which would break many things in client
// todo: id type should be ProcessName
@JsonCodec case class DisplayableProcess(id: String,
                                         properties: ProcessProperties,
                                         nodes: List[NodeData],
                                         edges: List[Edge],
                                         processingType: ProcessingType) {

  val metaData: MetaData = properties.toMetaData(id)

  val processName: ProcessName = ProcessName(id)
}

@JsonCodec case class ValidatedDisplayableProcess(id: String,
                                                  properties: ProcessProperties,
                                                  nodes: List[NodeData],
                                                  edges: List[Edge],
                                                  processingType: ProcessingType,
                                                  validationResult: ValidationResult) {

  def this(displayableProcess: DisplayableProcess, validationResult: ValidationResult) =
    this(
      displayableProcess.id,
      displayableProcess.properties,
      displayableProcess.nodes,
      displayableProcess.edges,
      displayableProcess.processingType,
      validationResult
    )

  def toDisplayable: DisplayableProcess = DisplayableProcess(id, properties, nodes, edges, processingType)

}

@JsonCodec(decodeOnly = true)
case class ProcessProperties(typeSpecificProperties: TypeSpecificData,
                             additionalFields: Option[ProcessAdditionalFields] = None,
                             subprocessVersions: Map[String, Long] = Map.empty) { //TODO: field subprocessVersions is deprecate - to remove

  def toMetaData(id: String): MetaData = MetaData(
    id = id,
    typeSpecificData = typeSpecificProperties,
    additionalFields = additionalFields,
    subprocessVersions = subprocessVersions
  )
  val isSubprocess: Boolean = typeSpecificProperties.isSubprocess

}

object ProcessProperties {
  implicit val encodeProcessProperties: Encoder[ProcessProperties] =
    Encoder.forProduct4("typeSpecificProperties", "isSubprocess", "additionalFields", "subprocessVersions") { p =>
    (p.typeSpecificProperties, p.isSubprocess, p.additionalFields, p.subprocessVersions)
  }
}
