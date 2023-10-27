package pl.touk.nussknacker.restmodel.displayedgraph

import io.circe.generic.JsonCodec
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder
import io.circe.{Decoder, Encoder, HCursor}
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue
import pl.touk.nussknacker.engine.api.fixedvaluespresets.FixedValuesPresetProvider
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{MetaData, ProcessAdditionalFields, TypeSpecificData}
import pl.touk.nussknacker.engine.graph.NodeDataCodec._
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.{FragmentInput, NodeData}
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode._
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult

//it would be better to have two classes but it would either to derive from each other, which is not easy for final case classes
//or we'd have to do composition which would break many things in client
// TODO: id type should be ProcessName
@JsonCodec final case class DisplayableProcess(
    id: String,
    properties: ProcessProperties,
    nodes: List[NodeData],
    edges: List[Edge],
    processingType: ProcessingType,
    category: String
) {

  val metaData: MetaData = properties.toMetaData(id)

  val processName: ProcessName = ProcessName(id)

}

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

final case class ProcessProperties(additionalFields: ProcessAdditionalFields) {

  def toMetaData(id: String): MetaData = MetaData(
    id = id,
    additionalFields = additionalFields
  )

  val isFragment: Boolean = additionalFields.typeSpecificProperties.isFragment

  // TODO: remove typeSpecificData-related code after the migration is completed
  def typeSpecificProperties: TypeSpecificData = additionalFields.typeSpecificProperties

}

object ProcessProperties {

  def combineTypeSpecificProperties(
      typeSpecificProperties: TypeSpecificData,
      additionalFields: ProcessAdditionalFields
  ): ProcessProperties = {
    ProcessProperties(additionalFields.combineTypeSpecificProperties(typeSpecificProperties))
  }

  def apply(typeSpecificProperties: TypeSpecificData): ProcessProperties = {
    ProcessProperties.combineTypeSpecificProperties(
      typeSpecificProperties,
      ProcessAdditionalFields(None, Map(), typeSpecificProperties.metaDataType)
    )
  }

  implicit val encodeProcessProperties: Encoder[ProcessProperties] =
    Encoder.forProduct2("isFragment", "additionalFields") { p =>
      (p.isFragment, p.additionalFields)
    }

  // This is a copy-paste from MetaData - see the comment there for the legacy consideration
  implicit val decoder: Decoder[ProcessProperties] = {
    val actualDecoder: Decoder[ProcessProperties] = deriveConfiguredDecoder[ProcessProperties]

    val legacyDecoder: Decoder[ProcessProperties] = {
      def legacyProcessAdditionalFieldsDecoder(metaDataType: String): Decoder[ProcessAdditionalFields] =
        (c: HCursor) =>
          for {
            description <- c.downField("description").as[Option[String]]
            properties  <- c.downField("properties").as[Option[Map[String, String]]]
          } yield {
            ProcessAdditionalFields(description, properties.getOrElse(Map.empty), metaDataType)
          }

      (c: HCursor) =>
        for {
          typeSpecificData <- c.downField("typeSpecificProperties").as[TypeSpecificData]
          additionalFields <- c
            .downField("additionalFields")
            .as[Option[ProcessAdditionalFields]](
              io.circe.Decoder.decodeOption(
                legacyProcessAdditionalFieldsDecoder(typeSpecificData.metaDataType)
              )
            )
            .map(_.getOrElse(ProcessAdditionalFields(None, Map.empty, typeSpecificData.metaDataType)))
        } yield {
          ProcessProperties.combineTypeSpecificProperties(typeSpecificData, additionalFields)
        }
    }

    actualDecoder or legacyDecoder
  }

}
