package db.migration

import io.circe._
import pl.touk.nussknacker.ui.db.migration.ProcessJsonMigration

trait V1_033__RequestResponseUrlToSlug extends ProcessJsonMigration {

  override def updateProcessJson(jsonScenario: Json): Option[Json] =
    V1_033__RequestResponseUrlToSlug.migrateMetadata(jsonScenario)
}

object V1_033__RequestResponseUrlToSlug {

  private val legacyProperty = "path"
  private val newProperty = "slug"

  private[migration] def migrateMetadata(jsonProcess: Json): Option[Json] = {
    jsonProcess.hcursor.downField("metaData").downField("typeSpecificData")
      .withFocus { typeSpecificDataType =>
        typeSpecificDataType.asObject.map {
          case obj if obj("type").contains(Json.fromString("RequestResponseMetaData")) && obj.contains(legacyProperty) =>
            Json.fromJsonObject(obj
              .add(newProperty, obj(legacyProperty).get)
              .filterKeys(_ != legacyProperty))
          case _ => typeSpecificDataType
        }.getOrElse(typeSpecificDataType)
      }
  }.top

}