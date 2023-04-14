package pl.touk.nussknacker.restmodel.displayedgraph

import io.circe.Encoder
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, LiteStreamMetaData, MetaData, ProcessAdditionalFields, RequestResponseMetaData, StreamMetaData, TypeSpecificData}
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode._
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult
import pl.touk.nussknacker.restmodel.process.{ProcessIdWithName, ProcessingType}
import pl.touk.nussknacker.engine.graph.NodeDataCodec._

import scala.util.Try

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
case class ProcessProperties(typeSpecificProperties: ProcessAdditionalFields,
                             additionalFields: Option[ProcessAdditionalFields] = None) {

  def toMetaData(id: String): MetaData = MetaData(
    id = id,
    typeSpecificData = toTypeSpecificData(typeSpecificProperties),
    additionalFields = additionalFields
  )

  private def toTypeSpecificData(additionalProperties: ProcessAdditionalFields): TypeSpecificData = {
    additionalProperties.properties match {
      case a if a.contains("spillStateToDisk") => StreamMetaData(
          parallelism = Try(a.get("parallelism").map(_.toInt)).getOrElse(None),
          spillStateToDisk = Try(a.get("spillStateToDisk").map(_.toBoolean)).getOrElse(None),
          useAsyncInterpretation = Try(a.get("useAsyncInterpretation").map(_.toBoolean)).getOrElse(None),
          checkpointIntervalInSeconds = Try(a.get("checkpointIntervalInSeconds").map(_.toLong)).getOrElse(None)
      )
      case a if a.contains("slug") => RequestResponseMetaData(
        slug = Try(a.get("slug").map(_.toString)).getOrElse(None)
      )
      case a if a.contains("parallelism") && !a.contains("spillStateToDisk") => LiteStreamMetaData(
        parallelism = Try(a.get("parallelism").map(_.toInt)).getOrElse(None)
      )
      case a if a.contains("docsUrl") => FragmentSpecificData(
        Try(a.get("docsUrl")).getOrElse(None)
      )
    }
  }

  val isSubprocess: Boolean = toTypeSpecificData(typeSpecificProperties).isSubprocess

}

object ProcessProperties {
  implicit val encodeProcessProperties: Encoder[ProcessProperties] =
    Encoder.forProduct3("typeSpecificProperties", "isSubprocess", "additionalFields") { p =>
    (p.typeSpecificProperties, p.isSubprocess, p.additionalFields)
  }
}
