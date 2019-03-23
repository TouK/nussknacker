package db.migration

import argonaut.Argonaut._
import argonaut._
import pl.touk.nussknacker.ui.db.migration.ProcessJsonMigration

trait V1_019__SourceSinkExceptionHandlerExpressionsChange extends ProcessJsonMigration {

  override def updateProcessJson(jsonProcess: Json): Option[Json] =
    V1_019__SourceSinkExceptionHandlerExpressionsChange.processJson(jsonProcess)

}

object V1_019__SourceSinkExceptionHandlerExpressionsChange {
  def processJson(jsonProcess: Json): Option[Json] =
    Option(updateSourceSinks(updateExceptionHandlers(jsonProcess)))

  private def updateSourceSinks(jsonProcess: Json) = {
    updateField(jsonProcess, "nodes", updateCanonicalNodes)
  }

  private def updateCanonicalNodes(array: Json) = {
    updateNodes(array, updateCanonicalNode)
  }

  private def updateExceptionHandlers(jsonProcess: Json) = {
    updateField(jsonProcess, "exceptionHandlerRef", updateField(_, "parameters", updateParameterList))
  }

  private def updateNodes(array: Json, fun: Json => Json) = jArray(array.arrayOrEmpty.map(fun))

  private def updateField(obj: Json, field: String, update: Json => Json): Json
  = (obj.cursor --\ field).map(_.withFocus(update).undo).getOrElse(obj)

  private def updateCanonicalNode(node: Json): Json = {
    node.field("type").flatMap(_.string).getOrElse("") match {
      case "Source" | "Sink" => updateObject(node)
      case "Switch" =>
        val updatedDefault = updateField(node, "defaultNext", updateCanonicalNodes)
        updateField(updatedDefault, "nexts", updateNodes(_, updateField(_, "nodes", updateCanonicalNodes)))
      case "Filter" =>
        updateField(node, "nextFalse", updateCanonicalNodes)
      case "Split" =>
        updateField(node, "nexts", updateNodes(_, updateCanonicalNodes))
      case "SubprocessInput" =>
        updateField(node, "outputs", outputs => {
          jObjectAssocList(outputs.objectFieldsOrEmpty.map(f => f -> updateCanonicalNodes(outputs.fieldOrEmptyArray(f))))
        })
      case _ => node
    }

  }

  private def updateObject(sourceOrSink: Json): Json = {
    (for {
      ref <- sourceOrSink.cursor --\ "ref"
      parameters <- ref --\ "parameters"
      updatedParams = parameters.withFocus(updateParameterList)
    } yield updatedParams.undo).getOrElse(sourceOrSink)
  }

  private def updateParameterList(old: Json): Json =
    jArray(old.arrayOrEmpty.map(updateParameter))

  private def updateParameter(old: Json): Json =
    jObjectFields("name" -> old.fieldOrEmptyString("name"),
      "expression" -> jObjectFields("language" -> jString("spel"), "expression" ->
        jString("'" + old.fieldOrEmptyString("value").stringOrEmpty + "'")))

}
