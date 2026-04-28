package pl.touk.nussknacker.engine.api

import io.circe.{Decoder, Encoder, HCursor, Json, JsonObject}
import io.circe.generic.JsonCodec
import io.circe.generic.extras.ConfiguredJsonCodec
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.process.ProcessName

@JsonCodec case class LayoutData(x: Long, y: Long)

// TODO: This class should be moved into components-api, scenario-api shouldn't use this. It should hold only properties
//       and ScenarioRuntimeMetadata (Currently called ProcessVersion)
@ConfiguredJsonCodec(encodeOnly = true) case class MetaData(id: String, additionalFields: ProcessAdditionalFields) {
  def typeSpecificData: TypeSpecificData = additionalFields.typeSpecificProperties

  def withTypeSpecificData(typeSpecificData: TypeSpecificData): MetaData = {
    MetaData(id, typeSpecificData)
  }

  def name: ProcessName = ProcessName(id)

}

object MetaData {

  private val actualDecoder: Decoder[MetaData] = deriveConfiguredDecoder[MetaData]

  /**
   * TODO: remove legacy decoder after the migration is completed
   * This may cause problems, because the relevant migration is not done in transaction - some processes may still be in a legacy state
   * (for example some archived processes which the migration could not handle).
   */
  private val legacyDecoder: Decoder[MetaData] = {
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
        id               <- c.downField("id").as[String]
        typeSpecificData <- c.downField("typeSpecificData").as[TypeSpecificData]
        additionalFields <- c
          .downField("additionalFields")
          .as[Option[ProcessAdditionalFields]](
            io.circe.Decoder.decodeOption(
              legacyProcessAdditionalFieldsDecoder(typeSpecificData.metaDataType)
            )
          )
          .map(_.getOrElse(ProcessAdditionalFields(None, Map.empty, typeSpecificData.metaDataType)))
      } yield {
        MetaData.combineTypeSpecificProperties(id, typeSpecificData, additionalFields)
      }
  }

  implicit val decoder: Decoder[MetaData] = actualDecoder or legacyDecoder

  // TODO: remove legacy constructors after the migration is completed
  def combineTypeSpecificProperties(
      id: String,
      typeSpecificData: TypeSpecificData,
      additionalFields: ProcessAdditionalFields
  ): MetaData = {
    MetaData(
      id = id,
      additionalFields = additionalFields.combineTypeSpecificProperties(typeSpecificData)
    )
  }

  def apply(id: String, typeSpecificData: TypeSpecificData): MetaData = {
    MetaData(
      id = id,
      additionalFields = ProcessAdditionalFields(None, typeSpecificData.toMap, typeSpecificData.metaDataType)
    )
  }

}

// TODO: remove metaDataType val and typeSpecificProperties def after the migration is completed
case class ProcessAdditionalFields(
    description: Option[String],
    properties: Map[String, String],
    metaDataType: String,
    showDescription: Boolean = false,
    unsafeFields: Map[String, Json] = Map.empty
) {

  def combineTypeSpecificProperties(typeSpecificData: TypeSpecificData): ProcessAdditionalFields = {
    this.copy(properties = properties ++ typeSpecificData.toMap)
  }

  def typeSpecificProperties: TypeSpecificData = {
    metaDataType match {
      case StreamMetaData.typeName          => StreamMetaData(properties)
      case LiteStreamMetaData.typeName      => LiteStreamMetaData(properties)
      case RequestResponseMetaData.typeName => RequestResponseMetaData(properties)
      case FragmentSpecificData.typeName    => FragmentSpecificData(properties)
      case _                                => throw new IllegalStateException("Unrecognized metadata type.")
    }
  }

}

object ProcessAdditionalFields {
  import io.circe.syntax._

  implicit val circeEncoder: Encoder.AsObject[ProcessAdditionalFields] = Encoder.AsObject.instance { f =>
    UnsafePrefixedFields.merge(
      JsonObject(
        "description"     -> f.description.asJson,
        "properties"      -> f.properties.asJson,
        "metaDataType"    -> f.metaDataType.asJson,
        "showDescription" -> f.showDescription.asJson,
      ),
      f.unsafeFields
    )
  }

  implicit val circeDecoder: Decoder[ProcessAdditionalFields] = Decoder.instance { c =>
    for {
      description     <- c.get[Option[String]]("description")
      properties      <- c.get[Option[Map[String, String]]]("properties")
      metaDataType    <- c.get[String]("metaDataType")
      showDescription <- c.getOrElse[Boolean]("showDescription")(false)
    } yield ProcessAdditionalFields(
      description,
      properties.getOrElse(Map.empty),
      metaDataType,
      showDescription,
      UnsafePrefixedFields.extract(c)
    )
  }

}
