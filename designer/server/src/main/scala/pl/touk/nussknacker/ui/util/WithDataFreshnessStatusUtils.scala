package pl.touk.nussknacker.ui.util

import pl.touk.nussknacker.engine.api.deployment.WithDataFreshnessStatus

object WithDataFreshnessStatusUtils {

  implicit class WithDataFreshnessStatusOps[K, V](scenarioActivity: WithDataFreshnessStatus[Map[K, V]]) {

    def get(k: K): Option[WithDataFreshnessStatus[V]] = scenarioActivity.map(_.get(k)) match {
      case WithDataFreshnessStatus(Some(value), cached) => Some(WithDataFreshnessStatus(value, cached))
      case WithDataFreshnessStatus(None, _)             => None
    }

  }

}
