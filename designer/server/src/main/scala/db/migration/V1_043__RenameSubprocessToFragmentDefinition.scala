package db.migration

import io.circe.Json
import pl.touk.nussknacker.ui.db.migration.ProcessJsonMigration

trait V1_043__RenameSubprocessToFragmentDefinition extends ProcessJsonMigration {

  //This migration was missing "outputs" field in SubprocessInput migration so it was replaced with empty migration and fixed in another one.
  override def updateProcessJson(jsonProcess: Json): Option[Json] = None

}