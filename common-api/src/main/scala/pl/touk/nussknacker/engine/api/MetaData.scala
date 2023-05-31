package pl.touk.nussknacker.engine.api

import io.circe.generic.JsonCodec
import io.circe.generic.extras.ConfiguredJsonCodec
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.engine.api.CirceUtil._

@JsonCodec case class LayoutData(x: Long, y: Long)

// todo: MetaData should hold ProcessName as id
@ConfiguredJsonCodec case class MetaData(id: String,
                                         typeSpecificData: TypeSpecificData,
                                         additionalFields: Option[ProcessAdditionalFields] = None) {
  val isSubprocess: Boolean = typeSpecificData.isSubprocess
}

object MetaData {

  def apply(id: String, properties: ProcessAdditionalFields, propertiesType: String): MetaData = {
    val (typeSpecificProperties, fields) = properties.extractTypeSpecificData(propertiesType)
    MetaData(id, typeSpecificProperties, fields)
  }

  def apply(id: String, typeSpecificData: TypeSpecificData): MetaData = {
    MetaData(id, typeSpecificData, Some(ProcessAdditionalFields(None, typeSpecificData.toProperties)))
  }

  def apply(id: String,
            typeSpecificData: TypeSpecificData,
            additionalFields: Option[ProcessAdditionalFields]): MetaData = {
    // We prevent instantiation of MetaData with invalid properties
    // TODO: this also prevents us from unmarshalling processes with unspecified AdditionalProperties - check UIProcessMarhsallerSpec
    typeSpecificData.validateOrDie(additionalFields.getOrElse(ProcessAdditionalFields.empty))
    new MetaData(id, typeSpecificData, additionalFields)
  }
}

case class ProcessAdditionalFields(description: Option[String] = None,
                                   properties: Map[String, String]) {

  def extractTypeSpecificData(propertiesType: String): (TypeSpecificData, Option[ProcessAdditionalFields]) = {
    val typeSpecificData = TypeSpecificData(properties, propertiesType)

    val fields = properties match {
      case p if p.isEmpty && description.isEmpty => None
      case p => Some(ProcessAdditionalFields(description, p))
    }

    (typeSpecificData, fields)
  }

}

object ProcessAdditionalFields {

  val empty: ProcessAdditionalFields = ProcessAdditionalFields(None, Map.empty)

  //TODO: is this currently needed?
  private case class OptionalProcessAdditionalFields(description: Option[String],
                                                     properties: Option[Map[String, String]])

  implicit val circeDecoder: Decoder[ProcessAdditionalFields]
  = deriveConfiguredDecoder[OptionalProcessAdditionalFields].map(opp => ProcessAdditionalFields(opp.description, opp.properties.getOrElse(Map())))

  implicit val circeEncoder: Encoder[ProcessAdditionalFields] = deriveConfiguredEncoder
}
