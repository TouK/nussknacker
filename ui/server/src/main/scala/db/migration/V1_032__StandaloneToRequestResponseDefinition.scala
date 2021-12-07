package db.migration

import io.circe._
import pl.touk.nussknacker.ui.db.migration.ProcessJsonMigration

trait V1_032__StandaloneToRequestResponseDefinition extends ProcessJsonMigration {

  override def updateProcessJson(jsonProcess: Json): Option[Json] =
    V1_032__StandaloneToRequestResponseDefinition.migrateMetadata(jsonProcess)
}

object V1_032__StandaloneToRequestResponseDefinition {

  private[migration] def migrateMetadata(jsonProcess: Json): Option[Json] = {
    jsonProcess.hcursor.downField("metaData").downField("typeSpecificData").downField("type")
      .withFocus { typeSpecificDataType => typeSpecificDataType.asString match {
          case Some("StandaloneMetaData") => Json.fromString("RequestResponseMetaData")
          case _ => typeSpecificDataType
        }
      }
  }.top

}
