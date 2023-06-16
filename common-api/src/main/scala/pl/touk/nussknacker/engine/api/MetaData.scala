package pl.touk.nussknacker.engine.api

import io.circe.generic.JsonCodec
import io.circe.generic.extras.ConfiguredJsonCodec
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.{Decoder, Encoder, HCursor}
import pl.touk.nussknacker.engine.api.CirceUtil._

@JsonCodec case class LayoutData(x: Long, y: Long)

// todo: MetaData should hold ProcessName as id
@ConfiguredJsonCodec(encodeOnly = true) case class MetaData(id: String,
                                                            additionalFields: ProcessAdditionalFields) {
  def isSubprocess: Boolean = typeSpecificData.isSubprocess

  def typeSpecificData: TypeSpecificData = additionalFields.typeSpecificProperties

  def withTypeSpecificData(typeSpecificData: TypeSpecificData): MetaData = {
    MetaData(id, typeSpecificData)
  }
}

object MetaData {

  private val actualDecoder: Decoder[MetaData] = deriveConfiguredDecoder[MetaData]

  // TODO: remove legacy decoder after the migration is completed
  private val legacyDecoder: Decoder[MetaData] = {
    def legacyProcessAdditionalFieldsDecoder(scenarioType: String): Decoder[ProcessAdditionalFields] =
      (c: HCursor) => for {
        id <- c.downField("description").as[Option[String]]
        properties <- c.downField("properties").as[Option[Map[String, String]]]
      } yield {
        ProcessAdditionalFields(id, properties.getOrElse(Map.empty), scenarioType)
      }

    (c: HCursor) => for {
      id <- c.downField("id").as[String]
      typeSpecificData <- c.downField("typeSpecificData").as[TypeSpecificData]
      additionalFields <- c.downField("additionalFields")
        .as[Option[ProcessAdditionalFields]](
          io.circe.Decoder.decodeOption(
            legacyProcessAdditionalFieldsDecoder(typeSpecificData.metaDataType)
          )
        ).map(_.getOrElse(ProcessAdditionalFields(None, Map.empty, typeSpecificData.metaDataType)))
    } yield {
      MetaData(id, typeSpecificData, additionalFields)
    }
  }

  implicit val decoder: Decoder[MetaData] = actualDecoder or legacyDecoder

  def apply(id: String, typeSpecificData: TypeSpecificData, additionalFields: ProcessAdditionalFields): MetaData = {
    MetaData(id = id, additionalFields = additionalFields.copy(
      properties = additionalFields.properties ++ typeSpecificData.toMap
    ))
  }

  def apply(id: String, typeSpecificData: TypeSpecificData): MetaData = {
    MetaData(
      id = id,
      additionalFields = ProcessAdditionalFields(None, typeSpecificData.toMap, typeSpecificData.metaDataType)
    )
  }
}

// TODO: remove metaDataType val and typeSpecificProperties def after the migration is completed
case class ProcessAdditionalFields(description: Option[String],
                                   properties: Map[String, String],
                                   metaDataType: String) {

  def typeSpecificProperties: TypeSpecificData = {
    metaDataType match {
      case "StreamMetaData" => StreamMetaData(properties)
      case "LiteStreamMetaData" => LiteStreamMetaData(properties)
      case "RequestResponseMetaData" => RequestResponseMetaData(properties)
      case "FragmentSpecificData" => FragmentSpecificData(properties)
      case _ => throw new IllegalStateException("Unrecognized metadata type.")
    }
  }

}

object ProcessAdditionalFields {

  //TODO: is this currently needed?
  private case class OptionalProcessAdditionalFields(description: Option[String],
                                                     properties: Option[Map[String, String]],
                                                     metaDataType: String)

  implicit val circeDecoder: Decoder[ProcessAdditionalFields]
  = deriveConfiguredDecoder[OptionalProcessAdditionalFields].map(opp => ProcessAdditionalFields(opp.description, opp.properties.getOrElse(Map()), opp.metaDataType))

  implicit val circeEncoder: Encoder[ProcessAdditionalFields] = deriveConfiguredEncoder

}
