package pl.touk.nussknacker.openapi.extractor

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter

import io.circe.{Json, JsonNumber, JsonObject}
import pl.touk.nussknacker.openapi.{PropertyName, SwaggerArray, SwaggerBigDecimal, SwaggerBool, SwaggerDateTime, SwaggerDouble, SwaggerEnum, SwaggerLong, SwaggerObject, SwaggerString, SwaggerTyped}
import pl.touk.nussknacker.engine.api.typed.TypedMap

// TODO: Validated
object JsonToObject {
  import scala.collection.JavaConverters._

  case class JsonToObjectError(json: Json, definition: SwaggerTyped)
    extends Exception(s"JSON returned by service has invalid type. Expected: $definition. Returned json: $json")

  def apply(json: Json, definition: SwaggerTyped): AnyRef = {

    def extract[A](fun: Json => Option[A], trans: A => AnyRef = identity[AnyRef] _): AnyRef =
      fun(json).map(trans).getOrElse(throw JsonToObjectError(json, definition))


    def extractObject(elementType: Map[PropertyName, SwaggerTyped], required: Set[PropertyName]): AnyRef = {
      def nullOrError(jsonField: PropertyName): AnyRef = {
        if (required.contains(jsonField)) throw JsonToObjectError(json, definition)
        else null
      }

      extract[JsonObject](
        _.asObject,
        jo => TypedMap(
          elementType.map { case (jsonField, jsonDef) =>
            jsonField -> jo(jsonField).map(JsonToObject(_, jsonDef)).getOrElse(nullOrError(jsonField))
          }
        )
      )
    }

    definition match {
      case _ if json.isNull =>
        null
      case SwaggerString =>
        extract(_.asString)
      case SwaggerEnum(values) =>
        extract(_.asString)
      case SwaggerBool =>
        extract(_.asBoolean, boolean2Boolean)
      case SwaggerLong =>
        //FIXME: to ok?
        extract[JsonNumber](_.asNumber, n => long2Long(n.toDouble.toLong))
      case SwaggerDateTime =>
        extract(_.asString, parseDateTime)
      case SwaggerDouble =>
        extract[JsonNumber](_.asNumber, n => double2Double(n.toDouble))
      case SwaggerBigDecimal =>
        extract[JsonNumber](_.asNumber, _.toBigDecimal.map(_.bigDecimal).orNull)
      case SwaggerArray(elementType) =>
        extract[Vector[Json]](_.asArray, _.map(JsonToObject(_, elementType)).asJava)
      case SwaggerObject(elementType, required) =>
        extractObject(elementType, required)
    }
  }

  //we want to accept empty string - just in case...
  //we use LocalDateTime, because it's used in many helpers
  private def parseDateTime(dateTime: String): LocalDateTime = {
    Option(dateTime).filterNot(_.isEmpty).map { dateTime =>
      LocalDateTime.ofInstant(ZonedDateTime.parse(dateTime, DateTimeFormatter.ISO_DATE_TIME).toInstant, ZoneId.systemDefault())
    }.orNull
  }
}
