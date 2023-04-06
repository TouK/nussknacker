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
import pl.touk.nussknacker.restmodel.displayedgraph.ProcessProperties.extractTypedData

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
case class ProcessProperties(additionalFields: ProcessAdditionalFields, propertiesType: String) {

  def toMetaData(id: String): MetaData = {

    val (typeSpecificData, additionalFields) = extractTypedData(this)

    MetaData(
      id = id,
      typeSpecificData = typeSpecificData,
      additionalFields = additionalFields
    )
  }

  val isSubprocess: Boolean = extractTypedData(this)._1.isSubprocess

}

object ProcessProperties {

  def apply(typeSpecificProperties: TypeSpecificData,
            additionalFields: Option[ProcessAdditionalFields] = None): ProcessProperties = {
    val genericTypeSpecificData = typeSpecificProperties.toGenericMap
    val additionalProps = additionalFields.map(a => a.properties).getOrElse(None)
    val description = additionalFields.flatMap(a => a.description)

    val props = ProcessAdditionalFields(description, genericTypeSpecificData ++ additionalProps)

    // We set the classname to make converting back from generic to typed easier
    val typeSpecificClassName = typeSpecificProperties.getClass.getSimpleName
    ProcessProperties(props, typeSpecificClassName)
  }

  private def extractTypedData(processProperties: ProcessProperties): (TypeSpecificData, Option[ProcessAdditionalFields]) = {
    val genericProperties = processProperties.additionalFields.properties

    val typeSpecificData = processProperties.propertiesType match {
      case "StreamMetaData" => StreamMetaData(
        parallelism = Try(genericProperties.get("parallelism").map(_.toInt)).getOrElse(None),
        spillStateToDisk = Try(genericProperties.get("spillStateToDisk").map(_.toBoolean)).getOrElse(None),
        useAsyncInterpretation = Try(genericProperties.get("useAsyncInterpretation").map(_.toBoolean)).getOrElse(None),
        checkpointIntervalInSeconds = Try(genericProperties.get("checkpointIntervalInSeconds").map(_.toLong)).getOrElse(None)
      )
      case "LiteStreamMetaData" => LiteStreamMetaData(
        parallelism = Try(genericProperties.get("parallelism").map(_.toInt)).getOrElse(None)
      )
      case "RequestResponseMetaData" => RequestResponseMetaData(
        slug = Try(genericProperties.get("slug")).getOrElse(None)
      )
      case "FragmentSpecificData" => FragmentSpecificData(
        docsUrl = Try(genericProperties.get("docsUrl")).getOrElse(None)
      )
      case _ => throw new IllegalStateException("Unrecognized metadata type")
    }

    val typeSpecificPropsList = typeSpecificData.toGenericMap.keySet
    val additionalFields = Some(ProcessAdditionalFields(processProperties.additionalFields.description, genericProperties.filterNot(k => typeSpecificPropsList.contains(k._1))))

    (typeSpecificData, additionalFields)
  }

  implicit val encodeProcessProperties: Encoder[ProcessProperties] =
    Encoder.forProduct3("additionalFields", "isSubprocess", "propertiesType") { p =>
      (p.additionalFields, p.isSubprocess, p.propertiesType)
    }
}
