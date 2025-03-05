package db.migration

import io.circe._
import io.circe.Json._
import pl.touk.nussknacker.ui.db.migration.ProcessJsonMigration

trait V1_019__SourceSinkExceptionHandlerExpressionsChange extends ProcessJsonMigration {

  override def updateProcessJson(jsonProcess: Json): Option[Json] =
    V1_019__SourceSinkExceptionHandlerExpressionsChange.processJson(jsonProcess)

}

object V1_019__SourceSinkExceptionHandlerExpressionsChange {
  def processJson(jsonProcess: Json): Option[Json] =
    Option(updateSourceSinks(jsonProcess))

  private def updateSourceSinks(jsonProcess: Json) = {
    updateField(jsonProcess, "nodes", updateCanonicalNodes)
  }

  private def updateCanonicalNodes(array: Json) = {
    updateNodes(array, updateCanonicalNode)
  }

  private def updateNodes(array: Json, fun: Json => Json) = fromValues(array.asArray.getOrElse(List()).map(fun))

  private def updateField(obj: Json, field: String, update: Json => Json): Json =
    (obj.hcursor downField field).success.flatMap(_.withFocus(update).top).getOrElse(obj)

  private def updateCanonicalNode(node: Json): Json = {
    node.hcursor.downField("type").focus.flatMap(_.asString).getOrElse("") match {
      case "Source" | "Sink" => updateObject(node)
      case "Switch" =>
        val updatedDefault = updateField(node, "defaultNext", updateCanonicalNodes)
        updateField(updatedDefault, "nexts", updateNodes(_, updateField(_, "nodes", updateCanonicalNodes)))
      case "Filter" =>
        updateField(node, "nextFalse", updateCanonicalNodes)
      case "Split" =>
        updateField(node, "nexts", updateNodes(_, updateCanonicalNodes))
      case "SubprocessInput" =>
        updateField(node, "outputs", outputs => outputs.mapObject(obj => obj.mapValues(updateCanonicalNodes)))
      case _ => node
    }

  }

  private def updateObject(sourceOrSink: Json): Json = {
    sourceOrSink.hcursor
      .downField("ref")
      .downField("parameters")
      .withFocus(updateParameterList)
      .top
      .getOrElse(sourceOrSink)
  }

  private def updateParameterList(old: Json): Json =
    fromValues(old.asArray.getOrElse(List()).map(updateParameter))

  private def updateParameter(old: Json): Json =
    obj(
      "name" -> old.hcursor.downField("name").focus.getOrElse(fromString("")),
      "expression" -> obj(
        "language" -> fromString("spel"),
        "expression" ->
          fromString("'" + old.hcursor.downField("value").focus.flatMap(_.asString).getOrElse("") + "'")
      )
    )

}
