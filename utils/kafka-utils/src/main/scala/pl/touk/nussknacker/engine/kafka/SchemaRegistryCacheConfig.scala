package pl.touk.nussknacker.engine.kafka

import pl.touk.nussknacker.engine.util.cache.CacheConfig

import scala.concurrent.duration.{DurationInt, FiniteDuration}

case class SchemaRegistryCacheConfig(availableSchemasExpirationTime: Option[FiniteDuration] = Some(10.seconds),
                                     parsedSchemaAccessExpirationTime: Option[FiniteDuration] = Some(120.minutes),
                                     maximumSize: Long = CacheConfig.defaultMaximumSize)