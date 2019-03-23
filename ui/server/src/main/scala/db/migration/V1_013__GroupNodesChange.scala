package db.migration

import argonaut.Argonaut._
import argonaut._
import pl.touk.nussknacker.ui.db.migration.ProcessJsonMigration

trait V1_013__GroupNodesChange extends ProcessJsonMigration {
  
  override def updateProcessJson(jsonProcess: Json): Option[Json] = for {
    meta <- jsonProcess.cursor --\ "metaData"
    fields <- meta --\ "additionalFields"
    groups <- fields --\ "groups"
    updated = groups.withFocus(updateGroups)
  } yield updated.undo

  private def updateGroups(groups: Json): Json = jArray(groups.arrayOrEmpty.map { group =>
    group.arrayOrObject(group, transformGroupArray, _ => group)
  })

  private def transformGroupArray(array: JsonArray) =
    jObjectFields("id" -> jString(array.map(_.stringOrEmpty).mkString("-")), "nodes" -> jArray(array))

}
