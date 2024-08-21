package pl.touk.nussknacker.engine.api.graph

import io.circe.generic.JsonCodec
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder
import io.circe.{Decoder, Encoder, HCursor}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{MetaData, ProcessAdditionalFields, TypeSpecificData}
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.graph.EdgeType
import pl.touk.nussknacker.engine.api.CirceUtil._

@JsonCodec final case class ScenarioGraph(
    properties: ProcessProperties,
    nodes: List[NodeData],
    edges: List[Edge]
) {

  def toMetaData(name: ProcessName, labels: List[String]): MetaData = properties.toMetaData(name, labels)

}

@JsonCodec final case class Edge(from: String, to: String, edgeType: Option[EdgeType])

// We have the same additionalFields name as in NodeData because properties are treated as node on the FE side
// TODO: remove additionalFields nesting when we stop treating properties as a node on the FE side
final case class ProcessProperties(additionalFields: ProcessAdditionalFields) {

  def toMetaData(scenarioName: ProcessName, labels: List[String]): MetaData = MetaData(
    id = scenarioName.value,
    labels = labels,
    additionalFields = additionalFields
  )

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
    Encoder.forProduct1("additionalFields") { p =>
      p.additionalFields
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
