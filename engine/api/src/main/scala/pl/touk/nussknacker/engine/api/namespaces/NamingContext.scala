package pl.touk.nussknacker.engine.api.namespaces

// This class can be extended in the future to provide more
// information about context (e.g. process name, category)
class NamingContext(val usageKey: UsageKey)

sealed trait UsageKey
case object KafkaUsageKey extends UsageKey
case object FlinkUsageKey extends UsageKey
case class CustomUsageKey(name: String) extends UsageKey