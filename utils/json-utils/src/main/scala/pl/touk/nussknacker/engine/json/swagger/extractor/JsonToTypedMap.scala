package pl.touk.nussknacker.engine.json.swagger.extractor

import io.circe.{Json, JsonNumber, JsonObject}
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.json.swagger._
import pl.touk.nussknacker.engine.json.swagger.parser.PropertyName

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, OffsetTime, ZonedDateTime}
import scala.util.Try

// TODO: Validated
object JsonToTypedMap {

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

      def notNullOrRequired(fieldName: String, value: Any, required: Set[PropertyName]): Boolean = {
        value != null || required.contains(fieldName)
      }

      extract[JsonObject](
        _.asObject,
        jo => TypedMap(
          elementType.map {
            case (jsonField, jsonDef) => jsonField -> jo(jsonField).map(JsonToTypedMap(_, jsonDef)).getOrElse(nullOrError(jsonField))
          }
            .filter(e => notNullOrRequired(e._1, e._2, required))
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
      case SwaggerTime =>
        extract(_.asString, parseTime)
      case SwaggerDate =>
        extract(_.asString, parseDate)
      case SwaggerDouble =>
        extract[JsonNumber](_.asNumber, n => double2Double(n.toDouble))
      case SwaggerBigDecimal =>
        extract[JsonNumber](_.asNumber, _.toBigDecimal.map(_.bigDecimal).orNull)
      case SwaggerArray(elementType) =>
        extract[Vector[Json]](_.asArray, _.map(JsonToTypedMap(_, elementType)).asJava)
      case SwaggerObject(elementType, required) =>
        extractObject(elementType, required)
      case u@SwaggerUnion(types) => types.foldLeft[Option[AnyRef]](None)((acc, i) =>
        if (acc.isEmpty) Try(apply(json, i)).toOption else acc).getOrElse(throw JsonToObjectError(json, u))
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
