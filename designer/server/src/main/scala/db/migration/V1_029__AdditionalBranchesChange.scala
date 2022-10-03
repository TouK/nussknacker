package db.migration

import io.circe._
import pl.touk.nussknacker.ui.db.migration.ProcessJsonMigration

trait V1_029__AdditionalBranchesChange extends ProcessJsonMigration {

  override def updateProcessJson(jsonProcess: Json): Option[Json] =
    V1_029__AdditionalBranchesChange.updateAdditionalBranches(jsonProcess)
}

object V1_029__AdditionalBranchesChange {

  final val emptyAdditionalBranches = Json.fromValues(List.empty)

  private[migration] def updateAdditionalBranches(jsonProcess: Json): Option[Json] = {
    val additionalBranches = jsonProcess.hcursor.downField("additionalBranches").focus

    additionalBranches match {
      case Some(_) =>
        jsonProcess.hcursor.downField("additionalBranches").withFocus(updateAdditionalBranchesJson).top
      case None =>
        Some(jsonProcess.mapObject(_.add("additionalBranches", emptyAdditionalBranches)))
    }
  }

  private def updateAdditionalBranchesJson(additionalBranches: Json): Json = {
    if (additionalBranches.isNull) {
      emptyAdditionalBranches
    } else {
      additionalBranches
    }
  }
}