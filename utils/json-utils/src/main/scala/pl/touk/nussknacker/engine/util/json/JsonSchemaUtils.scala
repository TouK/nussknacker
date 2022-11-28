package pl.touk.nussknacker.engine.util.json

import io.circe.{Json, JsonNumber, JsonObject}
import org.json.{JSONArray, JSONObject, JSONTokener}
import io.circe.Json._

object JsonSchemaUtils {

  import collection.JavaConverters._

  private val CirceJsonFolder: Json.Folder[Object] = new Json.Folder[Object] {
    def onNull: Object = JSONObject.NULL
    def onBoolean(value: Boolean): Object = Predef.boolean2Boolean(value)
    def onString(value: String): Object = value
    def onNumber(value: JsonNumber): Object = new JSONTokener(value.toString).nextValue
    def onArray(value: Vector[Json]): Object = new JSONArray(value.map(_.foldWith(this)).toArray)
    def onObject(value: JsonObject): Object = {
      val map = value.toMap.mapValues(_.foldWith(this)).asJava
      new JSONObject(map)
    }
  }

  def circeToJson(value: Json): Object = value.foldWith(CirceJsonFolder)

  def jsonToCirce(json: Object): Json = json match {
    case a: java.lang.Boolean => fromBoolean(a)
    case a: java.math.BigInteger => fromBigInt(a)
    case a: java.math.BigDecimal => fromBigDecimal(a)
    case a: java.lang.Double => fromBigDecimal(BigDecimal.valueOf(a))
    case a: java.lang.Integer => fromInt(a)
    case a: java.lang.Long => fromLong(a)
    case a: java.lang.String => fromString(a)
    case a: JSONArray => fromValues(a.asScala.map(jsonToCirce))
    case a if a == JSONObject.NULL => Null
    case a: JSONObject => fromJsonObject(JsonObject.fromIterable(a.keys().asScala.map(name => (name, jsonToCirce(a.get(name)))).toIterable))
    //should not happen, based on JSONTokener.nextValue docs
    case a => throw new IllegalArgumentException(s"Should not happen, JSON: ${a.getClass}")
  }

}
