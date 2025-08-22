package pl.touk.nussknacker.test

object KafkaConfigProperties {

  def bootstrapServersProperty(prefix: String): String =
    property(prefix, "bootstrap.servers")

  def property(prefix: String, key: String): String =
    (prefix :: "kafkaProperties" :: escapeeKeyIfNeeded(key) :: Nil).mkString(".")

  private def escapeeKeyIfNeeded(key: String) = if (key.contains(".")) s""""$key"""" else key

}
