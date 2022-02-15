package pl.touk.nussknacker.engine.api.namespaces

// This class can be extended in the future to provide more
// information about context (e.g. process name, category)
class NamingContext(val usageKey: UsageKey)

sealed trait UsageKey
case object KafkaUsageKey extends UsageKey

/**
 * By using this usage key in [[pl.touk.nussknacker.engine.api.namespaces.ObjectNaming ObjectNaming]]
 * it is possible to alter the Flink process names. Please do note that they are coupled with the metrics configuration
 * and, in the current implementation, some of the metrics-related functionality will break - namely links
 * to metrics and counters. This issue will be addressed in the future.
 */
case object FlinkUsageKey extends UsageKey
case class CustomUsageKey(name: String) extends UsageKey