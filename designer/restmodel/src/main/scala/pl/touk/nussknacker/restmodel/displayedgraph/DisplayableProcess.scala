package pl.touk.nussknacker.restmodel.displayedgraph

import com.typesafe.scalalogging.LazyLogging
import io.circe.Encoder
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{
  FragmentSpecificData,
  LiteStreamMetaData,
  MetaData,
  ProcessAdditionalFields,
  RequestResponseMetaData,
  StreamMetaData,
  TypeSpecificData
}
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

  val isSubprocess: Boolean = additionalFields.extractTypeSpecificData(propertiesType)._1.isSubprocess

}

object ProcessProperties extends LazyLogging {

  def apply(metaData: MetaData): ProcessProperties = {
    ProcessProperties(metaData.typeSpecificData, metaData.additionalFields)
  }

  def apply(
    typeSpecificProperties: TypeSpecificData,
    additionalFields: Option[ProcessAdditionalFields] = None
  ): ProcessProperties = {
    val mergedProps = merge(additionalFields, typeSpecificProperties)
    // We set the classname to make converting back from generic to typed easier
    val typeSpecificClassName = typeSpecificProperties match {
      case _: StreamMetaData          => "StreamMetaData"
      case _: LiteStreamMetaData      => "LiteStreamMetaData"
      case _: RequestResponseMetaData => "RequestResponseMetaData"
      case _: FragmentSpecificData    => "FragmentSpecificData"
      case _                          => throw new IllegalStateException("Type specific properties name not recognized.")
    }
    ProcessProperties(mergedProps, typeSpecificClassName)
  }

  private def merge(properties: Option[ProcessAdditionalFields],
                    typeSpecificData: TypeSpecificData): ProcessAdditionalFields = {
    val fields = properties.getOrElse(ProcessAdditionalFields.empty)
    // For ease of transitioning into generic properties, we write duplicate TypeSpecificData into the generic
    // properties. Only when we have a non-null value in TypeSpecificData and a different corresponding generic property
    // we throw an exception since they need to be synchronized - in this case you have to synchronize these values manually
    typeSpecificData.toProperties
      .filterNot(prop => prop._2.isEmpty)
      .filter(prop => fields.properties.contains(prop._1))
      .filter(duplicate => fields.properties(duplicate._1) != duplicate._2)
      .foreach(
        incompatible =>
          throw new IllegalStateException(
            s"Duplicate incompatible properties with the same name '${incompatible._1}'. " +
              s"The properties have different values: '${incompatible._2}' and '${fields.properties(incompatible._1)}'."
        )
      )

    val mergedProps = fields.properties ++ typeSpecificData.toProperties
    ProcessAdditionalFields(fields.description, mergedProps)
  }

  implicit val encodeProcessProperties: Encoder[ProcessProperties] =
    Encoder.forProduct3("additionalFields", "isSubprocess", "propertiesType") { p =>
      (p.additionalFields, p.isSubprocess, p.propertiesType)
    }
}
