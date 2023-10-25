package pl.touk.nussknacker.engine.json.swagger.extractor

import com.github.benmanes.caffeine.cache.{CacheLoader, Caffeine, LoadingCache}
import io.circe.syntax.EncoderOps
import io.circe.{Json, JsonObject}
import pl.touk.nussknacker.engine.json.swagger._
import pl.touk.nussknacker.engine.json.swagger.extractor.JsonToNuStruct.JsonToObjectError

import java.{util => ju}
import java.util.stream.Collectors.toList
import scala.util.Try
import cats.implicits._

import java.util.concurrent.CompletionException
import java.util.stream.Collectors

final case class LazyJsonTypedMap(jsonObject: JsonObject, definition: SwaggerObject, path: String = "")
    extends java.util.AbstractMap[String, Any]
    with java.util.Map[String, Any] {

  import LazyJsonTypedMap._
  import scala.jdk.CollectionConverters._

  private val loader: CacheLoader[String, Option[Any]] = key => Some(extractValue(key))
  private val cache: LoadingCache[String, Option[Any]] = Caffeine.newBuilder.build(loader)
  private val definedFields: List[String] = jsonObject.keys.filter(keyStringSwaggerType(_).isDefined).toList

  override def remove(key: Any): Any                   = ???
  override def putAll(m: ju.Map[_ <: String, _]): Unit = ???
  override def clear(): Unit                           = ???
  override def put(key: String, value: Any): Any       = ???

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
    entrySet().stream().anyMatch(_.getValue == value)
  }

  override def keySet(): ju.Set[String] = extractException {
    jsonObject.filterKeys(definedFields.contains).keys.toSet.asJava
  }

  override def values(): ju.Collection[Any] = extractException {
    entrySet().stream().map[Any](_.getValue).collect(toList())
  }

  override def entrySet(): ju.Set[ju.Map.Entry[String, Any]] = extractException {
    cache
      .getAll(definedFields.asJava)
      .entrySet()
      .stream
      .map[ju.Map.Entry[String, Any]] { entry =>
        new ju.AbstractMap.SimpleEntry(entry.getKey, entry.getValue.orNull)
      }
      .collect(Collectors.toSet[ju.Map.Entry[String, Any]])
  }

  override def get(key: Any): Any = extractException {
    cache.get(key.asInstanceOf[String]).orNull
  }

  override def toString: String = cache.getAll(definedFields.asJava).asScala.toString()

  private def extractValue(keyString: String): Any =
    keyStringSwaggerType(keyString) match {
      case Some(swaggerType) =>
        JsonToNuStruct(jsonObject(keyString).getOrElse(Json.Null), swaggerType, addPath(keyString))
      case None => JsonToObjectError(jsonObject.asJson, definition, path)
    }

  private def keyStringSwaggerType(keyString: String): Option[SwaggerTyped] =
    definition.elementType.get(keyString) orElse patternPropertyOption(keyString).map(
      _.propertyType
    ) orElse additionalPropertyOption(definition.additionalProperties)

  private def patternPropertyOption(keyString: String) = definition.patternProperties
    .find(_.testPropertyName(keyString))

  private def additionalPropertyOption(additionalProperties: AdditionalProperties): Option[SwaggerTyped] =
    additionalProperties match {
      case AdditionalPropertiesEnabled(swaggerType) => Some(swaggerType)
      case _                                        => None
    }

  private def addPath(next: String): String = if (path.isEmpty) next else s"$path.$next"

}

object LazyJsonTypedMap {

  def apply(jsonObject: JsonObject, definition: SwaggerObject, path: String = ""): LazyJsonTypedMap = {
    new LazyJsonTypedMap(jsonObject, definition, path)
  }

  private def extractException[A](expression: => A) = Try(expression).adaptErr { case ex: CompletionException =>
    ex.getCause
  }.get

}
