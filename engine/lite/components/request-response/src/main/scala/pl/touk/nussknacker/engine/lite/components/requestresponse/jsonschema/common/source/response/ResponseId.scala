package pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.common.source.response

import java.util.UUID

import io.circe.Json
import io.circe.JsonObject


object ResponseId {
  private val responseId = "responseId"

  def addToJson(json: JsonObject): JsonObject = {

    (responseId, Json.fromString(uuid)) +: json
  }

  private def uuid: String = UUID.randomUUID.toString
}