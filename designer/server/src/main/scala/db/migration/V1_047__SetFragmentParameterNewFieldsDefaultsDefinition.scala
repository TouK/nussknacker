package db.migration

import io.circe.Json._
import io.circe._
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.ParameterInputMode.InputModeAny
import pl.touk.nussknacker.ui.db.migration.ProcessJsonMigration

trait V1_047__SetFragmentParameterNewFieldsDefaultsDefinition extends ProcessJsonMigration {

  override def updateProcessJson(jsonProcess: Json): Option[Json] =
    V1_047__SetFragmentParameterNewFieldsDefaultsDefinition.updateProcessJson(jsonProcess)

}

object V1_047__SetFragmentParameterNewFieldsDefaultsDefinition {

  private val fieldNameRequired     = "required"
  private val fieldNameInitialValue = "initialValue"
  private val fieldNameHintText     = "hintText"
  private val fieldNameInputConfig  = "inputConfig"

  private val defaultInputConfig: Json = Json.fromJsonObject(
    JsonObject(
      "inputMode"                     -> Json.fromString(InputModeAny.toString),
      "fixedValuesType"               -> Json.Null,
      "fixedValuesList"               -> Json.Null,
      "fixedValuesListPresetId"       -> Json.Null,
      "resolvedPresetFixedValuesList" -> Json.Null,
    )
  )

  private val defaultNewFieldValues = Map(
    fieldNameRequired     -> Json.fromBoolean(false),
    fieldNameInitialValue -> Json.Null,
    fieldNameHintText     -> Json.Null,
    fieldNameInputConfig  -> defaultInputConfig
  )

  private[migration] def updateProcessJson(jsonProcess: Json): Option[Json] = {
    val updatedNodes = updateField(jsonProcess, "nodes", updateCanonicalNodes)
    val updateAdditionalBranches =
      updateField(updatedNodes, "additionalBranches", array => updateNodes(array, updateCanonicalNodes))
    Some(updateAdditionalBranches)
  }

  private def updateCanonicalNodes(array: Json): Json = {
    updateNodes(array, json => updateCanonicalNode(json))
  }

  private def updateField(obj: Json, field: String, update: Json => Json): Json = {
    (obj.hcursor downField field).success.flatMap(_.withFocus(update).top).getOrElse(obj)
  }

  private def setDefaultIfAbsent(obj: JsonObject, fieldName: String): JsonObject =
    if (!obj.contains(fieldName)) {
      obj.add(fieldName, defaultNewFieldValues(fieldName))
    } else {
      obj
    }

  private def updateFragmentParameters(array: Json): Json =
    fromValues(array.asArray.getOrElse(List()).map { a =>
      Json.fromJsonObject(
        List(fieldNameRequired, fieldNameInitialValue, fieldNameHintText, fieldNameInputConfig)
          .foldLeft(a.asObject.get)((acc, fieldName) => setDefaultIfAbsent(acc, fieldName))
      )
    })

  private def updateNodes(array: Json, fun: Json => Json) = fromValues(array.asArray.getOrElse(List()).map(fun))

  private def updateCanonicalNode(node: Json): Json = {
    node.hcursor.downField("type").focus.flatMap(_.asString).getOrElse("") match {
      case "FragmentInputDefinition" => updateField(node, "parameters", updateFragmentParameters)
      case _                         => node
    }
  }

}
