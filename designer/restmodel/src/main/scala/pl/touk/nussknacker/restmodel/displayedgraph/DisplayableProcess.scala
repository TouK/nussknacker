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

// TODO: remove propertiesType and additionalFields nesting
@JsonCodec(decodeOnly = true)
case class ProcessProperties(additionalFields: ProcessAdditionalFields, propertiesType: String) {

  def toMetaData(id: String): MetaData = MetaData(id = id, properties = additionalFields, propertiesType = propertiesType)

  val isSubprocess: Boolean = additionalFields.extractTypedData(propertiesType)._1.isSubprocess

}

object ProcessProperties {

  def apply(typeSpecificProperties: TypeSpecificData,
            additionalFields: Option[ProcessAdditionalFields] = None): ProcessProperties = {
    val typeSpecificPropertiesMap = typeSpecificProperties.toProperties
    val propertiesMap = additionalFields.map(a => a.properties).getOrElse(Map())
    val mergedProps = mergeProperties(typeSpecificPropertiesMap, propertiesMap)
    val description = additionalFields.flatMap(a => a.description)

    val props = ProcessAdditionalFields(description, mergedProps)

    // We set the classname to make converting back from generic to typed easier
    val typeSpecificClassName = typeSpecificProperties.getClass.getSimpleName
    ProcessProperties(props, typeSpecificClassName)
  }

  private def mergeProperties(typeSpecificProperties: Map[String, String],
                              properties: Map[String, String]): Map[String, String] = {
    // We check if the additional properties contain a value that could override a scenario property
    // and throw exception in that case
    properties.toList
      .filter(p => typeSpecificProperties.contains(p._1))
      .filter(duplicate => typeSpecificProperties(duplicate._1) == duplicate._2)
      .foreach(incompatible => {
        // TODO: test this
        throw new IllegalStateException(s"Incompatible duplicate properties in typeSpecificProperties and additionalFields: ${incompatible._1}")
      })
    typeSpecificProperties ++ properties
  }

  implicit val encodeProcessProperties: Encoder[ProcessProperties] =
    Encoder.forProduct3("additionalFields", "isSubprocess", "propertiesType") { p =>
      (p.additionalFields, p.isSubprocess, p.propertiesType)
    }
}
