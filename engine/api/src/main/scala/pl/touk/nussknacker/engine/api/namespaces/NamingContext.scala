package pl.touk.nussknacker.engine.api.namespaces

// This class can be extended in the future to provide more
// information about context (e.g. process name, category)
// TODO: add context without changing this file
class NamingContext(val usageKey: ObjectNamingUsageKey.UsageKey)

object ObjectNamingUsageKey extends Enumeration {
  type UsageKey = Value

  val kafkaTopic: ObjectNamingUsageKey.Value = Value("kafka-topic")
}