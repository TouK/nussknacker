package pl.touk.nussknacker.test

object KafkaConfigProperties {

  private val defaultPrefix = "kafka"

  val bootstrapServersProperty: String = property(None, "bootstrap.servers")
  def bootstrapServersPropertyNestedAtPath(prefix: String = defaultPrefix): String =
    property(Some(prefix), "bootstrap.servers")

  def property(key: String): String = property(Some(defaultPrefix), key)
  // FIXME abr: remove prefix
  def property(prefix: Option[String], key: String): String =
    (prefix.toList ::: "kafkaProperties" :: escapeeKeyIfNeeded(key) :: Nil).mkString(".")

  private def escapeeKeyIfNeeded(key: String) = if (key.contains(".")) s""""$key"""" else key
}
