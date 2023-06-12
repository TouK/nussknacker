package db.migration

import db.migration.V1_041__MoveTypePropertiesToGenericDefinition.migrateMetaData
import io.circe._
import pl.touk.nussknacker.engine.api.TypeSpecificData
import pl.touk.nussknacker.ui.db.migration.ProcessJsonMigration
import io.circe.syntax._

trait V1_041__MoveTypePropertiesToGenericDefinition extends ProcessJsonMigration {

  override def updateProcessJson(json: Json): Option[Json] = migrateMetaData(json)

}

object V1_041__MoveTypePropertiesToGenericDefinition {

  implicit val mapEncoder: Encoder[Map[String, String]] = Encoder.instance { map =>
    Json.fromFields(map.map { case (k, v) => k -> Json.fromString(v) })
  }

  def migrateMetaData(json: Json): Option[Json] = {

    val typeSpecificData = json.hcursor.downField("metaData").downField("typeSpecificData").as[TypeSpecificData].getOrElse(throw new IllegalStateException("Scenario lacks required TypeSpecificData."))
    val jsonTypeSpecificDataRemoved = removeTypeSpecificData(json)

    jsonTypeSpecificDataRemoved.hcursor.downField("metaData").downField("additionalFields").withFocus {
      fields =>
        fields.asObject match {
          case Some(value) => Json.fromFields(List(
            ("description", value.asJson.hcursor.downField("description").focus.getOrElse(Json.Null)),
            // check if this works
            ("properties", (value.asJson.hcursor.downField("properties").as[Map[String,String]].getOrElse(Map.empty) ++ typeSpecificData.toMap).asJson),
            ("scenarioType", Json.fromString(typeSpecificData.scenarioType))
          ))
          case None => Json.fromFields(List(
            ("description", Json.Null),
            ("properties", typeSpecificData.toMap.asJson),
            ("scenarioType", Json.fromString(typeSpecificData.scenarioType))
          ))
        }
    }.top
  }

  private def removeTypeSpecificData(json: Json): Json = {
    json.hcursor.downField("metaData").withFocus { metadata =>
      metadata.hcursor.downField("typeSpecificData").delete.top.get
    }.top.get
  }

}