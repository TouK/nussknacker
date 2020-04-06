package pl.touk.nussknacker.engine.util.namespaces

import pl.touk.nussknacker.engine.util.namespaces.ObjectNamingUsageKey.UsageKey

trait ObjectNaming {
  def prepareName(originalName: String, usageKey: ObjectNamingUsageKey.UsageKey): String
}

object ObjectNamingUsageKey extends Enumeration {
  type UsageKey = Value

  val kafkaTopic: ObjectNamingUsageKey.Value = Value("kafka-topic")
  val flinkProcess: ObjectNamingUsageKey.Value = Value("flink-process")
}

case class DefaultObjectNaming() extends ObjectNaming {
  override def prepareName(originalName: String, usageKey: UsageKey): String = originalName
}