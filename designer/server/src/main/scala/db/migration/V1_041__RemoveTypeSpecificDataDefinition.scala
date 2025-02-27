package db.migration

import com.typesafe.scalalogging.LazyLogging
import db.migration.V1_041__RemoveTypeSpecificDataDefinition.migrateMetaData
import io.circe._
import io.circe.syntax._
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.db.migration.ProcessJsonMigration

trait V1_041__RemoveTypeSpecificDataDefinition extends ProcessJsonMigration with LazyLogging {

  override def updateProcessJson(json: Json): Option[Json] = {
    migrateMetaData(json) match {
      case Left(failure) =>
        logger.error(s"Updating process json during migration failed: ${failure.reason}.\nProcess json: $json")
        None
      case Right(updatedJson) => Some(updatedJson)
    }
  }

}

object V1_041__RemoveTypeSpecificDataDefinition {

  def migrateMetaData(json: Json): Either[DecodingFailure, Json] = {
    json.as[CanonicalProcess] match {
      case Left(failure)         => Left(failure)
      case Right(updatedProcess) => Right(updatedProcess.asJson)
    }
  }

}
