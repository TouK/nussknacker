package pl.touk.nussknacker.engine.json.swagger.extractor

import io.circe.{Json, JsonNumber, JsonObject}
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.json.swagger._
import pl.touk.nussknacker.engine.util.json.JsonUtils.jsonToAny

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, OffsetTime, ZonedDateTime}
import scala.util.Try

// TODO: Validated
object FromJsonDecoder {

  import scala.jdk.CollectionConverters._

  case class JsonToObjectError(json: Json, definition: SwaggerTyped, path: String)
      extends Exception(
        s"JSON returned by service has invalid type at $path. Expected: $definition. Returned json: $json"
      )

  // TODO: remove flow control using exceptions
  def decode(json: Json, definition: SwaggerTyped, path: String = ""): AnyRef = {

    def extract[A](fun: Json => Option[A], trans: A => AnyRef = identity[AnyRef] _): AnyRef =
      fun(json).map(trans).getOrElse(throw JsonToObjectError(json, definition, path))

    def addPath(next: String): String = if (path.isEmpty) next else s"$path.$next"

    def extractObject(obj: SwaggerObject): AnyRef = {
      object KeyMatchingPatternSchema {
        def unapply(keyValue: (String, Json)): Option[SwaggerTyped] = {
          val (propertyName, _) = keyValue
          obj.patternProperties.find(_.testPropertyName(propertyName)).map(_.propertyType)
        }
      }

      extract[JsonObject](
        _.asObject,
        jo =>
          TypedMap(
            jo.toMap.collect {
              case (key, value) if obj.elementType.contains(key) =>
                key -> FromJsonDecoder.decode(value, obj.elementType(key), addPath(key))
              case keyValue @ KeyMatchingPatternSchema(patternPropertySchema) =>
                val (key, value) = keyValue
                key -> FromJsonDecoder.decode(value, patternPropertySchema, addPath(key))
              case (key, value) if obj.additionalProperties != AdditionalPropertiesDisabled =>
                obj.additionalProperties match {
                  case add: AdditionalPropertiesEnabled =>
                    key -> FromJsonDecoder.decode(value, add.value, addPath(key))
                  case _ =>
                    key -> jsonToAny(value)
                }
            }
          )
      )
    }

    // we handle null here to enable pattern matching exhaustive check
    if (json.isNull) {
      null
    } else {
      definition match {
        case SwaggerString =>
          extract(_.asString)
        case SwaggerEnum(_) =>
          extract[AnyRef](j => Option(jsonToAny(j).asInstanceOf[AnyRef]))
        case SwaggerBool =>
          extract(_.asBoolean, boolean2Boolean)
        case SwaggerInteger =>
          extract[JsonNumber](_.asNumber, n => int2Integer(n.toDouble.toInt))
        case SwaggerLong =>
          // FIXME: to ok?
          extract[JsonNumber](_.asNumber, n => long2Long(n.toDouble.toLong))
        case SwaggerBigInteger =>
          extract[JsonNumber](_.asNumber, _.toBigInt.map(_.bigInteger).orNull)
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
          extract[Vector[Json]](
            _.asArray,
            _.zipWithIndex.map { case (el, idx) => FromJsonDecoder.decode(el, elementType, s"$path[$idx]") }.asJava
          )
        case obj: SwaggerObject => extractObject(obj)
        case u @ SwaggerUnion(types) =>
          types.view
            .flatMap(aType => Try(decode(json, aType)).toOption)
            .headOption
            .getOrElse(throw JsonToObjectError(json, u, path))
        case SwaggerAny => extract[AnyRef](j => Option(jsonToAny(j).asInstanceOf[AnyRef]))
        // should not happen as we handle null above
        case SwaggerNull => throw JsonToObjectError(json, definition, path)
      }
    }

  }

  // we want to accept empty string - just in case...
  // some of the implementations allow timezone to be optional, we are strict
  // see e.g. https://github.com/OAI/OpenAPI-Specification/issues/1498#issuecomment-369680369
  private def parseDateTime(dateTime: String): ZonedDateTime = {
    Option(dateTime)
      .filterNot(_.isEmpty)
      .map { dateTime =>
        ZonedDateTime.parse(dateTime, DateTimeFormatter.ISO_DATE_TIME)
      }
      .orNull
  }

  private def parseDate(date: String): LocalDate = {
    Option(date)
      .filterNot(_.isEmpty)
      .map { date =>
        LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
      }
      .orNull
  }

  private def parseTime(time: String): OffsetTime = {
    Option(time)
      .filterNot(_.isEmpty)
      .map { time =>
        OffsetTime.parse(time, DateTimeFormatter.ISO_OFFSET_TIME)
      }
      .orNull
  }

}
