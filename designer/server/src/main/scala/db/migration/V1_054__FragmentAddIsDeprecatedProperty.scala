package db.migration

import io.circe._
import pl.touk.nussknacker.ui.db.migration.ProcessJsonMigration

trait V1_054__FragmentAddIsDeprecatedPropertyDefinition extends ProcessJsonMigration {

  override def updateProcessJson(jsonProcess: Json): Option[Json] =
    V1_054__FragmentAddIsDeprecatedProperty.migrateMetadata(jsonProcess)
}

object V1_054__FragmentAddIsDeprecatedProperty {

  import io.circe.syntax._

  private[migration] def migrateMetadata(jsonProcess: Json): Option[Json] = {
    jsonProcess.hcursor
      .downField("metaData")
      .downField("additionalFields")
      .downField("properties")
      .withFocus { properties =>
        properties.hcursor.downField("isDeprecated").focus.flatMap(_.asBoolean) match {
          case Some(_) => properties
          case None    => properties.mapObject(_.add("isDeprecated", Json.fromBoolean(false)))
        }
      }
      .top
  }

}
