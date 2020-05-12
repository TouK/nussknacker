package pl.touk.nussknacker.engine.util.cache

import scala.concurrent.duration.Duration

trait Cache[T] {
  def getOrCreate(key: String, ttl: Option[Duration])(value: => T): T
}
