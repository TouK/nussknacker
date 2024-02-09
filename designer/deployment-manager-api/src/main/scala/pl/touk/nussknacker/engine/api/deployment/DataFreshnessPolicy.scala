package pl.touk.nussknacker.engine.api.deployment

sealed trait DataFreshnessPolicy

object DataFreshnessPolicy {
  case object Fresh       extends DataFreshnessPolicy
  case object CanBeCached extends DataFreshnessPolicy
}

final case class WithDataFreshnessStatus[T](value: T, cached: Boolean) {
  def map[R](f: T => R): WithDataFreshnessStatus[R] = WithDataFreshnessStatus(f(value), cached)
}

object WithDataFreshnessStatus {

  def fresh[T](value: T): WithDataFreshnessStatus[T] = WithDataFreshnessStatus(value, cached = false)

  def cached[T](value: T): WithDataFreshnessStatus[T] = WithDataFreshnessStatus(value, cached = true)

}
