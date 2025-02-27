package db.migration

import io.circe._
import io.circe.Json._
import pl.touk.nussknacker.ui.db.migration.ProcessJsonMigration

trait V1_013__GroupNodesChange extends ProcessJsonMigration {

  override def updateProcessJson(jsonProcess: Json): Option[Json] =
    jsonProcess.hcursor
      .downField("metaData")
      .downField("additionalFields")
      .downField("groups")
      .withFocus(updateGroups)
      .top

  private def updateGroups(groups: Json): Json = fromValues(groups.asArray.getOrElse(List()).map { group =>
    group.arrayOrObject(group, transformGroupArray, _ => group)
  })

  private def transformGroupArray(array: Vector[Json]) =
    obj("id" -> fromString(array.map(_.asString.getOrElse("")).mkString("-")), "nodes" -> fromValues(array))

}
