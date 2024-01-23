package pl.touk.nussknacker.engine.api.displayedgraph

import io.circe.generic.JsonCodec
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder
import io.circe.{Decoder, Encoder, HCursor}
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.displayedgraph.displayablenode._
import pl.touk.nussknacker.engine.api.process.{ProcessName, ProcessingType}
import pl.touk.nussknacker.engine.api.{MetaData, ProcessAdditionalFields, TypeSpecificData}
import pl.touk.nussknacker.engine.graph.node.NodeData

@JsonCodec final case class DisplayableProcess(
    // TODO: remove - it is already available in ScenarioWithDetails
    name: ProcessName,
    properties: ProcessProperties,
    nodes: List[NodeData],
    edges: List[Edge],
    // TODO: remove both processingType and category - they are already available in ScenarioWithDetails
    processingType: ProcessingType,
    category: String
) {

  val metaData: MetaData = properties.toMetaData(name)

}

final case class ProcessProperties(additionalFields: ProcessAdditionalFields) {

  def toMetaData(scenarioName: ProcessName): MetaData = MetaData(
    id = scenarioName.value,
    additionalFields = additionalFields
  )

  // TODO: Remove this after we stop using `DisplayableProcess` for the source of information about scenario's metadata
  //       (processToDisplay redux state) on the FE side.,
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
