package pl.touk.nussknacker.engine.kafka

case class KafkaConfig(kafkaAddress: String,
                       kafkaProperties: Option[Map[String, String]],
                       kafkaEspProperties: Option[Map[String, String]])
