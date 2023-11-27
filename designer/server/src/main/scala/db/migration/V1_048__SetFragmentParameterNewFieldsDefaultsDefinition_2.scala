package db.migration

import io.circe.Json._
import io.circe._
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.ParameterInputMode.InputModeAny
import pl.touk.nussknacker.ui.db.migration.ProcessJsonMigration

// TODO after all PRs expanding FragmentParameter are merged, these migrations can be combined into one
trait V1_048__SetFragmentParameterNewFieldsDefaultsDefinition_2 extends ProcessJsonMigration {

  override def updateProcessJson(jsonProcess: Json): Option[Json] =
    V1_048__SetFragmentParameterNewFieldsDefaultsDefinition_2.updateProcessJson(jsonProcess)

}

object V1_048__SetFragmentParameterNewFieldsDefaultsDefinition_2 {

  val fieldNameFixedValuesType               = "fixedValuesType"
  val fieldNameFixedValuesListPresetId       = "fixedValuesListPresetId"
  val fieldNameResolvedPresetFixedValuesList = "resolvedPresetFixedValuesList"

  private val defaultNewFieldValues = Map(
    fieldNameFixedValuesType               -> Json.Null,
    fieldNameFixedValuesListPresetId       -> Json.Null,
    fieldNameResolvedPresetFixedValuesList -> Json.Null,
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
      Json.fromJsonObject {
        val updatedInputConfig =
          List(fieldNameFixedValuesType, fieldNameFixedValuesListPresetId, fieldNameResolvedPresetFixedValuesList)
            .foldLeft(a.asObject.get("inputConfig").get.asObject.get)((acc, fieldName) =>
              setDefaultIfAbsent(acc, fieldName)
            )
        a.asObject.get.add("inputConfig", Json.fromJsonObject(updatedInputConfig))
      }
    })

  private def updateNodes(array: Json, fun: Json => Json) = fromValues(array.asArray.getOrElse(List()).map(fun))

  private def updateCanonicalNode(node: Json): Json = {
    node.hcursor.downField("type").focus.flatMap(_.asString).getOrElse("") match {
      case "FragmentInputDefinition" => updateField(node, "parameters", updateFragmentParameters)
      case _                         => node
    }
  }

}
