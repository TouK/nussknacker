package db.migration

import io.circe._
import io.circe.Json._
import pl.touk.nussknacker.ui.db.migration.ProcessJsonMigration

trait V1_016__TypeSpecificMetaDataChange extends ProcessJsonMigration {
  override def updateProcessJson(jsonProcess: Json): Option[Json] =
    V1_016__TypeSpecificMetaDataChange.updateMetaData(jsonProcess)
}

object V1_016__TypeSpecificMetaDataChange {

  private[migration] def updateMetaData(jsonProcess: Json): Option[Json] = {
    val meta        = jsonProcess.hcursor.downField("metaData")
    val parallelism = meta.downField("parallelism")
    val splitToDisk = parallelism.delete.success.getOrElse(meta).downField("splitStateToDisk")
    val withTypeSpecificData = splitToDisk.delete.success
      .getOrElse(meta)
      .withFocus(_.mapObject(_.+:("typeSpecificData", streamMetaData(parallelism.focus, splitToDisk.focus))))
    withTypeSpecificData.top
  }

  private def streamMetaData(parallelism: Option[Json], splitToDisk: Option[Json]): Json = {
    // so far we don't have production Request/Response processes ;)
    fromFields(
      List("type" -> fromString("StreamMetaData")) ++ parallelism.map("parallelism" -> _).toList ++ splitToDisk
        .map("splitStateToDisk" -> _)
        .toList
    )
  }

}
