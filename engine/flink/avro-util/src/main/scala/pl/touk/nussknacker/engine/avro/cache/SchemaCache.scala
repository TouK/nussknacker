package pl.touk.nussknacker.engine.avro.cache

import java.io.Closeable

import org.apache.avro.Schema

import scala.concurrent.duration.Duration

trait SchemaCache extends Closeable {
  def getOrCreate(key: String, ttl: Option[Duration], opSchema: => Schema): Schema
}
