package db.migration

import argonaut.Argonaut._
import argonaut._
import pl.touk.esp.ui.db.migration.ProcessJsonMigration
import slick.jdbc.JdbcProfile

class V1_013__GroupNodesChange extends ProcessJsonMigration {

  override protected val profile: JdbcProfile = DefaultJdbcProfile.profile

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
