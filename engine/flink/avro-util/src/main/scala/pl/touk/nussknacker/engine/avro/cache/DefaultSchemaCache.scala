package pl.touk.nussknacker.engine.avro.cache

import com.github.benmanes.caffeine.cache.{Cache, Caffeine}
import org.apache.avro.Schema
import scalacache.Entry

import scala.concurrent.duration.Duration

class DefaultSchemaCache extends SchemaCache {

  import scalacache.caffeine._
  import scalacache.modes.sync._

  private val caffeineClientCache: Cache[String, Entry[Schema]] =
    Caffeine
      .newBuilder()
      .maximumSize(10000L)
      .build[String, Entry[Schema]]

  implicit private val cache: CaffeineCache[Schema] =
    CaffeineCache(caffeineClientCache)

  override def getOrCreate(key: String, ttl: Option[Duration], opSchema: => Schema): Schema = {
    val schema = cache.caching(key)(ttl) {
      opSchema
    }
    schema
  }

  override def close(): Unit = {
    caffeineClientCache.cleanUp()
    cache.removeAll()
  }
}
