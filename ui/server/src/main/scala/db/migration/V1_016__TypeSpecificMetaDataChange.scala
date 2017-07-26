package db.migration

import argonaut.Argonaut._
import argonaut._
import pl.touk.nussknacker.ui.db.migration.ProcessJsonMigration
import slick.jdbc.JdbcProfile

class V1_016__TypeSpecificMetaDataChange extends ProcessJsonMigration {

  override protected val profile: JdbcProfile = DefaultJdbcProfile.profile

  override def updateProcessJson(jsonProcess: Json): Option[Json] = V1_016__TypeSpecificMetaDataChange.updateMetaData(jsonProcess)
}


object V1_016__TypeSpecificMetaDataChange {
  private[migration] def updateMetaData(jsonProcess: Json): Option[Json] = for {
    meta <- jsonProcess.cursor --\ "metaData"
    parallelism = meta --\ "parallelism"
    deletedParallelism = parallelism.flatMap(_.delete).getOrElse(meta)
    splitToDisk = deletedParallelism --\ "splitStateToDisk"
    deletedSplitToDisk = splitToDisk.flatMap(_.delete).getOrElse(deletedParallelism)
    withData = deletedSplitToDisk.withFocus(_.withObject(_ :+ ("typeSpecificData", streamMetaData(parallelism.map(_.focus), splitToDisk.map(_.focus)))))
  } yield withData.undo

  private def streamMetaData(parallelism: Option[Json], splitToDisk: Option[Json]) : Json = {
    //so far we don't have production Request/Response processes ;)
    val list = List("type" -> jString("StreamMetaData")) ++ parallelism.map("parallelism" -> _).toList ++ splitToDisk.map("splitStateToDisk" -> _).toList
    jObjectFields(list: _*)
  }



}


