package pl.touk.nussknacker.engine.json.serde

import io.circe.Json
import org.everit.json.schema.Schema
import org.json.JSONTokener

//todo
class CirceJsonSerializer(jsonSchema: Schema) {
  def serialize(json: Json): Array[Byte] = {
    val jsonString = json.noSpaces
    val jsonObject = new JSONTokener(jsonString).nextValue()
    jsonSchema.validate(jsonObject)
    jsonString.getBytes
  }
}
