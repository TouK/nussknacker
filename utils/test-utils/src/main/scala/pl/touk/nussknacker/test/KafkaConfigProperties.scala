package pl.touk.nussknacker.test

object KafkaConfigProperties {

  private val defaultPrefix = "kafka"

  def bootstrapServersProperty(prefix: String = defaultPrefix): String = property(prefix, "bootstrap.servers")

  def property(prefix: String, key: String): String = s"""$prefix.kafkaProperties.${escapeeKeyIfNeeded(key)}"""
  def property(key: String): String = property(defaultPrefix, key)

  private def escapeeKeyIfNeeded(key: String) = if (key.contains(".")) s""""$key"""" else key
}
