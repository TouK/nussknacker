package pl.touk.nussknacker.engine.api.deployment

sealed trait DataFreshnessPolicy

object DataFreshnessPolicy {
  case object Fresh extends DataFreshnessPolicy
  case object CanBeCached extends DataFreshnessPolicy
}

case class WithDataFreshnessStatus[T](value: T, cached: Boolean) {
  def map[R](f: T => R): WithDataFreshnessStatus[R] = WithDataFreshnessStatus(f(value), cached)
}