package db.migration

import io.circe._
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, TypeSpecificData}
import pl.touk.nussknacker.ui.db.migration.ProcessJsonMigration

trait V1_031__FragmentSpecificData extends ProcessJsonMigration {

  override def updateProcessJson(jsonProcess: Json): Option[Json] =
    V1_031__FragmentSpecificData.migrateMetadata(jsonProcess)
}



object V1_031__FragmentSpecificData {

  import io.circe.syntax._

  private[migration] def migrateMetadata(jsonProcess: Json): Option[Json] = {
    jsonProcess.hcursor.downField("metaData")
      .withFocus { metadata => metadata.hcursor.downField("isSubprocess").focus.flatMap(_.asBoolean) match {
          case Some(true) => metadata.hcursor.withFocus { mj => mj.mapObject(_.+:("typeSpecificData",
            FragmentSpecificData().asInstanceOf[TypeSpecificData].asJson))}.downField("isSubprocess").delete.focus.get
          case Some(false) => metadata.hcursor.downField("isSubprocess").delete.focus.get
          case _ => metadata
        }
      }
  }.top


}
