package pl.touk.nussknacker.processCounts.influxdb

import pl.touk.nussknacker.engine.util.metrics.common.naming.{nodeIdTag, scenarioIdTag}

case class InfluxConfig(influxUrl: String, user: Option[String], password: Option[String],
                        database: String,
                        queryMode: QueryMode.Value = QueryMode.OnlySumOfDifferences,
                        metricsConfig: Option[MetricsConfig])

case class MetricsConfig(sourceCountMetric: String = "source_count",
                         nodeCountMetric: String = "nodeCount",
                         nodeIdTag: String = nodeIdTag,
                         slotTag: String = "slot",
                         scenarioTag: String = scenarioIdTag,
                         countField: String = "count",
                         envTag: String = "env")

object QueryMode extends Enumeration {
  type QueryMode = Value
  val OnlySingleDifference, OnlySumOfDifferences, SumOfDifferencesForRestarts = Value
}