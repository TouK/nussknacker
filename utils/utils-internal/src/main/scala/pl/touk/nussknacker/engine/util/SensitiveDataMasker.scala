package pl.touk.nussknacker.engine.util

import io.circe.Json
import io.circe.Json.{fromJsonObject, fromString, fromValues}

object SensitiveDataMasker extends SensitiveDataMasker(2) {
  def placeholder = "****"
}

class SensitiveDataMasker(visiblePartSize: Int) {

  def mask(value: String): String = {
    val visibleRightPartSize = Math.min(Math.max(value.length - visiblePartSize, 0), visiblePartSize)
    value.take(visiblePartSize) + ("*" * (value.length - visiblePartSize * 2)) + value.takeRight(visibleRightPartSize)
  }

  implicit class JsonMasker(json: Json) {

    def masked: Json = maskJson(json)

    private def maskJson(j: Json): Json = {
      j.fold(
        jsonNull = fromString(SensitiveDataMasker.placeholder),
        jsonBoolean = _ => fromString(SensitiveDataMasker.placeholder),
        jsonNumber = _ => fromString(SensitiveDataMasker.placeholder),
        jsonString = str => fromString(mask(str)),
        jsonArray = _ => fromValues(Iterable.empty),
        jsonObject = obj => fromJsonObject(obj.mapValues(maskJson)))
    }

  }

}
