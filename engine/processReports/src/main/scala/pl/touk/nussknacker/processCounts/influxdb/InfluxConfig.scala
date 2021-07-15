package pl.touk.nussknacker.processCounts.influxdb

case class InfluxConfig(influxUrl: String, user: Option[String], password: Option[String],
                        database: String,
                        queryMode: QueryMode.Value = QueryMode.OnlySingleDifference,
                        metricsConfig: Option[MetricsConfig])

case class MetricsConfig(sourceCountMetric: String = "source_count",
                         nodeCountMetric: String = "nodeCount",
                         nodeIdTag: String = "nodeId",
                         slotTag: String = "slot",
                         processTag: String = "process",
                         countField: String = "count",
                         envTag: String = "env")

object QueryMode extends Enumeration {
  type QueryMode = Value
  val OnlySingleDifference, OnlySumOfDifferences, SumOfDifferencesForRestarts = Value
}