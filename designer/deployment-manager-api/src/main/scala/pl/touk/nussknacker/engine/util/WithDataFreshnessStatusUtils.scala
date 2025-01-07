package pl.touk.nussknacker.engine.util

import pl.touk.nussknacker.engine.api.deployment.WithDataFreshnessStatus

object WithDataFreshnessStatusUtils {

  implicit class WithDataFreshnessStatusMapOps[K, V](withDataFreshnessStatus: WithDataFreshnessStatus[Map[K, V]]) {

    def get(k: K): Option[WithDataFreshnessStatus[V]] = withDataFreshnessStatus.map(_.get(k)) match {
      case WithDataFreshnessStatus(Some(value), cached) => Some(WithDataFreshnessStatus(value, cached))
      case WithDataFreshnessStatus(None, _)             => None
    }

    def getOrElse(k: K, orElse: V): WithDataFreshnessStatus[V] = {
      withDataFreshnessStatus.map(_.get(k)) match {
        case WithDataFreshnessStatus(Some(value), cached) => WithDataFreshnessStatus(value, cached)
        case WithDataFreshnessStatus(None, cached)        => WithDataFreshnessStatus(orElse, cached)
      }
    }

  }

  implicit class WithDataFreshnessStatusOps[A, B](scenarioActivity: WithDataFreshnessStatus[A]) {

    def withValue(v: B): WithDataFreshnessStatus[B] = {
      scenarioActivity.map(_ => v)
    }

  }

}
