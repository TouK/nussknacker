package pl.touk.nussknacker.engine.util.json

import io.circe.{Json, JsonObject}
import io.circe.Json._
import org.json.{JSONArray, JSONObject, JSONTokener}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

import scala.collection.compat.immutable.LazyList

object JsonSchemaUtils {

  import scala.jdk.CollectionConverters._

  def circeToJson(value: Json): AnyRef = value.fold(
    jsonNull = JSONObject.NULL,
    jsonBoolean => Predef.boolean2Boolean(jsonBoolean),
    jsonNumber => new JSONTokener(jsonNumber.toString).nextValue,
    jsonString = identity[String],
    jsonArray => new JSONArray(jsonArray.map(circeToJson).toArray),
    jsonObject => new JSONObject(jsonObject.toMap.mapValuesNow(circeToJson).asJava)
  )

  def jsonToCirce(json: AnyRef): Json = json match {
    case a: java.lang.Boolean      => fromBoolean(a)
    case a: java.math.BigInteger   => fromBigInt(a)
    case a: java.math.BigDecimal   => fromBigDecimal(a)
    case a: java.lang.Double       => fromBigDecimal(BigDecimal.valueOf(a))
    case a: java.lang.Integer      => fromInt(a)
    case a: java.lang.Long         => fromLong(a)
    case a: java.lang.String       => fromString(a)
    case a: JSONArray              => fromValues(a.asScala.map(jsonToCirce))
    case a if a == JSONObject.NULL => Null
    case a: JSONObject =>
      fromJsonObject(JsonObject.fromIterable(a.keys().asScala.map(name => (name, jsonToCirce(a.get(name)))).toList))
    // should not happen, based on JSONTokener.nextValue docs
    case a => throw new IllegalArgumentException(s"Should not happen, JSON: ${a.getClass}")
  }

}
