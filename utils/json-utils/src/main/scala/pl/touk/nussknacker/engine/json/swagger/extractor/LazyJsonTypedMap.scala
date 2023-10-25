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
  private val definedFields: List[String] =
    jsonObject.keys.filter(SwaggerObject.fieldSwaggerTypeByKey(definition, _).isDefined).toList

  override def remove(key: Any): Any                   = immutableException
  override def putAll(m: ju.Map[_ <: String, _]): Unit = immutableException
  override def clear(): Unit                           = immutableException
  override def put(key: String, value: Any): Any       = immutableException

  override def size(): Int = definedFields.size

  override def isEmpty(): Boolean =
    definedFields.isEmpty

  override def containsKey(key: Any): Boolean =
    definedFields.contains(key)

  override def containsValue(value: Any): Boolean = withCauseExtraction {
    entrySet().stream().anyMatch(_.getValue == value)
  }

  override def keySet(): ju.Set[String] =
    jsonObject.filterKeys(definedFields.contains).keys.toSet.asJava

  override def values(): ju.Collection[Any] = withCauseExtraction {
    entrySet().stream().map[Any](_.getValue).collect(toList())
  }

  override def entrySet(): ju.Set[ju.Map.Entry[String, Any]] = withCauseExtraction {
    cache
      .getAll(definedFields.asJava)
      .entrySet()
      .stream
      .map[ju.Map.Entry[String, Any]] { entry =>
        new ju.AbstractMap.SimpleEntry(entry.getKey, entry.getValue.orNull)
      }
      .collect(Collectors.toSet[ju.Map.Entry[String, Any]])
  }

  override def get(key: Any): Any = withCauseExtraction {
    cache.get(key.asInstanceOf[String]).orNull
  }

  override def toString: String = cache.getAll(definedFields.asJava).asScala.toString()

  private def extractValue(keyString: String): Any =
    SwaggerObject.fieldSwaggerTypeByKey(definition, keyString) match {
      case Some(swaggerType) =>
        JsonToNuStruct(jsonObject(keyString).getOrElse(Json.Null), swaggerType, addPath(keyString))
      case None => JsonToObjectError(jsonObject.asJson, definition, path)
    }

  private def addPath(next: String): String = if (path.isEmpty) next else s"$path.$next"

  private def immutableException = throw new UnsupportedOperationException("LazyJsonTypedMap is immutable")
}

object LazyJsonTypedMap {

  def apply(jsonObject: JsonObject, definition: SwaggerObject, path: String = ""): LazyJsonTypedMap = {
    new LazyJsonTypedMap(jsonObject, definition, path)
  }

  private def withCauseExtraction[A](expression: => A) = Try(expression).adaptErr { case ex: CompletionException =>
    ex.getCause
  }.get

}
