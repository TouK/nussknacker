package db.migration

import io.circe._
import pl.touk.nussknacker.ui.db.migration.ProcessJsonMigration

trait V1_030__SpillStateToDisk extends ProcessJsonMigration {

  override def updateProcessJson(jsonProcess: Json): Option[Json] =
    V1_030__SpillStateToDisk.renameSpillStateToDisk(jsonProcess)
}

object V1_030__SpillStateToDisk {

  final val emptyAdditionalBranches = Json.fromValues(List.empty)

  private[migration] def renameSpillStateToDisk(jsonProcess: Json): Option[Json] = {
    val typeSpecificDataCursor = jsonProcess.hcursor.downField("metaData").downField("typeSpecificData")
    val updatedTypeSpecificData = typeSpecificDataCursor
      .withFocus { typeSpecificData =>
        val splitStateToDisk = typeSpecificData.hcursor.downField("splitStateToDisk").focus
        splitStateToDisk match {
          case Some(oldValue) =>
            return typeSpecificDataCursor.withFocus { json =>
              json.mapObject(_.add("spillStateToDisk", oldValue))
                .hcursor.downField("splitStateToDisk").delete.top.get
            }.top
          case None => typeSpecificData
        }
      }
    updatedTypeSpecificData.top
  }

}
