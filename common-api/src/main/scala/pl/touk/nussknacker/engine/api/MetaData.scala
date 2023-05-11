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
}

case class ProcessAdditionalFields(description: Option[String] = None,
                                   properties: Map[String, String]) {

  def extractTypeSpecificData(propertiesType: String): (TypeSpecificData, Option[ProcessAdditionalFields]) = {
    val typeSpecificData = TypeSpecificData(properties, propertiesType)
    val typeSpecificPropNamesList = typeSpecificData.toProperties.keySet

    val otherProps = properties.filterNot(k => typeSpecificPropNamesList.contains(k._1))
    val fields = otherProps match {
      case m if m.isEmpty && description.isEmpty => None
      case e => Some(ProcessAdditionalFields(description, e))
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
