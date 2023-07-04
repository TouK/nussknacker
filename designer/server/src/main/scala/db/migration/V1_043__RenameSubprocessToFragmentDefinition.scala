package db.migration

import io.circe.Json._
import io.circe._
import pl.touk.nussknacker.ui.db.migration.ProcessJsonMigration

trait V1_043__RenameSubprocessToFragmentDefinition extends ProcessJsonMigration {

  override def updateProcessJson(jsonProcess: Json): Option[Json] =
    V1_043__RenameSubprocessToFragmentDefinition.updateProcessJson(jsonProcess)

}

object V1_043__RenameSubprocessToFragmentDefinition {

  private val legacyProperty = "subprocessParams"
  private val newProperty = "fragmentParams"

  private[migration] def updateProcessJson(jsonProcess: Json): Option[Json] = {
    val updatedMetadata = migrateMetadata(jsonProcess).getOrElse(jsonProcess)
    val updateCanonicalNodes = (array: Json) => updateNodes(array, updateCanonicalNode)
    updateField(updatedMetadata, "nodes", updateCanonicalNodes)
  }

  private def migrateMetadata(jsonProcess: Json): Option[Json] = {
    jsonProcess.hcursor.downField("metaData").downField("additionalFields").downField("metaDataType")
      .withFocus { typeSpecificDataType =>
        typeSpecificDataType.asString match {
          case Some("SubprocessSpecificData") => Json.fromString("FragmentSpecificData")
          case _ => typeSpecificDataType
        }
      }
  }.top

  private def updateField(obj: Json, field: String, update: Json => Json): Option[Json] = {
    (obj.hcursor downField field).success.flatMap(_.withFocus(update).top)
  }

  private def updateNodes(array: Json, fun: Json => Json) = fromValues(array.asArray.getOrElse(List()).map(fun))

  private def updateCanonicalNode(node: Json): Json = {
    updatePropertyKey(node).hcursor.downField("type")
      .withFocus { nodeType =>
        nodeType.asString match {
          case Some("SubprocessInput") => Json.fromString("FragmentInput")
          case Some("SubprocessOutput") => Json.fromString("FragmentOutput")
          case Some("SubprocessOutputDefinition") => Json.fromString("FragmentOutputDefinition")
          case Some("SubprocessInputDefinition") => Json.fromString("FragmentInputDefinition")
          case _ => nodeType
        }
      }
      .top
      .getOrElse(node)
  }

  private def updatePropertyKey(node: Json): Json = {
    node.hcursor.withFocus(json => {
      json.asObject.map {
        case obj if obj.contains(legacyProperty) =>
          Json.fromJsonObject(obj
            .add(newProperty, obj(legacyProperty).get)
            .filterKeys(_ != legacyProperty))
        case _ => json
      }.getOrElse(json)
    }).top.getOrElse(node)
  }

}