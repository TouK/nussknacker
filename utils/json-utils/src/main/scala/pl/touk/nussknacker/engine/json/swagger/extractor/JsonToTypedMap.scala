package pl.touk.nussknacker.engine.json.swagger.extractor

import io.circe.{Json, JsonNumber, JsonObject}
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.json.swagger._
import pl.touk.nussknacker.engine.json.swagger.parser.PropertyName

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, OffsetTime, ZonedDateTime}
import scala.util.Try

// TODO: Validated
// TODO: Missing filling fields by defaults?
object JsonToTypedMap {

  import scala.collection.JavaConverters._

  case class JsonToObjectError(json: Json, definition: SwaggerTyped, path: String)
    extends Exception(s"JSON returned by service has invalid type at $path. Expected: $definition. Returned json: $json")

  def apply(json: Json, definition: SwaggerTyped, path: String = ""): AnyRef = {

    def extract[A](fun: Json => Option[A], trans: A => AnyRef = identity[AnyRef] _): AnyRef =
      fun(json).map(trans).getOrElse(throw JsonToObjectError(json, definition, path))

    def addPath(next: String) = if (path.isEmpty) next else s"$path.$next"

    def extractObject(elementType: Map[PropertyName, SwaggerTyped], required: Set[PropertyName]): AnyRef = {
      def noneOrError(jsonField: PropertyName): Option[AnyRef] = {
        if (required.contains(jsonField)) throw JsonToObjectError(json, definition, addPath(jsonField))
        else None
      }

      extract[JsonObject](
        _.asObject,
        jo => TypedMap(
          elementType
            .map { case (jsonField, jsonDef) =>
              jsonField -> jo(jsonField).map(JsonToTypedMap(_, jsonDef, addPath(jsonField))).orElse(noneOrError(jsonField))
            }.collect {
              case (key, Some(value)) => key -> value
            }
            .filter{ case (key, _) => jo.contains(key) }
        )
      )
    }

    definition match {
      case _ if json.isNull =>
        null
      case SwaggerString =>
        extract(_.asString)
      case SwaggerEnum(_) =>
        extract(_.asString)
      case SwaggerBool =>
        extract(_.asBoolean, boolean2Boolean)
      case SwaggerLong =>
        //FIXME: to ok?
        extract[JsonNumber](_.asNumber, n => long2Long(n.toDouble.toLong))
      case SwaggerDateTime =>
        extract(_.asString, parseDateTime)
      case SwaggerTime =>
        extract(_.asString, parseTime)
      case SwaggerDate =>
        extract(_.asString, parseDate)
      case SwaggerDouble =>
        extract[JsonNumber](_.asNumber, n => double2Double(n.toDouble))
      case SwaggerBigDecimal =>
        extract[JsonNumber](_.asNumber, _.toBigDecimal.map(_.bigDecimal).orNull)
      case SwaggerArray(elementType) =>
        extract[Vector[Json]](_.asArray, _.zipWithIndex.map { case (el, idx) => JsonToTypedMap(el, elementType, s"$path[$idx]") }.asJava)
      case SwaggerObject(elementType, required) =>
        extractObject(elementType, required)
      case u@SwaggerUnion(types) => types.view.flatMap(aType => Try(apply(json, aType)).toOption)
        .headOption.getOrElse(throw JsonToObjectError(json, u, path))
    }
  }

  //we want to accept empty string - just in case...
  //some of the implementations allow timezone to be optional, we are strict
  //see e.g. https://github.com/OAI/OpenAPI-Specification/issues/1498#issuecomment-369680369
  private def parseDateTime(dateTime: String): ZonedDateTime = {
    Option(dateTime).filterNot(_.isEmpty).map { dateTime =>
      ZonedDateTime.parse(dateTime, DateTimeFormatter.ISO_DATE_TIME)
    }.orNull
  }

  private def parseDate(date: String): LocalDate = {
    Option(date).filterNot(_.isEmpty).map { date =>
      LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
    }.orNull
  }

  private def parseTime(time: String): OffsetTime = {
    Option(time).filterNot(_.isEmpty).map { time =>
      OffsetTime.parse(time, DateTimeFormatter.ISO_OFFSET_TIME)
    }.orNull
  }
}
