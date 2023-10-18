package pl.touk.nussknacker.engine.json.swagger.extractor

import com.github.benmanes.caffeine.cache.{CacheLoader, Caffeine, LoadingCache}
import io.circe.syntax.EncoderOps
import io.circe.{Json, JsonNumber, JsonObject}
import pl.touk.nussknacker.engine.json.swagger._
import pl.touk.nussknacker.engine.util.json.JsonUtils.jsonToAny

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, OffsetTime, ZonedDateTime}
import pl.touk.nussknacker.engine.json.swagger.extractor.JsonToNuStruct.JsonToObjectError

import java.{util => ju}
import java.util.stream.Collectors.toList
import scala.util.Try
import cats.implicits._

import java.util.concurrent.CompletionException

class ShallowTypedMap(jsonObject: JsonObject, definition: SwaggerObject, path: String)
    extends java.util.Map[String, Any] {

  import ShallowTypedMap._
  import scala.jdk.CollectionConverters._

  private val loader: CacheLoader[String, Option[Any]] = key => Some(extractValue(key))
  private val cache: LoadingCache[String, Option[Any]] = Caffeine.newBuilder.build(loader)
  private val definedFields: Seq[String]               = jsonObject.keys.filter(keyStringSwaggerType(_).isDefined).toSeq

  def this() = this(JsonObject.empty, SwaggerObject(Map.empty, AdditionalPropertiesDisabled, List.empty), "")

  def extractValue(keyString: String): Any = {

    def extract[A](
        expectedType: SwaggerTyped,
        fun: Json => Option[A],
        trans: A => AnyRef = identity[AnyRef] _
    ): AnyRef =
      jsonObject(keyString) match {
        case None => null
        case Some(extractedJson) =>
          fun(extractedJson)
            .map(trans)
            .getOrElse(
              throw JsonToObjectError(extractedJson, expectedType, addPath(keyString))
            )
      }

    keyStringSwaggerType(keyString) match {
      case Some(SwaggerString) =>
        extract(SwaggerString, _.asString)
      case Some(swaggerType @ SwaggerEnum(_)) =>
        extract[AnyRef](swaggerType, j => Option(jsonToAny(j).asInstanceOf[AnyRef]))
      case Some(SwaggerBool) =>
        extract(SwaggerBool, _.asBoolean, boolean2Boolean)
      case Some(SwaggerInteger) =>
        extract[JsonNumber](SwaggerInteger, _.asNumber, n => int2Integer(n.toDouble.toInt))
      case Some(SwaggerLong) =>
        extract[JsonNumber](SwaggerLong, _.asNumber, n => long2Long(n.toDouble.toLong))
      case Some(SwaggerBigInteger) =>
        extract[JsonNumber](SwaggerBigInteger, _.asNumber, _.toBigInt.map(_.bigInteger).orNull)
      case Some(SwaggerDateTime) =>
        extract(SwaggerDateTime, _.asString, parseDateTime)
      case Some(SwaggerTime) =>
        extract(SwaggerTime, _.asString, parseTime)
      case Some(SwaggerDate) =>
        extract(SwaggerDate, _.asString, parseDate)
      case Some(SwaggerDouble) =>
        extract[JsonNumber](SwaggerDouble, _.asNumber, n => double2Double(n.toDouble))
      case Some(SwaggerBigDecimal) =>
        extract[JsonNumber](SwaggerBigDecimal, _.asNumber, _.toBigDecimal.map(_.bigDecimal).orNull)
      case Some(swaggerType @ SwaggerArray(elementType)) =>
        extract[Vector[Json]](
          swaggerType,
          _.asArray,
          _.zipWithIndex.map { case (el, idx) => JsonToNuStruct(el, elementType, s"$keyString$path[$idx]") }.asJava
        )
      case Some(obj @ SwaggerObject(_, _, _)) =>
        ShallowTypedMap(jsonObject(keyString).get.asObject.get, obj, addPath(keyString))
      case Some(u @ SwaggerUnion(types)) =>
        types.view
          .flatMap { case aType =>
            (jsonObject(keyString) orElse Some(Json.Null)).flatMap { case json =>
              Try(JsonToNuStruct(json, aType, addPath(keyString))).toOption
            }
          }
          .headOption
          .getOrElse(throw JsonToObjectError(jsonObject.asJson, u, path))
      case Some(SwaggerAny) => extract[AnyRef](SwaggerAny, j => Option(jsonToAny(j).asInstanceOf[AnyRef]))
      // should not happen as we handle null above
      case Some(SwaggerNull) => null
      case None              => JsonToObjectError(jsonObject.asJson, definition, path)
    }

  }

  private def keyStringSwaggerType(keyString: String): Option[SwaggerTyped] =
    definition.elementType.get(keyString) orElse patternPropertyOption(keyString).map(
      _.propertyType
    ) orElse additionalProperty.lift(definition.additionalProperties)

  private def patternPropertyOption(keyString: String) = definition.patternProperties
    .find(_.testPropertyName(keyString))

  private def additionalProperty: PartialFunction[AdditionalProperties, SwaggerTyped] = {
    case AdditionalPropertiesEnabled(swaggerType) => swaggerType
  }

  private def addPath(next: String): String = if (path.isEmpty) next else s"$path.$next"

  override def size(): Int = extractException {
    jsonObject.keys.count(keyStringSwaggerType(_).isDefined)
  }

  override def isEmpty: Boolean = extractException {
    definedFields.isEmpty
  }

  override def containsKey(key: Any): Boolean =
    extractException {
      definedFields.contains(key)
    }

  override def containsValue(value: Any): Boolean = extractException {
    entrySet().stream().anyMatch { x => x.getValue == value }
  }

  override def put(key: String, value: Any): Any       = cache.put(key, Option(value))
  override def remove(key: Any): Any                   = ???
  override def putAll(m: ju.Map[_ <: String, _]): Unit = ???
  override def clear(): Unit                           = ???

  override def keySet(): ju.Set[String] = extractException {
    jsonObject.filterKeys(definedFields.contains).keys.toSet.asJava
  }

  override def values(): ju.Collection[Any] = extractException {
    entrySet().stream().map { x => x.getValue }.collect(toList())
  }

  override def entrySet(): ju.Set[ju.Map.Entry[String, Any]] = extractException {
    cache
      .getAll(definedFields.asJava)
      .asScala
      .map { case (key, value) =>
        new ju.AbstractMap.SimpleEntry[String, Any](key, value.orNull): ju.Map.Entry[String, Any]
      }
      .toSet
      .asJava
  }

  override def get(key: Any): Any = extractException {
    cache.get(key.asInstanceOf[String]).orNull
  }

  override def toString: String = cache.getAll(definedFields.asJava).asScala.toString()

  override def equals(obj: Any): Boolean =
    obj match {
      case x: ju.Map[_, _] => x.equals(this)
      case _               => super.equals(obj)
    }

}

object ShallowTypedMap {

  def apply(jsonObject: JsonObject, definition: SwaggerObject, path: String = ""): ShallowTypedMap = {
    new ShallowTypedMap(jsonObject, definition, path)
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

  private def extractException[A](expression: => A) = Try(expression).adaptErr { case ex: CompletionException =>
    ex.getCause
  }.get

}
