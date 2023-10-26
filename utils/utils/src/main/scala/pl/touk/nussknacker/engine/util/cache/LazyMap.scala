package pl.touk.nussknacker.engine.util.cache

import cats.implicits._
import com.github.benmanes.caffeine.cache.{CacheLoader, Caffeine, LoadingCache}

import java.util.concurrent.CompletionException
import java.util.stream.Collectors
import java.util.stream.Collectors.toList
import java.{util => ju}
import scala.jdk.CollectionConverters._
import scala.util.Try

class LazyMap[A <: AnyRef, B >: Null](
    definedFields: ju.Set[A],
    extractValue: A => B
) extends ju.AbstractMap[A, B]
    with ju.Map[A, B] {

  import LazyMap._

  private val loader: CacheLoader[A, Option[B]] = key => Some(extractValue(key))
  private val cache: LoadingCache[A, Option[B]] = Caffeine.newBuilder.build(loader)

  override def remove(key: Any): B                     = throwImmutableException
  override def putAll(m: ju.Map[_ <: A, _ <: B]): Unit = throwImmutableException
  override def clear(): Unit                           = throwImmutableException
  override def put(key: A, value: B): B                = throwImmutableException

  override def size(): Int = definedFields.size

  override def isEmpty(): Boolean =
    definedFields.isEmpty

  override def containsKey(key: Any): Boolean =
    definedFields.contains(key.asInstanceOf[A])

  override def containsValue(value: Any): Boolean =
    entrySet().stream().anyMatch(_.getValue == value)

  override def keySet(): ju.Set[A] =
    definedFields

  override def values(): ju.Collection[B] =
    entrySet().stream().map[B](_.getValue).collect(toList())

  override def entrySet(): ju.Set[ju.Map.Entry[A, B]] = withCauseExtraction {
    cache
      .getAll(definedFields)
      .entrySet()
      .stream
      .map[ju.Map.Entry[A, B]] { entry =>
        new ju.AbstractMap.SimpleEntry(entry.getKey, entry.getValue.orNull)
      }
      .collect(Collectors.toSet[ju.Map.Entry[A, B]])
  }

  override def get(key: Any): B = withCauseExtraction {
    Option
      .when(definedFields.contains(key))(key.asInstanceOf[A])
      .flatMap(cache.get)
      .orNull
  }

  override def toString: String = cache.getAll(definedFields).asScala.toString()

}

object LazyMap {

  private def withCauseExtraction[A](expression: => A) = Try(expression).adaptErr { case ex: CompletionException =>
    ex.getCause
  }.get

  private def throwImmutableException = throw new UnsupportedOperationException("LazyImmutableMap is immutable")

}
