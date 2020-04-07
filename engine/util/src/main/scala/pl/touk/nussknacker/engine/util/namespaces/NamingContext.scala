package pl.touk.nussknacker.engine.util.namespaces

// This class can be extended in the future to provide more
// information about context (e.g. process name, category)
class NamingContext(usageKey: ObjectNamingUsageKey.UsageKey)

object ObjectNamingUsageKey extends Enumeration {
  type UsageKey = Value

  val kafkaTopic: ObjectNamingUsageKey.Value = Value("kafka-topic")
}