package db.migration

import io.circe._
import pl.touk.nussknacker.ui.db.migration.ProcessJsonMigration

trait V1_054__FragmentAddComponentGroupPropertyDefinition extends ProcessJsonMigration {

  override def updateProcessJson(jsonProcess: Json): Option[Json] =
    V1_054__FragmentAddComponentGroupProperty.migrateMetadata(jsonProcess)

}

object V1_054__FragmentAddComponentGroupProperty {

  private[migration] def migrateMetadata(jsonProcess: Json): Option[Json] = {
    for {
      typeSpecificDataType <- jsonProcess.hcursor
        .downField("metaData")
        .downField("typeSpecificData")
        .downField("type")
        .as[String]
        .toOption
      if typeSpecificDataType == "FragmentSpecificData"
      withAddedComponentGroupIfMissing <- jsonProcess.hcursor
        .downField("metaData")
        .downField("additionalFields")
        .downField("properties")
        .withFocus { properties =>
          properties.hcursor.downField("componentGroup").focus.flatMap(_.asString) match {
            case Some(_) => properties
            case None    => properties.mapObject(_.add("componentGroup", Json.fromString("fragments")))
          }
        }
        .top
    } yield withAddedComponentGroupIfMissing

  }

}
