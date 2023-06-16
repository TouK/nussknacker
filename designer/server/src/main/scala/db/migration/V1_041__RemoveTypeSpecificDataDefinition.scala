package db.migration

import db.migration.V1_041__RemoveTypeSpecificDataDefinition.migrateMetaData
import io.circe._
import pl.touk.nussknacker.ui.db.migration.ProcessJsonMigration
import io.circe.syntax._
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess

trait V1_041__RemoveTypeSpecificDataDefinition extends ProcessJsonMigration {

  override def updateProcessJson(json: Json): Option[Json] = migrateMetaData(json)

}

object V1_041__RemoveTypeSpecificDataDefinition {

  def migrateMetaData(json: Json): Option[Json] = json.as[CanonicalProcess] match {
    case Left(failed: DecodingFailure) => throw new IllegalStateException(s"Migration failed - invalid json. $failed")
    case Right(canonicalProcess: CanonicalProcess) => Some(canonicalProcess.asJson)
  }

}
