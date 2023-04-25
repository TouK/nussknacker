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
    val (typeSpecificProperties, fields) = properties.extractTypedData(propertiesType)
    MetaData(id, typeSpecificProperties, Some(fields))
  }
}

case class ProcessAdditionalFields(description: Option[String],
                                   properties: Map[String, String]) {

  def extractTypedData(propertiesType: String): (TypeSpecificData, ProcessAdditionalFields) = {
    val typeSpecificData = TypeSpecificData(properties, propertiesType)
    val typeSpecificPropsList = typeSpecificData.toProperties.keySet
    val fields = ProcessAdditionalFields(description, properties.filterNot(k => typeSpecificPropsList.contains(k._1)))
    (typeSpecificData, fields)
  }

}

object ProcessAdditionalFields {

  //TODO: is this currently needed?
  private case class OptionalProcessAdditionalFields(description: Option[String],
                                                     properties: Option[Map[String, String]])

  implicit val circeDecoder: Decoder[ProcessAdditionalFields]
  = deriveConfiguredDecoder[OptionalProcessAdditionalFields].map(opp => ProcessAdditionalFields(opp.description, opp.properties.getOrElse(Map())))

  implicit val circeEncoder: Encoder[ProcessAdditionalFields] = deriveConfiguredEncoder
}
