package pl.touk.nussknacker.engine.util.cache

trait Cache[T] {
  def getOrCreate(key: String)(value: => T): T
}
